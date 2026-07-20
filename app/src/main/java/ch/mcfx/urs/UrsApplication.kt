package ch.mcfx.urs

import android.app.Application
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.Room
import ch.mcfx.urs.auth.AuthAuthenticator
import ch.mcfx.urs.auth.AuthInterceptor
import ch.mcfx.urs.auth.AuthRepository
import ch.mcfx.urs.auth.AuthTokenStore
import ch.mcfx.urs.auth.BiometricGate
import ch.mcfx.urs.data.BeerRepository
import ch.mcfx.urs.data.CatalogRepository
import ch.mcfx.urs.data.FuelRepository
import ch.mcfx.urs.data.InventoryRepository
import ch.mcfx.urs.data.ShoppingListRepository
import ch.mcfx.urs.data.UserRepository
import ch.mcfx.urs.data.WorkTimeRepository
import ch.mcfx.urs.data.local.AppDatabase
import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.data.sync.ReachabilityChecker
import ch.mcfx.urs.data.sync.SyncManager
import ch.mcfx.urs.data.sync.SyncWorker
import ch.mcfx.urs.location.LocationCapture
import ch.mcfx.urs.location.LocationCaptureScheduler
import ch.mcfx.urs.location.LocationHistorySettingsStore
import ch.mcfx.urs.notifications.NotificationChannels
import ch.mcfx.urs.notifications.NotificationSender
import ch.mcfx.urs.notifications.ReminderScheduler
import ch.mcfx.urs.notifications.ReminderStore
import ch.mcfx.urs.vpn.NetworkGate
import ch.mcfx.urs.vpn.VpnConfigRepository
import ch.mcfx.urs.vpn.WifiSsidReader
import ch.mcfx.urs.vpn.WireGuardManager
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.osmdroid.config.Configuration as OsmConfiguration
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class UrsApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Required by osmdroid's tile usage policy — an unset/default user
        // agent gets tile requests blocked by some providers.
        OsmConfiguration.getInstance().userAgentValue = packageName
        // Process-lifetime Wi-Fi listener so a live network change (e.g.
        // arriving home while the app is already open) is reacted to
        // immediately, not just at the next cold start.
        container.networkGate.startObserving(container.applicationScope)
        // Channels must exist before any notification can be posted to them.
        NotificationChannels.registerAll(this)
        // App-open case for missed scheduled reminders (the other case,
        // device boot, is handled by BootCompletedReceiver). Suspend now
        // (a conditional reminder needs a network round-trip), so this
        // launches on the app's own process-lifetime scope rather than
        // blocking onCreate().
        container.applicationScope.launch {
            container.reminderScheduler.rearmAndCheckMissed()
        }
        // First-launch-before-any-successful-sync fallback so the fuel
        // currency picker works fully offline — replaced by the server's
        // own list as soon as a refresh succeeds (see FuelViewModel.load).
        container.applicationScope.launch {
            container.fuelRepository.seedCurrenciesIfEmpty()
        }
        // Durability backstop for the offline fill outbox (15-minute floor);
        // a much faster connectivity-triggered path also exists via
        // NetworkGate's own callback, see AppContainer.networkGate below.
        SyncWorker.enqueuePeriodic(this)
        // Re-arms the life map's periodic capture across process restarts —
        // WorkManager itself persists periodic work across reboot, but this
        // covers the case where it was never enqueued in this process at
        // all (e.g. right after an app update).
        if (container.locationHistorySettingsStore.isEnabled()) {
            LocationCaptureScheduler.reschedule(this, container.locationHistorySettingsStore.intervalMinutes())
        }
        // Re-checks BiometricGate's 24h window on every app-level foreground,
        // not just a true cold start (ProcessLifecycleOwner fires once for
        // the whole process, unlike an individual Activity's onStart) — the
        // user's own requirement (2026-07-17): the 24h clock is wall-clock
        // time since the last successful unlock, not "once per process".
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) container.biometricGate.reevaluate()
            },
        )
    }
}

