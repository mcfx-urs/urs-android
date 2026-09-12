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
import ch.mcfx.urs.chores.ChoreOrderStore
import ch.mcfx.urs.chores.ChoreOverdueWorker
import ch.mcfx.urs.chores.ChoreReminderSettingsStore
import ch.mcfx.urs.data.AudioNoteRepository
import ch.mcfx.urs.data.BakingRepository
import ch.mcfx.urs.data.BeerRepository
import ch.mcfx.urs.data.CatalogRepository
import ch.mcfx.urs.data.ChoreRepository
import ch.mcfx.urs.data.FuelRepository
import ch.mcfx.urs.data.DefaultVehicleStore
import ch.mcfx.urs.data.ImageGenRepository
import ch.mcfx.urs.data.InventoryRepository
import ch.mcfx.urs.data.KanbanRepository
import ch.mcfx.urs.data.LocationHistoryRepository
import ch.mcfx.urs.data.NoteRepository
import ch.mcfx.urs.data.ServiceRepository
import ch.mcfx.urs.data.ShoppingListRepository
import ch.mcfx.urs.data.VehicleRepository
import ch.mcfx.urs.data.UserRepository
import ch.mcfx.urs.data.WorkSettingsStore
import ch.mcfx.urs.data.WorkTimeRepository
import ch.mcfx.urs.data.local.AppDatabase
import ch.mcfx.urs.home.HomeLayoutStore
import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.data.sync.PullCoordinator
import ch.mcfx.urs.data.sync.ReachabilityChecker
import ch.mcfx.urs.data.sync.SyncManager
import ch.mcfx.urs.data.sync.SyncStatusStore
import ch.mcfx.urs.data.sync.SyncWorker
import ch.mcfx.urs.location.LocationActivityRecognitionManager
import ch.mcfx.urs.location.LocationCapture
import ch.mcfx.urs.location.LocationCaptureDebugLog
import ch.mcfx.urs.location.LocationCaptureModeManager
import ch.mcfx.urs.location.LocationGeofenceManager
import ch.mcfx.urs.location.LocationHistorySettingsStore
import ch.mcfx.urs.location.LocationProvider
import ch.mcfx.urs.location.hasActivityRecognitionPermission
import ch.mcfx.urs.location.hasLocationPermission
import ch.mcfx.urs.notifications.NotificationChannels
import ch.mcfx.urs.notifications.NotificationSender
import ch.mcfx.urs.notifications.ReminderScheduler
import ch.mcfx.urs.notifications.ReminderStore
import ch.mcfx.urs.obd.ObdManager
import ch.mcfx.urs.settings.ThemeSettingsStore
import ch.mcfx.urs.vpn.NetworkGate
import ch.mcfx.urs.vpn.VpnConfigRepository
import ch.mcfx.urs.vpn.WifiSsidReader
import ch.mcfx.urs.vpn.WireGuardManager
import ch.mcfx.urs.watchrelay.WatchRelayService
import ch.mcfx.urs.watchrelay.WatchRelaySettingsStore
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
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
        // Twice-a-day overdue-chore check; the worker itself no-ops unless
        // the Settings toggle is on (GitHub issue #29).
        ChoreOverdueWorker.enqueuePeriodic(this)
        // Re-arms the life map's periodic capture across process restarts —
        // WorkManager itself persists periodic work across reboot, but this
        // covers the case where it was never enqueued in this process at
        // all (e.g. right after an app update).
        // Re-arms the geofence/activity-recognition registrations too
        // (GitHub issue #60) — Play Services usually persists these across
        // process death, but not necessarily across a reboot, and this
        // mirrors the existing scheduler-only re-arm above regardless.
        if (container.locationHistorySettingsStore.isEnabled()) {
            val store = container.locationHistorySettingsStore
            LocationCaptureModeManager.applyEffectiveCapture(this, "process-start")
            if (store.isGeofenceAdaptiveEnabled() && hasLocationPermission(this)) {
                store.geofenceCenter()?.let { (lat, lon) ->
                    try {
                        LocationGeofenceManager.arm(this, lat, lon, store.geofenceRadiusMeters().toFloat())
                    } catch (e: SecurityException) {
                        // Permission revoked since the toggle was turned on — leave it
                        // to the settings screen's own permission-row UI to surface.
                    }
                }
            }
            if (store.isActivityPauseEnabled() && hasActivityRecognitionPermission(this)) {
                try {
                    LocationActivityRecognitionManager.start(this)
                } catch (e: SecurityException) {
                    // Same rationale as above.
                }
            }
        }
        // Same rationale — a killed-and-relaunched process (not a full
        // reboot, which BootCompletedReceiver covers) otherwise leaves the
        // watch relay silently stopped despite the Settings toggle still
        // showing enabled.
        if (container.watchRelaySettingsStore.isEnabled()) {
            WatchRelayService.start(this)
        }
        // Re-checks BiometricGate's 24h window on every app-level foreground,
        // not just a true cold start (ProcessLifecycleOwner fires once for
        // the whole process, unlike an individual Activity's onStart) — the
        // user's own requirement (2026-07-17): the 24h clock is wall-clock
        // time since the last successful unlock, not "once per process".
        // Same foreground signal also refreshes the ambient LocationProvider
        // so a screen that needs it (the fuel-add station picker) already
        // has a recent fix instead of fetching one itself; a no-op without
        // location permission granted yet.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    container.biometricGate.reevaluate()
                    container.applicationScope.launch { container.locationProvider.refresh() }
                }
            },
        )
    }
}

