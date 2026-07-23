package ch.mcfx.urs.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsBar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import ch.mcfx.urs.R
import ch.mcfx.urs.car.CarRoutes
import ch.mcfx.urs.fuel.FuelRoutes
import ch.mcfx.urs.inventory.InventoryRoutes
import ch.mcfx.urs.settings.SettingsRoutes
import ch.mcfx.urs.shoppinglist.ShoppingListRoutes
import ch.mcfx.urs.worktime.WorkTimeRoutes

// Mirrors the web app's sidebar (urs-frontend App.vue). Destinations with
// isAvailable = false have no route or screen yet — they exist here only so
// the drawer and home dashboard can show the app's full planned scope.
// Flip to true and register a NavHost route once the feature is ported.
enum class Destination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val isAvailable: Boolean,
    // FUEL stays registered (route/icon/label) for the fuel/hub NavHost
    // route (reached via CAR's own Fuel tile), but is hidden from the
    // drawer — CAR (Fuel + OBD) is the drawer-level entry point for
    // car/vehicle-related screens. GOKART/PRICE_MONITOR/K are also
    // drawer-hidden (): still shown as "soon" placeholders on Home,
    // but the drawer only lists destinations with a real screen behind them.
    val showInDrawer: Boolean = true,
) {
    // Declaration order is also the drawer's display order (see
    // AppNavigation's `Destination.entries.filter { it.showInDrawer }`) —
    // kept matching 's specified drawer order: Home, Vehicle,
    // Shopping, Inventory, Work Time, Life Map, Beer, Settings.
    HOME("home", R.string.nav_home, Icons.Filled.Home, isAvailable = true),
    CAR(CarRoutes.HUB, R.string.nav_vehicle, Icons.Filled.DirectionsCar, isAvailable = true),
    FUEL(FuelRoutes.HUB, R.string.nav_fuel, Icons.Filled.LocalGasStation, isAvailable = true, showInDrawer = false),
    SHOPPING_LIST(ShoppingListRoutes.LISTS, R.string.nav_shopping_list, Icons.Filled.ShoppingCart, isAvailable = true),
    INVENTORY(InventoryRoutes.INVENTORIES, R.string.nav_inventory, Icons.Filled.Inventory2, isAvailable = true),
    WORK_TIME(WorkTimeRoutes.HISTORY, R.string.nav_work_time, Icons.Filled.Schedule, isAvailable = true),
    LIFE_MAP("life-map", R.string.nav_life_map, Icons.Filled.Map, isAvailable = true),
    BEER("beer", R.string.nav_beer, Icons.Filled.SportsBar, isAvailable = true),
    // Placeholder only — purpose not decided yet, reserves a Home grid slot
    // (see  in the shared tracker) the same way GOKART/PRICE_MONITOR
    // already do for their own not-yet-built features.
    K("k", R.string.nav_k, Icons.Filled.Star, isAvailable = false, showInDrawer = false),
    GOKART("gokart", R.string.nav_gokart, Icons.Filled.Flag, isAvailable = false, showInDrawer = false),
    PRICE_MONITOR("price_monitor", R.string.nav_price_monitor, Icons.Filled.QrCodeScanner, isAvailable = false, showInDrawer = false),
    SETTINGS(SettingsRoutes.HUB, R.string.nav_settings, Icons.Filled.Settings, isAvailable = true),
}
