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
import androidx.compose.runtime.rememberCoroutineScope
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
import ch.mcfx.urs.beer.BeerScreen
import ch.mcfx.urs.fuel.FuelAddScreen
import ch.mcfx.urs.fuel.FuelHubScreen
import ch.mcfx.urs.fuel.FuelRoutes
import ch.mcfx.urs.fuel.FuelScreen
import ch.mcfx.urs.fuel.FuelStationsScreen
import ch.mcfx.urs.fuel.FuelStatsScreen
import ch.mcfx.urs.home.HomeScreen
import ch.mcfx.urs.inventory.CategoryListScreen
import ch.mcfx.urs.inventory.InventoryRoutes
import ch.mcfx.urs.inventory.ProductListScreen
import ch.mcfx.urs.settings.NotificationSettingsScreen
import ch.mcfx.urs.settings.SettingsRoutes
import ch.mcfx.urs.settings.SettingsScreen
import ch.mcfx.urs.settings.VpnSettingsScreen
import ch.mcfx.urs.settings.WorkTimeSettingsScreen
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
    val navController = rememberNavController()
    val drawerState = rememberUrsDrawerState(initialValue = UrsDrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Silent, best-effort only: the app is fully usable without any VPN set
    // up at all. If a tunnel is already configured and permissions are
    // already granted, try to bring it up in the background; otherwise do
    // nothing here — no prompts, no banner. Screens that actually need the
    // backend show their own existing error/retry state if it's unreachable.
    LaunchedEffect(Unit) {
        val app = context.applicationContext as UrsApplication
        app.container.networkGate.ensureReachable()
    }

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
                UrsText(stringResource(R.string.app_name), style = UrsTheme.typography.brand)
            }
            Destination.entries.forEach { destination ->
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
            UrsTopBar(
                navigationIcon = {
                    if (isTopLevel) {
                        UrsIconButton(
                            onClick = { coroutineScope.launch { drawerState.open() } },
                            contentDescription = stringResource(R.string.open_menu),
                            imageVector = Icons.Filled.Menu,
                        )
                    } else {
                        UrsIconButton(
                            onClick = { navController.popBackStack() },
                            contentDescription = stringResource(R.string.back),
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                            UrsText(stringResource(R.string.app_name), style = UrsTheme.typography.brand)
                        }
                    } else {
                        UrsText(stringResource(currentScreenLabel(currentRoute)), style = UrsTheme.typography.screenTitle)
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
                    composable(Destination.FUEL.route) {
                        FuelHubScreen(onNavigate = { route -> navController.navigate(route) })
                    }
                    composable(FuelRoutes.FILLS) {
                        FuelScreen(onAddFillUp = { navController.navigate(FuelRoutes.ADD) })
                    }
                    composable(FuelRoutes.ADD) {
                        FuelAddScreen(onDone = { navController.popBackStack() })
                    }
                    composable(FuelRoutes.STATIONS) { FuelStationsScreen() }
                    composable(FuelRoutes.STATS) { FuelStatsScreen() }
                    composable(
                        route = Destination.INVENTORY.route,
                        // First deep-link target in the app (Issue #1) — a
                        // grouped/summary low-stock notification opens the
                        // category list, since a summary covers several
                        // products at once rather than one specific item.
                        deepLinks = listOf(navDeepLink { uriPattern = "urs://${Destination.INVENTORY.route}" }),
                    ) {
                        CategoryListScreen(
                            onOpenCategory = { category ->
                                navController.navigate(InventoryRoutes.products(category.id, category.name))
                            },
                        )
                    }
                    composable(
                        route = InventoryRoutes.PRODUCTS,
                        arguments = listOf(
                            navArgument("categoryId") { type = NavType.StringType },
                            navArgument("categoryName") { type = NavType.StringType },
                        ),
                        // Per-product low-stock reminder target — more
                        // specific than the categories-list summary deep link
                        // above, since a single-product reminder can point
                        // straight at the product's own list.
                        deepLinks = listOf(navDeepLink { uriPattern = "urs://${InventoryRoutes.PRODUCTS}" }),
                    ) { backStackEntry ->
                        val categoryId = backStackEntry.arguments?.getString("categoryId") ?: return@composable
                        val categoryName = backStackEntry.arguments?.getString("categoryName") ?: ""
                        ProductListScreen(categoryId = categoryId, categoryName = categoryName)
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
                    composable(Destination.SETTINGS.route) {
                        SettingsScreen(onNavigate = { route -> navController.navigate(route) })
                    }
                    composable(SettingsRoutes.VPN) { VpnSettingsScreen() }
                    composable(SettingsRoutes.NOTIFICATIONS) { NotificationSettingsScreen() }
                    composable(SettingsRoutes.WORK_TIME) { WorkTimeSettingsScreen() }
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
    FuelRoutes.STATS to R.string.fuel_tile_stats,
)

private val SETTINGS_ROUTE_LABELS = mapOf(
    SettingsRoutes.VPN to R.string.settings_tile_vpn,
    SettingsRoutes.NOTIFICATIONS to R.string.settings_tile_notifications,
    SettingsRoutes.WORK_TIME to R.string.settings_tile_work_time,
)

private val INVENTORY_ROUTE_LABELS = mapOf(
    InventoryRoutes.PRODUCTS to R.string.inventory_products_title,
)

private fun currentScreenLabel(route: String): Int =
    Destination.entries.find { it.route == route }?.labelRes
        ?: FUEL_ROUTE_LABELS[route]
        ?: SETTINGS_ROUTE_LABELS[route]
        ?: INVENTORY_ROUTE_LABELS[route]
        ?: R.string.app_name
