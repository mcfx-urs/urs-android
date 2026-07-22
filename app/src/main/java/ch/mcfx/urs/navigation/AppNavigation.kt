package ch.mcfx.urs.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import ch.mcfx.urs.beer.BeerScreen
import ch.mcfx.urs.car.CarHubScreen
import ch.mcfx.urs.fuel.FuelAddScreen
import ch.mcfx.urs.fuel.FuelHubScreen
import ch.mcfx.urs.fuel.FuelRoutes
import ch.mcfx.urs.fuel.FuelScreen
import ch.mcfx.urs.fuel.FuelStationMapScreen
import ch.mcfx.urs.fuel.FuelStationsScreen
import ch.mcfx.urs.fuel.FuelStatsScreen
import ch.mcfx.urs.fuel.StationsViewModel
import ch.mcfx.urs.home.HomeScreen
import ch.mcfx.urs.data.local.publicId
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
import ch.mcfx.urs.settings.LocationHistorySettingsScreen
import ch.mcfx.urs.settings.NotificationSettingsScreen
import ch.mcfx.urs.settings.ProductManagementScreen
import ch.mcfx.urs.settings.SettingsRoutes
import ch.mcfx.urs.settings.SettingsScreen
import ch.mcfx.urs.settings.VpnSettingsScreen
import ch.mcfx.urs.shoppinglist.ListDetailScreen
import ch.mcfx.urs.shoppinglist.ShoppingListRoutes
import ch.mcfx.urs.shoppinglist.ShoppingListsScreen
import ch.mcfx.urs.ui.components.UrsDrawerValue
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsNavigationDrawer
import ch.mcfx.urs.ui.components.UrsNavigationDrawerItem
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTopBar
import ch.mcfx.urs.ui.components.rememberUrsDrawerState
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import ch.mcfx.urs.worktime.WorkTimeAddScreen
import ch.mcfx.urs.worktime.WorkTimeRoutes
import ch.mcfx.urs.worktime.WorkTimeScreen
import kotlinx.coroutines.launch

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
        BiometricUnlockScreen(gate = biometricGate, onUsePasswordInstead = app.container.authRepository::logout)
        return
    }

    val navController = rememberNavController()
    val drawerState = rememberUrsDrawerState(initialValue = UrsDrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // Hands the controller back to MainActivity so a notification tap can
    // deep-link while the app is already running (onNewIntent) as well as
    // on cold start (handled once here, for the Activity's launching intent).
    LaunchedEffect(navController) { onNavControllerReady(navController) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Destination.HOME.route
    val isTopLevel = Destination.entries.any { it.route == currentRoute }

    UrsNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.padding(Spacing.l),
            ) {
                Image(
                    painter = painterResource(R.drawable.urs_bear_logo),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
                UrsText(
                    stringResource(R.string.app_name),
                    style = UrsTheme.typography.brand,
                    color = UrsTheme.colors.accent,
                )
            }
            Destination.entries.filter { it.showInDrawer }.forEach { destination ->
                UrsNavigationDrawerItem(
                    label = stringResource(destination.labelRes),
                    icon = destination.icon,
                    selected = destination.route == currentRoute,
                    enabled = destination.isAvailable,
                    trailing = if (destination.isAvailable) {
                        null
                    } else {
                        {
                            UrsPill(
                                text = stringResource(R.string.coming_soon).uppercase(),
                                containerColor = UrsTheme.colors.surface,
                                contentColor = UrsTheme.colors.onSurfaceMuted,
                                style = UrsTheme.typography.tag,
                            )
                        }
                    },
                    onClick = {
                        navController.navigateToDestination(destination)
                        coroutineScope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize().background(UrsTheme.colors.background)) {
            val topBarTint = if (isAccentTopBarRoute(currentRoute)) UrsTheme.colors.accent else UrsTheme.colors.onSurface
            UrsTopBar(
                navigationIcon = {
                    if (isTopLevel) {
                        UrsIconButton(
                            onClick = { coroutineScope.launch { drawerState.open() } },
                            contentDescription = stringResource(R.string.open_menu),
                            imageVector = Icons.Filled.Menu,
                            tint = topBarTint,
                        )
                    } else {
                        UrsIconButton(
                            onClick = { navController.popBackStack() },
                            contentDescription = stringResource(R.string.back),
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            tint = topBarTint,
                        )
                    }
                },
                title = {
                    if (currentRoute == Destination.HOME.route) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            Image(
                                painter = painterResource(R.drawable.urs_bear_logo),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                            )
                            UrsText(stringResource(R.string.app_name), style = UrsTheme.typography.brand, color = topBarTint)
                        }
                    } else {
                        UrsText(
                            stringResource(currentScreenLabel(currentRoute)),
                            style = UrsTheme.typography.screenTitle,
                            color = topBarTint,
                        )
                    }
                },
            )
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = Destination.HOME.route,
                ) {
                    composable(Destination.HOME.route) {
                        HomeScreen(onNavigate = { navController.navigateToDestination(it) })
                    }
                    composable(Destination.CAR.route) {
                        CarHubScreen(onNavigate = { route -> navController.navigate(route) })
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
                        FuelStationsScreen(onOpenMapConfirm = { navController.navigate(FuelRoutes.STATIONS_MAP) })
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
                        GeneralSettingsScreen(onNavigate = { route -> navController.navigate(route) })
                    }
                    composable(SettingsRoutes.VPN) { VpnSettingsScreen() }
                    composable(SettingsRoutes.NOTIFICATIONS) { NotificationSettingsScreen() }
                    composable(SettingsRoutes.ACCOUNT) { AccountSettingsScreen() }
                    composable(SettingsRoutes.ABOUT) { AboutScreen() }
                    composable(SettingsRoutes.LOCATION_HISTORY) { LocationHistorySettingsScreen() }
                    composable(SettingsRoutes.PRODUCT_MANAGEMENT) { ProductManagementScreen() }
                    composable(SettingsRoutes.ADMIN) { AdminScreen() }
                }
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

private val FUEL_ROUTE_LABELS = mapOf(
    FuelRoutes.FILLS to R.string.fuel_tile_fills,
    FuelRoutes.ADD to R.string.fuel_tile_add,
    FuelRoutes.STATIONS to R.string.fuel_tile_stations,
    FuelRoutes.STATIONS_MAP to R.string.station_map_title,
    FuelRoutes.STATS to R.string.fuel_tile_stats,
)

private val SETTINGS_ROUTE_LABELS = mapOf(
    SettingsRoutes.GENERAL to R.string.settings_tile_general,
    SettingsRoutes.VPN to R.string.settings_tile_vpn,
    SettingsRoutes.NOTIFICATIONS to R.string.settings_tile_notifications,
    SettingsRoutes.ACCOUNT to R.string.settings_tile_account,
    SettingsRoutes.ABOUT to R.string.settings_tile_about,
    SettingsRoutes.LOCATION_HISTORY to R.string.settings_tile_location_history,
    SettingsRoutes.PRODUCT_MANAGEMENT to R.string.settings_tile_product_management,
    SettingsRoutes.ADMIN to R.string.settings_tile_admin,
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

// Home, Fuel, Inventory and Shopping List currently get the accent-colored
// top-bar/drawer-icon treatment; every Fuel/Inventory/Shopping List subpage
// route is prefixed accordingly, so a prefix check covers those too without
// listing each one.
private fun isAccentTopBarRoute(route: String): Boolean =
    route == Destination.HOME.route ||
        route.startsWith("fuel/") ||
        route.startsWith("inventory/") ||
        route.startsWith("shoppinglist/")

private fun currentScreenLabel(route: String): Int =
    Destination.entries.find { it.route == route }?.labelRes
        ?: FUEL_ROUTE_LABELS[route]
        ?: OBD_ROUTE_LABELS[route]
        ?: SETTINGS_ROUTE_LABELS[route]
        ?: INVENTORY_ROUTE_LABELS[route]
        ?: SHOPPING_LIST_ROUTE_LABELS[route]
        ?: R.string.app_name
