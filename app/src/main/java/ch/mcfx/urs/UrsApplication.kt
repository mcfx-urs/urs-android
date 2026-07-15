package ch.mcfx.urs

import android.app.Application
import android.content.Context
import androidx.room.Room
import ch.mcfx.urs.data.BeerRepository
import ch.mcfx.urs.data.CatalogRepository
import ch.mcfx.urs.data.FuelRepository
import ch.mcfx.urs.data.InventoryRepository
import ch.mcfx.urs.data.UserRepository
import ch.mcfx.urs.data.WorkTimeRepository
import ch.mcfx.urs.data.local.AppDatabase
import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.data.sync.ReachabilityChecker
import ch.mcfx.urs.data.sync.SyncManager
import ch.mcfx.urs.data.sync.SyncWorker
import ch.mcfx.urs.location.LocationCapture
import ch.mcfx.urs.notifications.NotificationChannels
import ch.mcfx.urs.notifications.NotificationSender
import ch.mcfx.urs.notifications.ReminderScheduler
import ch.mcfx.urs.notifications.ReminderStore
import ch.mcfx.urs.vpn.NetworkGate
import ch.mcfx.urs.vpn.VpnConfigRepository
import ch.mcfx.urs.vpn.WifiSsidReader
import ch.mcfx.urs.vpn.WireGuardManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class UrsApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
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

    private val httpClient = OkHttpClient.Builder()
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
                )
            }
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(httpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val ursApi = retrofit.create(UrsApi::class.java)

    val reachabilityChecker = ReachabilityChecker()
    val syncManager = SyncManager(
        api = ursApi,
        fillDao = database.fillDao(),
        fillingStationDao = database.fillingStationDao(),
        workTimeDao = database.workTimeDao(),
        inventoryCategoryDao = database.inventoryCategoryDao(),
        inventoryProductDao = database.inventoryProductDao(),
        outboxDao = database.outboxDao(),
        reachabilityChecker = reachabilityChecker,
        json = json,
    )
    val locationCapture = LocationCapture(context)

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
        inventoryCategoryDao = database.inventoryCategoryDao(),
        inventoryProductDao = database.inventoryProductDao(),
        outboxDao = database.outboxDao(),
        syncManager = syncManager,
        applicationScope = applicationScope,
        json = json,
    )
    val catalogRepository = CatalogRepository(
        api = ursApi,
        catalogProductDao = database.catalogProductDao(),
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
    )
    val userRepository = UserRepository(retrofit.create(UrsApi::class.java))

    val reminderStore = ReminderStore(context)
    val notificationSender = NotificationSender(context)
    val reminderScheduler = ReminderScheduler(
        context, reminderStore, notificationSender,
        quantityLookup = { categoryId, productId -> inventoryRepository.getProductQuantity(categoryId, productId) },
    )
}