// Manual dependency injection: one place that builds and owns the object
// graph. A DI framework (Hilt) can replace this later if it grows.
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val json = Json { ignoreUnknownKeys = true }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // No migration strategy exists yet for this pre-release database (first
    // Room use in this app) — destructive fallback is acceptable now since
    // no shipped build has real user data at stake; revisit before a schema
    // change ever needs to preserve an existing outbox in the wild.
    val database: AppDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, "urs.db")
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    val vpnConfigRepository = VpnConfigRepository(context)
    val wireGuardManager = WireGuardManager(context, vpnConfigRepository)
    val networkGate = NetworkGate(
        context, WifiSsidReader(context), vpnConfigRepository, wireGuardManager,
        onConnectivityAvailable = { SyncWorker.enqueueOneTime(appContext) },
    )

    val authTokenStore = AuthTokenStore(context)
    val biometricGate = BiometricGate(context)

    private fun baseHttpClientBuilder() = OkHttpClient.Builder()
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                        // The access token is otherwise printed in full on every
                        // logged request — this app has no auth at all before
                        // , so this redaction didn't exist/matter until now.
                        redactHeader("Authorization")
                    }
                )
            }
        }

    // Deliberately carries neither AuthInterceptor nor the authenticator
    // below — AuthAuthenticator uses this to make its own refresh call, and
    // a failing refresh call must not recurse back into authentication.
    private val refreshClient = baseHttpClientBuilder().build()

    private val httpClient = baseHttpClientBuilder()
        .addInterceptor(AuthInterceptor(authTokenStore))
        .authenticator(AuthAuthenticator(authTokenStore, refreshClient, BuildConfig.BASE_URL))
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(httpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val ursApi = retrofit.create(UrsApi::class.java)

    val authRepository = AuthRepository(ursApi, authTokenStore)

    val reachabilityChecker = ReachabilityChecker()
    val syncManager = SyncManager(
        api = ursApi,
        fillDao = database.fillDao(),
        fillingStationDao = database.fillingStationDao(),
        workTimeDao = database.workTimeDao(),
        inventoryDao = database.inventoryDao(),
        inventoryProductDao = database.inventoryProductDao(),
        listDao = database.listDao(),
        listItemDao = database.listItemDao(),
        outboxDao = database.outboxDao(),
        reachabilityChecker = reachabilityChecker,
        json = json,
    )
    val locationCapture = LocationCapture(context)
    val locationHistorySettingsStore = LocationHistorySettingsStore(context)

    // Reuses the same authenticated httpClient Retrofit uses (AuthInterceptor
    // + AuthAuthenticator already attached) — /api/v1/catalog-image/{id} sits
    // on the protected route subrouter server-side, so an unauthenticated
    // Coil request would otherwise get a 401.
    val imageLoader: ImageLoader = ImageLoader.Builder(appContext)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { httpClient })) }
        .build()

    val fuelRepository = FuelRepository(
        api = ursApi,
        fillDao = database.fillDao(),
        fillingStationDao = database.fillingStationDao(),
        currencyDao = database.currencyDao(),
        carDao = database.carDao(),
        outboxDao = database.outboxDao(),
        syncManager = syncManager,
        applicationScope = applicationScope,
        json = json,
    )
    val inventoryRepository = InventoryRepository(
        api = ursApi,
        inventoryDao = database.inventoryDao(),
        inventoryProductDao = database.inventoryProductDao(),
        outboxDao = database.outboxDao(),
        syncManager = syncManager,
        applicationScope = applicationScope,
        json = json,
    )
    val catalogRepository = CatalogRepository(
        api = ursApi,
        catalogProductDao = database.catalogProductDao(),
        catalogCategoryDao = database.catalogCategoryDao(),
    )
    val shoppingListRepository = ShoppingListRepository(
        api = ursApi,
        listDao = database.listDao(),
        listItemDao = database.listItemDao(),
        catalogProductDao = database.catalogProductDao(),
        catalogCategoryDao = database.catalogCategoryDao(),
        recentlyUsedProductDao = database.recentlyUsedProductDao(),
        outboxDao = database.outboxDao(),
        syncManager = syncManager,
        applicationScope = applicationScope,
        json = json,
    )
    val beerRepository = BeerRepository(retrofit.create(UrsApi::class.java))
    val workTimeRepository = WorkTimeRepository(
        api = ursApi,
        workTimeDao = database.workTimeDao(),
        workTimeMonthOverrideDao = database.workTimeMonthOverrideDao(),
        outboxDao = database.outboxDao(),
        syncManager = syncManager,
        applicationScope = applicationScope,
        json = json,
        tokenStore = authTokenStore,
    )
    val userRepository = UserRepository(retrofit.create(UrsApi::class.java), authTokenStore)

    val reminderStore = ReminderStore(context)
    val notificationSender = NotificationSender(context)
    val reminderScheduler = ReminderScheduler(
        context, reminderStore, notificationSender,
        quantityLookup = { inventoryId, productId -> inventoryRepository.getProductQuantity(inventoryId, productId) },
    )
}