// Manual dependency injection: one place that builds and owns the object
// graph. A DI framework (Hilt) can replace this later if it grows.
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    // Not private — LocationCaptureWorker also needs this to encode its own
    // outbox payload directly (it has no repository of its own to hide that
    // behind, unlike every other outbox producer in this app).
    // coerceInputValues: a Go `nil` slice/map with no `omitempty` tag
    // serializes as JSON `null`, not `[]`/`{}` — confirmed 2026-08-22 for
    // note.Tags (an omitted request field left the backend's tagNames nil,
    // echoed straight into the create/update response). Decoding `null`
    // into a non-nullable Kotlin collection field otherwise throws even
    // though the field has an empty-collection default, permanently
    // stranding that outbox mutation (see SyncManager's replay catch block)
    // and duplicating the row itself, since the backend already completed
    // the write before the client-side response parse failed. This makes
    // every such field fall back to its declared default instead.
    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // No migration strategy exists yet for this pre-release database (first
    // Room use in this app) — destructive fallback is acceptable now since
    // no shipped build has real user data at stake; revisit before a schema
    // change ever needs to preserve an existing outbox in the wild.
    val database: AppDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, "urs.db")
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    val authTokenStore = AuthTokenStore(context)

    val vpnConfigRepository = VpnConfigRepository(context, authTokenStore)
    val wireGuardManager = WireGuardManager(context, vpnConfigRepository)

    // Late-bound in this class's init {} below, once pullCoordinator exists
    // (it needs every repository, which are all constructed after
    // networkGate) — same forward-reference shape as syncManager's
    // onListConflictResolved further down.
    private var onTunnelReachable: (() -> Unit)? = null

    val networkGate = NetworkGate(
        context, WifiSsidReader(context), vpnConfigRepository, wireGuardManager,
        onConnectivityAvailable = {
            SyncWorker.enqueueOneTime(appContext)
            onTunnelReachable?.invoke()
        },
    )

    val biometricGate = BiometricGate(context)

    private fun baseHttpClientBuilder() = OkHttpClient.Builder()
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                        // The access token is otherwise printed in full on every
                        // logged request.
                        redactHeader("Authorization")
                    }
                )
            }
        }

    // Deliberately carries neither AuthInterceptor nor the authenticator
    // below — AuthAuthenticator uses this to make its own refresh call, and
    // a failing refresh call must not recurse back into authentication.
    // retryOnConnectionFailure(false): OkHttp otherwise silently re-sends a
    // request on a fresh connection when a pooled one turns out to be dead
    // (e.g. right after a backend redeploy) — safe for ordinary calls, but
    // this one carries a single-use refresh token. If the first, invisible
    // attempt actually reached the server and rotated the token before the
    // client saw the dead connection, the automatic retry then presents an
    // already-consumed token, which the backend can't tell apart from theft
    // and revokes every session. A clean failure here already correctly
    // forces a re-login, so there's nothing to gain from the silent retry.
    private val refreshClient = baseHttpClientBuilder()
        .retryOnConnectionFailure(false)
        .build()

    // AI image generation () is synchronous and can legitimately run
    // well past every other endpoint's default 10s read timeout. Without
    // this, OkHttp gives up and closes the connection first, which
    // urs-backend then sees as its request context being canceled (not a
    // clean timeout) partway through a perfectly healthy generation.
    // chain.withReadTimeout scopes the longer timeout to just this one
    // endpoint rather than loosening it for every call. 100s sits above
    // both the ingress's proxy-read-timeout (90s) and urs-backend's own
    // imagegen.requestTimeout (75s, imagegen.go) — see that file's own
    // comment for why the three need this exact ordering.
    private val longRunningEndpointTimeout = Interceptor { chain ->
        val request = chain.request()
        val path = request.url.encodedPath
        if (path.endsWith("/catalog-image/generate") || path.endsWith("/image/generate")) {
            chain.withReadTimeout(100, TimeUnit.SECONDS).proceed(request)
        } else {
            chain.proceed(request)
        }
    }

    private val httpClient = baseHttpClientBuilder()
        .addInterceptor(AuthInterceptor(authTokenStore))
        .addInterceptor(longRunningEndpointTimeout)
        .authenticator(AuthAuthenticator(authTokenStore, refreshClient, BuildConfig.BASE_URL))
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(httpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val ursApi = retrofit.create(UrsApi::class.java)

    val workSettingsStore = WorkSettingsStore(context)
    val defaultVehicleStore = DefaultVehicleStore(context)
    val choreOrderStore = ChoreOrderStore(context)

    val authRepository = AuthRepository(
        ursApi, authTokenStore, database, wireGuardManager, workSettingsStore, defaultVehicleStore, choreOrderStore,
    )

    val reachabilityChecker = ReachabilityChecker()
    val syncStatusStore = SyncStatusStore(context)
    val syncManager = SyncManager(
        api = ursApi,
        fillDao = database.fillDao(),
        fillingStationDao = database.fillingStationDao(),
        workTimeDao = database.workTimeDao(),
        inventoryDao = database.inventoryDao(),
        inventoryProductDao = database.inventoryProductDao(),
        listDao = database.listDao(),
        listItemDao = database.listItemDao(),
        locationHistoryDao = database.locationHistoryDao(),
        vehicleServiceDao = database.vehicleServiceDao(),
        bakePlanDao = database.bakePlanDao(),
        bakePlanStepDao = database.bakePlanStepDao(),
        noteDao = database.noteDao(),
        trackerTypeDao = database.trackerTypeDao(),
        trackerEventDao = database.trackerEventDao(),
        kanbanBoardDao = database.kanbanBoardDao(),
        kanbanColumnDao = database.kanbanColumnDao(),
        kanbanCardDao = database.kanbanCardDao(),
        kanbanChecklistItemDao = database.kanbanChecklistItemDao(),
        outboxDao = database.outboxDao(),
        reachabilityChecker = reachabilityChecker,
        syncStatusStore = syncStatusStore,
        json = json,
    )
    val locationCaptureDebugLog = LocationCaptureDebugLog(database.locationCaptureLogDao(), applicationScope)
    val locationCapture = LocationCapture(context, locationCaptureDebugLog)
    val locationProvider = LocationProvider(context, locationCapture)
    val locationHistorySettingsStore = LocationHistorySettingsStore(context)
    val watchRelaySettingsStore = WatchRelaySettingsStore(context)
    val themeSettingsStore = ThemeSettingsStore(context)

    // Launcher-style Home layout persistence (GitHub issue #12). The
    // edit-mode UI on top of this is not built yet — HomeScreen still
    // renders its fixed layout for now.
    val homeLayoutStore = HomeLayoutStore(context, json)

    // Not started here - connect()/disconnect() are driven by whatever
    // future UI surfaces this (a live-data screen). Constructed eagerly
    // like the app's other managers so that surface has a ready instance.
    val obdManager = ObdManager(context)

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
        vehicleDao = database.vehicleDao(),
        outboxDao = database.outboxDao(),
        syncManager = syncManager,
        applicationScope = applicationScope,
        json = json,
    )
    val vehicleRepository = VehicleRepository(
        api = ursApi,
        vehicleDao = database.vehicleDao(),
    )
    val serviceRepository = ServiceRepository(
        api = ursApi,
        vehicleServiceDao = database.vehicleServiceDao(),
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
        authTokenStore = authTokenStore,
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
    val beerRepository = BeerRepository(
        api = retrofit.create(UrsApi::class.java),
        outboxDao = database.outboxDao(),
        json = json,
    )
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
    val userRepository = UserRepository(retrofit.create(UrsApi::class.java), authTokenStore, defaultVehicleStore)
    val imageGenRepository = ImageGenRepository(retrofit.create(UrsApi::class.java))
    val locationHistoryRepository = LocationHistoryRepository(
        api = ursApi,
        locationHistoryDao = database.locationHistoryDao(),
    )
    val bakingRepository = BakingRepository(
        context = appContext,
        bakePlanDao = database.bakePlanDao(),
        bakePlanStepDao = database.bakePlanStepDao(),
        outboxDao = database.outboxDao(),
        syncManager = syncManager,
        applicationScope = applicationScope,
        json = json,
    )
    val noteRepository = NoteRepository(
        context = appContext,
        api = ursApi,
        noteDao = database.noteDao(),
        noteTagDao = database.noteTagDao(),
        outboxDao = database.outboxDao(),
        syncManager = syncManager,
        tokenStore = authTokenStore,
        applicationScope = applicationScope,
        json = json,
    )
    // Device-local voice notes relayed from the watch (GitHub issue #41).
    val audioNoteRepository = AudioNoteRepository(appContext, database.audioNoteDao())

    val choreRepository = ChoreRepository(
        api = ursApi,
        trackerTypeDao = database.trackerTypeDao(),
        trackerEventDao = database.trackerEventDao(),
        outboxDao = database.outboxDao(),
        syncManager = syncManager,
        tokenStore = authTokenStore,
        applicationScope = applicationScope,
        json = json,
    )
    val kanbanRepository = KanbanRepository(
        context = appContext,
        api = ursApi,
        boardDao = database.kanbanBoardDao(),
        columnDao = database.kanbanColumnDao(),
        cardDao = database.kanbanCardDao(),
        checklistDao = database.kanbanChecklistItemDao(),
        tagDao = database.kanbanCardTagDao(),
        outboxDao = database.outboxDao(),
        syncManager = syncManager,
        applicationScope = applicationScope,
        json = json,
    )

    // Everything from here down needs one repository or another — that's
    // why it's built last, not with the other sync/network setup above (see
    // PullCoordinator's own doc comment for what it does and why
    // bakingRepository, above, is deliberately not in its list).
    val pullCoordinator = PullCoordinator(
        fuelRepository = fuelRepository,
        vehicleRepository = vehicleRepository,
        serviceRepository = serviceRepository,
        inventoryRepository = inventoryRepository,
        catalogRepository = catalogRepository,
        shoppingListRepository = shoppingListRepository,
        workTimeRepository = workTimeRepository,
        locationHistoryRepository = locationHistoryRepository,
        noteRepository = noteRepository,
        choreRepository = choreRepository,
        kanbanRepository = kanbanRepository,
        syncStatusStore = syncStatusStore,
    )

    val choreReminderSettingsStore = ChoreReminderSettingsStore(context)

    val reminderStore = ReminderStore(context)
    val notificationSender = NotificationSender(context)
    val reminderScheduler = ReminderScheduler(
        context, reminderStore, notificationSender,
        quantityLookup = { inventoryId, productId -> inventoryRepository.getProductQuantity(inventoryId, productId) },
    )

    init {
        // SyncManager is constructed before ShoppingListRepository, so the
        // list-conflict refresh hook is late-bound here. Wrapped in
        // applicationScope.launch so it never re-enters syncNow() from
        // inside a replay pass — see SyncManager.onListConflictResolved.
        syncManager.onListConflictResolved = {
            applicationScope.launch { shoppingListRepository.refreshFromBackend() }
        }
        // Same rationale as onListConflictResolved above, for the Kanban
        // board/column/card equivalent.
        syncManager.onKanbanConflictResolved = {
            applicationScope.launch { kanbanRepository.refreshFromBackend() }
        }
        // See onTunnelReachable's own doc comment above for why this is
        // late-bound instead of passed to NetworkGate directly. Runs
        // alongside (not instead of) the SyncWorker.enqueueOneTime call in
        // that same callback — this is the "feels instant" pull path,
        // SyncWorker's periodic run is the 15-minute-floor backstop for
        // whatever this one didn't manage to land.
        onTunnelReachable = { applicationScope.launch { pullCoordinator.pullAll() } }
    }
}
