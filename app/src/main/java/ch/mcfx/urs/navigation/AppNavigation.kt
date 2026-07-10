package ch.mcfx.urs.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ch.mcfx.urs.R
import ch.mcfx.urs.fuel.FuelScreen
import ch.mcfx.urs.home.HomeScreen
import ch.mcfx.urs.settings.SettingsScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Destination.HOME.route

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
                    title = { Text(stringResource(currentDestinationLabel(currentRoute))) },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.open_menu))
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
                composable(Destination.FUEL.route) { FuelScreen() }
                composable(Destination.SETTINGS.route) { SettingsScreen() }
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

private fun currentDestinationLabel(route: String): Int =
    Destination.entries.find { it.route == route }?.labelRes ?: R.string.app_name
