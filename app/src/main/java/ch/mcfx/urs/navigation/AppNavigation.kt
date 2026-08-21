package ch.mcfx.urs.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import ch.mcfx.urs.R
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.auth.BiometricUnlockScreen
import ch.mcfx.urs.auth.LoginScreen
import ch.mcfx.urs.baking.BakePlanDetailScreen
import ch.mcfx.urs.baking.BakingHistoryScreen
import ch.mcfx.urs.baking.BakingHubScreen
import ch.mcfx.urs.baking.BakingRoutes
import ch.mcfx.urs.beer.BeerScreen
import ch.mcfx.urs.fuel.FuelAddScreen
import ch.mcfx.urs.fuel.FuelHubScreen
import ch.mcfx.urs.fuel.FuelPriceAddScreen
import ch.mcfx.urs.fuel.FuelRoutes
import ch.mcfx.urs.fuel.FuelScreen
import ch.mcfx.urs.fuel.FuelStationMapScreen
import ch.mcfx.urs.fuel.FuelStationsScreen
import ch.mcfx.urs.fuel.FuelStatsScreen
import ch.mcfx.urs.fuel.StationsViewModel
import ch.mcfx.urs.home.HomeScreen
import ch.mcfx.urs.data.local.publicId
import kotlinx.coroutines.launch
import ch.mcfx.urs.inventory.InventoriesScreen
import ch.mcfx.urs.inventory.InventoryRoutes
import ch.mcfx.urs.inventory.ProductListScreen
import ch.mcfx.urs.lifemap.LifeMapScreen
import ch.mcfx.urs.obd.ObdLiveScreen
import ch.mcfx.urs.obd.ObdRoutes
import ch.mcfx.urs.obd.ObdSetupScreen
import ch.mcfx.urs.settings.AboutScreen
import ch.mcfx.urs.settings.AccountSettingsScreen
import ch.mcfx.urs.settings.AdminScreen
import ch.mcfx.urs.settings.GeneralSettingsScreen
import ch.mcfx.urs.settings.ImageReviewScreen
import ch.mcfx.urs.settings.LanguageSettingsScreen
import ch.mcfx.urs.settings.LocationHistorySettingsScreen
import ch.mcfx.urs.settings.NotificationSettingsScreen
import ch.mcfx.urs.settings.ProductManagementScreen
import ch.mcfx.urs.settings.SettingsRoutes
import ch.mcfx.urs.settings.SettingsScreen
import ch.mcfx.urs.settings.ThemeSettingsScreen
import ch.mcfx.urs.settings.VpnSettingsScreen
import ch.mcfx.urs.settings.WageRulesSettingsScreen
import ch.mcfx.urs.settings.WatchRelaySettingsScreen
import ch.mcfx.urs.service.ServiceAddScreen
import ch.mcfx.urs.service.ServiceRoutes
import ch.mcfx.urs.service.ServiceScreen
import ch.mcfx.urs.shoppinglist.ListDetailScreen
import ch.mcfx.urs.shoppinglist.ShoppingListRoutes
import ch.mcfx.urs.shoppinglist.ShoppingListsScreen
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTopBar
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.vehicle.VehicleAddScreen
import ch.mcfx.urs.vehicle.VehicleHubScreen
import ch.mcfx.urs.vehicle.VehicleRoutes
import ch.mcfx.urs.vehicle.VehicleScreen
import ch.mcfx.urs.worktime.WorkTimeAddScreen
import ch.mcfx.urs.worktime.WorkTimeRoutes
import ch.mcfx.urs.worktime.WorkTimeScreen

