package ch.mcfx.urs.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
import ch.mcfx.urs.settings.SettingsRoutes
import ch.mcfx.urs.settings.SettingsScreen
import ch.mcfx.urs.settings.VpnSettingsScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
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

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Destination.HOME.route
    val isTopLevel = Destination.entries.any { it.route == currentRoute }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "🐻 urs",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp),
                )
                Destination.entries.forEach { destination ->
                    NavigationDrawerItem(
                        label = { Text(stringResource(destination.labelRes)) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        selected = destination.route == currentRoute,
                        badge = {
                            if (!destination.isAvailable) {
                                Text(
                                    stringResource(R.string.coming_soon),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        },
                        colors = if (destination.isAvailable) {
                            NavigationDrawerItemDefaults.colors()
                        } else {
                            NavigationDrawerItemDefaults.colors(
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                        },
                        onClick = {
                            if (destination.isAvailable) {
                                navController.navigateToDestination(destination)
                            }
                            coroutineScope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(currentScreenLabel(currentRoute))) },
                    navigationIcon = {
                        if (isTopLevel) {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.open_menu))
                            }
                        } else {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                )
                            }
                        }
                    },
                )
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Destination.HOME.route,
                modifier = Modifier.padding(innerPadding),
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
                composable(Destination.INVENTORY.route) {
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
                ) { backStackEntry ->
                    val categoryId = backStackEntry.arguments?.getString("categoryId") ?: return@composable
                    val categoryName = backStackEntry.arguments?.getString("categoryName") ?: ""
                    ProductListScreen(categoryId = categoryId, categoryName = categoryName)
                }
                composable(Destination.BEER.route) { BeerScreen() }
                composable(Destination.SETTINGS.route) {
                    SettingsScreen(onNavigate = { route -> navController.navigate(route) })
                }
                composable(SettingsRoutes.VPN) { VpnSettingsScreen() }
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