@Composable
fun AppNavigation(onNavControllerReady: (NavHostController) -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as UrsApplication

    // Silent, best-effort only: the app is fully usable without any VPN set
    // up at all. If a tunnel is already configured and permissions are
    // already granted, try to bring it up in the background; otherwise do
    // nothing here — no prompts, no banner. Screens that actually need the
    // backend show their own existing error/retry state if it's unreachable.
    // Placed ahead of the isLoggedIn/biometric gates below:
    // both of those `return` early, so this never ran at all while either
    // screen was showing, leaving LoginScreen to hit the backend over
    // whatever network happened to be active with no tunnel brought up.
    LaunchedEffect(Unit) {
        app.container.networkGate.ensureReachable()
    }

    // Gates the whole app behind login — swaps out the entire
    // drawer+NavHost UI below for LoginScreen whenever there's no valid
    // session, rather than trying to model "logged out" as just another
    // NavHost route. AuthTokenStore.isLoggedIn is driven off the refresh
    // token's presence, not the access token, and AuthAuthenticator clears
    // it automatically the moment a refresh attempt itself fails (expired/
    // revoked refresh token) — so this recomposes back to LoginScreen from
    // anywhere in the app the instant that happens, with no back-stack
    // entry left behind to accidentally return to.
    val isLoggedIn by app.container.authTokenStore.isLoggedIn.collectAsStateWithLifecycle()
    if (!isLoggedIn) {
        LoginScreen()
        return
    }

    // Local re-entry gate on top of the real session above (opt-in, see
    // Settings → Account) — re-evaluated on every app-level foreground by
    // UrsApplication's ProcessLifecycleOwner observer, not just here, so
    // the 24h window (the user's own requirement, 2026-07-17) is checked
    // even if this composable never leaves composition across a
    // background/foreground cycle.
    val biometricGate = app.container.biometricGate
    val isBiometricUnlocked by biometricGate.isUnlocked.collectAsStateWithLifecycle()
    if (biometricGate.isEnabled && !isBiometricUnlocked && biometricGate.needsUnlock()) {
        val coroutineScope = rememberCoroutineScope()
        BiometricUnlockScreen(
            gate = biometricGate,
            onUsePasswordInstead = { coroutineScope.launch { app.container.authRepository.logout() } },
        )
        return
    }

    val navController = rememberNavController()

    // Hands the controller back to MainActivity so a notification tap can
    // deep-link while the app is already running (onNewIntent) as well as
    // on cold start (handled once here, for the Activity's launching intent).
    LaunchedEffect(navController) { onNavControllerReady(navController) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Destination.HOME.route

    Column(modifier = Modifier.fillMaxSize().background(UrsTheme.colors.background)) {
        // Home renders its own header (logo/gear + collapsing welcome/hero
        // row) inline as part of its scrollable content instead of this
        // shared bar — see HomeScreen's HomeHeader.
        if (currentRoute != Destination.HOME.route) {
            val topBarTint = if (isAccentTopBarRoute(currentRoute)) UrsTheme.colors.accent else UrsTheme.colors.onSurface
            UrsTopBar(
                navigationIcon = {
                    // The only in-app "back" affordance anywhere below Home:
                    // always jumps straight to Home, never one level up.
                    UrsIconButton(
                        onClick = { navController.navigateHome() },
                        contentDescription = stringResource(R.string.nav_home),
                        imageVector = Icons.Filled.Home,
                        tint = topBarTint,
                    )
                },
                title = {
                    UrsText(
                        stringResource(currentScreenLabel(currentRoute)),
                        style = UrsTheme.typography.screenTitle,
                        color = topBarTint,
                    )
                },
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Destination.HOME.route,
            ) {
                    composable(Destination.HOME.route) {
                        HomeScreen(
                            onNavigate = { navController.navigateToDestination(it) },
                            onNavigateRoute = { route -> navController.navigate(route) },
                        )
                    }
                    composable(Destination.VEHICLE.route) {
                        VehicleHubScreen(onNavigate = { route -> navController.navigate(route) })
                    }
                    composable(VehicleRoutes.LIST) {
                        VehicleScreen(
                            onAddVehicle = { navController.navigate(VehicleRoutes.ADD) },
                            onEditVehicle = { vehicleId -> navController.navigate(VehicleRoutes.edit(vehicleId)) },
                        )
                    }
                    composable(VehicleRoutes.ADD) {
                        VehicleAddScreen(onDone = { navController.popBackStack() })
                    }
                    composable(VehicleRoutes.EDIT) { backStackEntry ->
                        val vehicleId = backStackEntry.arguments?.getString("vehicleId") ?: return@composable
                        VehicleAddScreen(vehicleId = vehicleId, onDone = { navController.popBackStack() })
                    }
                    composable(ServiceRoutes.LIST) {
                        ServiceScreen(
                            onAddService = { navController.navigate(ServiceRoutes.ADD) },
                            onEditService = { serviceId -> navController.navigate(ServiceRoutes.edit(serviceId)) },
                        )
                    }
                    composable(ServiceRoutes.ADD) {
                        ServiceAddScreen(onDone = { navController.popBackStack() })
                    }
                    composable(
                        route = ServiceRoutes.EDIT,
                        arguments = listOf(navArgument("serviceId") { type = NavType.LongType }),
                    ) { backStackEntry ->
                        val serviceId = backStackEntry.arguments?.getLong("serviceId") ?: return@composable
                        ServiceAddScreen(serviceId = serviceId, onDone = { navController.popBackStack() })
                    }
                    composable(Destination.FUEL.route) {
                        FuelHubScreen(onNavigate = { route -> navController.navigate(route) })
                    }
                    composable(FuelRoutes.FILLS) {
                        FuelScreen(
                            onAddFillUp = { navController.navigate(FuelRoutes.ADD) },
                            onEditFillUp = { fillId -> navController.navigate(FuelRoutes.edit(fillId)) },
                        )
                    }
                    composable(FuelRoutes.ADD) {
                        FuelAddScreen(onDone = { navController.popBackStack() })
                    }
                    composable(
                        route = FuelRoutes.EDIT,
                        arguments = listOf(navArgument("fillId") { type = NavType.LongType }),
                    ) { backStackEntry ->
                        val fillId = backStackEntry.arguments?.getLong("fillId") ?: return@composable
                        FuelAddScreen(fillId = fillId, onDone = { navController.popBackStack() })
                    }
                    composable(FuelRoutes.STATIONS) {
                        FuelStationsScreen(
                            onOpenMapConfirm = { navController.navigate(FuelRoutes.STATIONS_MAP) },
                            isSuperUser = app.container.authTokenStore.isSuperUser,
                        )
                    }
                    composable(FuelRoutes.STATIONS_MAP) { backStackEntry ->
                        val stationsEntry = remember(backStackEntry) { navController.getBackStackEntry(FuelRoutes.STATIONS) }
                        val stationsViewModel: StationsViewModel = viewModel(stationsEntry, factory = StationsViewModel.Factory)
                        FuelStationMapScreen(
                            viewModel = stationsViewModel,
                            onConfirm = { navController.popBackStack() },
                        )
                    }
                    composable(FuelRoutes.STATS) { FuelStatsScreen() }
                    composable(FuelRoutes.PRICE) {
                        FuelPriceAddScreen(onDone = { navController.popBackStack() })
                    }
                    composable(
                        route = Destination.INVENTORY.route,
                        // First deep-link target in the app (Issue #1) — a
                        // grouped/summary low-stock notification opens the
                        // inventories list (Inventory has its own
                        // top-level, multi-instance list-of-containers screen,
                        // same shape Shopping List always had).
                        deepLinks = listOf(navDeepLink { uriPattern = "urs://${Destination.INVENTORY.route}" }),
                    ) {
                        InventoriesScreen(
                            onOpenInventory = { inventory ->
                                navController.navigate(InventoryRoutes.products(inventory.publicId, inventory.name))
                            },
                        )
                    }
                    composable(
                        route = InventoryRoutes.PRODUCTS,
                        arguments = listOf(
                            navArgument("inventoryId") { type = NavType.StringType },
                            navArgument("inventoryName") { type = NavType.StringType },
                        ),
                        // Per-product low-stock reminder target — more
                        // specific than the inventories-list summary deep link
                        // above, since a single-product reminder can point
                        // straight at the product's own inventory.
                        deepLinks = listOf(navDeepLink { uriPattern = "urs://${InventoryRoutes.PRODUCTS}" }),
                    ) { backStackEntry ->
                        val inventoryId = backStackEntry.arguments?.getString("inventoryId") ?: return@composable
                        val inventoryName = backStackEntry.arguments?.getString("inventoryName") ?: ""
                        ProductListScreen(
                            inventoryId = inventoryId,
                            inventoryName = inventoryName,
                        )
                    }
                    composable(Destination.SHOPPING_LIST.route) {
                        ShoppingListsScreen(
                            onOpenList = { list -> navController.navigate(ShoppingListRoutes.listDetail(list.publicId)) },
                        )
                    }
                    composable(
                        route = ShoppingListRoutes.LIST_DETAIL,
                        arguments = listOf(navArgument("listId") { type = NavType.StringType }),
                    ) { backStackEntry ->
                        val listId = backStackEntry.arguments?.getString("listId") ?: return@composable
                        ListDetailScreen(listId = listId)
                    }
                    composable(Destination.BAKING.route) {
                        BakingHubScreen(
                            onOpenPlan = { plan -> navController.navigate(BakingRoutes.planDetail(plan.publicId)) },
                            onOpenHistory = { navController.navigate(BakingRoutes.HISTORY) },
                        )
                    }
                    composable(
                        route = BakingRoutes.PLAN_DETAIL,
                        arguments = listOf(navArgument("planId") { type = NavType.StringType }),
                        // Reached by BakingStepAlarmReceiver's fired notification.
                        deepLinks = listOf(navDeepLink { uriPattern = "urs://${BakingRoutes.PLAN_DETAIL}" }),
                    ) { backStackEntry ->
                        val planId = backStackEntry.arguments?.getString("planId") ?: return@composable
                        BakePlanDetailScreen(planId = planId)
                    }
                    composable(BakingRoutes.HISTORY) { BakingHistoryScreen() }
                    composable(Destination.BEER.route) { BeerScreen() }
                    composable(Destination.WORK_TIME.route) {
                        WorkTimeScreen(
                            onAddEntry = { navController.navigate(WorkTimeRoutes.ADD) },
                            onEditEntry = { entryId -> navController.navigate(WorkTimeRoutes.edit(entryId)) },
                        )
                    }
                    composable(WorkTimeRoutes.ADD) {
                        WorkTimeAddScreen(onDone = { navController.popBackStack() })
                    }
                    composable(
                        route = WorkTimeRoutes.EDIT,
                        arguments = listOf(navArgument("entryId") { type = NavType.LongType }),
                    ) { backStackEntry ->
                        val entryId = backStackEntry.arguments?.getLong("entryId") ?: return@composable
                        WorkTimeAddScreen(entryId = entryId, onDone = { navController.popBackStack() })
                    }
                    composable(Destination.LIFE_MAP.route) { LifeMapScreen() }
                    composable(ObdRoutes.LIVE) {
                        ObdLiveScreen(onOpenSetup = { navController.navigate(ObdRoutes.SETUP) })
                    }
                    composable(ObdRoutes.SETUP) { ObdSetupScreen() }
                    composable(Destination.SETTINGS.route) {
                        SettingsScreen(
                            isSuperUser = app.container.authTokenStore.isSuperUser,
                            onNavigate = { route -> navController.navigate(route) },
                        )
                    }
                    composable(SettingsRoutes.GENERAL) {
                        GeneralSettingsScreen(
                            onNavigate = { route -> navController.navigate(route) },
                        )
                    }
                    composable(SettingsRoutes.VPN) { VpnSettingsScreen() }
                    composable(SettingsRoutes.NOTIFICATIONS) { NotificationSettingsScreen() }
                    composable(SettingsRoutes.ACCOUNT) {
                        AccountSettingsScreen(
                            onNavigateToWageRules = { navController.navigate(SettingsRoutes.WAGE_RULES) },
                        )
                    }
                    composable(SettingsRoutes.WAGE_RULES) { WageRulesSettingsScreen() }
                    composable(SettingsRoutes.ABOUT) { AboutScreen() }
                    composable(SettingsRoutes.LOCATION_HISTORY) { LocationHistorySettingsScreen() }
                    composable(SettingsRoutes.WATCH_RELAY) { WatchRelaySettingsScreen() }
                    composable(SettingsRoutes.THEME) { ThemeSettingsScreen() }
                    composable(SettingsRoutes.LANGUAGE) { LanguageSettingsScreen() }
                    composable(SettingsRoutes.PRODUCT_MANAGEMENT) {
                        ProductManagementScreen(
                            isSuperUser = app.container.authTokenStore.isSuperUser,
                            onNavigateToImageReview = { navController.navigate(SettingsRoutes.IMAGE_REVIEW) },
                        )
                    }
                    composable(SettingsRoutes.ADMIN) { AdminScreen() }
                    composable(SettingsRoutes.IMAGE_REVIEW) { ImageReviewScreen() }
                }
            }
        }
    }

private fun NavHostController.navigateToDestination(destination: Destination) {
    navigate(destination.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * The Home-icon's "jump straight to Home" action, used from every non-Home
 * screen regardless of nesting depth — deliberately simpler than
 * [navigateToDestination]'s save/restoreState (that pattern is for
 * switching between Home's own top-level tiles and preserving each one's
 * scroll position; jumping home from a deeply nested screen, e.g. three
 * levels into Settings, doesn't need any of that, and save/restoreState
 * silently failed to actually navigate at all from that depth — confirmed
 * on-device, not just a theoretical concern).
 */
private fun NavHostController.navigateHome() {
    navigate(Destination.HOME.route) {
        popUpTo(graph.startDestinationId)
        launchSingleTop = true
    }
}

private val FUEL_ROUTE_LABELS = mapOf(
    FuelRoutes.FILLS to R.string.fuel_tile_fills,
    FuelRoutes.ADD to R.string.fuel_tile_add,
    FuelRoutes.STATIONS to R.string.fuel_tile_stations,
    FuelRoutes.STATIONS_MAP to R.string.station_map_title,
    FuelRoutes.STATS to R.string.fuel_tile_stats,
    FuelRoutes.PRICE to R.string.fuel_tile_price,
)

private val SETTINGS_ROUTE_LABELS = mapOf(
    SettingsRoutes.GENERAL to R.string.settings_tile_general,
    SettingsRoutes.VPN to R.string.settings_tile_vpn,
    SettingsRoutes.NOTIFICATIONS to R.string.settings_tile_notifications,
    SettingsRoutes.ACCOUNT to R.string.settings_tile_account,
    SettingsRoutes.WAGE_RULES to R.string.worktime_wage_rules_title,
    SettingsRoutes.ABOUT to R.string.settings_tile_about,
    SettingsRoutes.LOCATION_HISTORY to R.string.settings_tile_location_history,
    SettingsRoutes.WATCH_RELAY to R.string.settings_tile_watch_relay,
    SettingsRoutes.THEME to R.string.settings_tile_theme,
    SettingsRoutes.LANGUAGE to R.string.settings_tile_language,
    SettingsRoutes.PRODUCT_MANAGEMENT to R.string.settings_tile_product_management,
    SettingsRoutes.ADMIN to R.string.settings_tile_admin,
    SettingsRoutes.IMAGE_REVIEW to R.string.settings_tile_image_review,
)

private val VEHICLE_ROUTE_LABELS = mapOf(
    VehicleRoutes.LIST to R.string.vehicle_list_title,
    VehicleRoutes.ADD to R.string.vehicle_add_title,
)

private val SERVICE_ROUTE_LABELS = mapOf(
    ServiceRoutes.LIST to R.string.service_list_title,
    ServiceRoutes.ADD to R.string.service_add_title,
)

private val OBD_ROUTE_LABELS = mapOf(
    ObdRoutes.LIVE to R.string.obd_live_title,
    ObdRoutes.SETUP to R.string.obd_setup_title,
)

private val INVENTORY_ROUTE_LABELS = mapOf(
    InventoryRoutes.PRODUCTS to R.string.inventory_products_title,
)

private val SHOPPING_LIST_ROUTE_LABELS = mapOf(
    ShoppingListRoutes.LIST_DETAIL to R.string.shoppinglist_list_detail_title,
)

private val BAKING_ROUTE_LABELS = mapOf(
    BakingRoutes.PLAN_DETAIL to R.string.baking_plan_detail_title,
    BakingRoutes.HISTORY to R.string.baking_history_title,
)

// Home, Fuel, Inventory and Shopping List currently get the accent-colored
// top-bar/drawer-icon treatment; every Fuel/Inventory/Shopping List subpage
// route is prefixed accordingly, so a prefix check covers those too without
// listing each one.
private fun isAccentTopBarRoute(route: String): Boolean =
    route.startsWith("fuel/") ||
        route.startsWith("inventory/") ||
        route.startsWith("shoppinglist/") ||
        route.startsWith("vehicle/") ||
        route.startsWith("service/")

private fun currentScreenLabel(route: String): Int =
    Destination.entries.find { it.route == route }?.labelRes
        ?: FUEL_ROUTE_LABELS[route]
        ?: VEHICLE_ROUTE_LABELS[route]
        ?: SERVICE_ROUTE_LABELS[route]
        ?: OBD_ROUTE_LABELS[route]
        ?: SETTINGS_ROUTE_LABELS[route]
        ?: INVENTORY_ROUTE_LABELS[route]
        ?: SHOPPING_LIST_ROUTE_LABELS[route]
        ?: BAKING_ROUTE_LABELS[route]
        ?: R.string.app_name
