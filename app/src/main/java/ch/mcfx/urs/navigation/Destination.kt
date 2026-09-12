package ch.mcfx.urs.navigation

import ch.mcfx.urs.R
import ch.mcfx.urs.baking.BakingRoutes
import ch.mcfx.urs.chores.ChoresRoutes
import ch.mcfx.urs.vehicle.VehicleHubRoutes
import ch.mcfx.urs.fuel.FuelRoutes
import ch.mcfx.urs.inventory.InventoryRoutes
import ch.mcfx.urs.kanban.KanbanRoutes
import ch.mcfx.urs.notes.NotesRoutes
import ch.mcfx.urs.settings.SettingsRoutes
import ch.mcfx.urs.shoppinglist.ShoppingListRoutes
import ch.mcfx.urs.voicenotes.VoiceNotesRoutes
import ch.mcfx.urs.worktime.WorkTimeRoutes

// Mirrors the web app's sidebar (urs-frontend App.vue). Destinations with
// isAvailable = false have no route or screen yet — they exist here only so
// the Home dashboard can show the app's full planned scope. Flip to true and
// register a NavHost route once the feature is ported.
enum class Destination(
    val route: String,
    val labelRes: Int,
    val isAvailable: Boolean,
) {
    HOME("home", R.string.nav_home, isAvailable = true),
    VEHICLE(VehicleHubRoutes.HUB, R.string.nav_vehicle, isAvailable = true),
    // FUEL stays registered (route/label) for the fuel/hub NavHost route
    // (reached via VEHICLE's own Fuel tile) but has no Home tile of its own.
    FUEL(FuelRoutes.HUB, R.string.nav_fuel, isAvailable = true),
    SHOPPING_LIST(ShoppingListRoutes.LISTS, R.string.nav_shopping_list, isAvailable = true),
    INVENTORY(InventoryRoutes.INVENTORIES, R.string.nav_inventory, isAvailable = true),
    WORK_TIME(WorkTimeRoutes.HISTORY, R.string.nav_work_time, isAvailable = true),
    LIFE_MAP("life-map", R.string.nav_life_map, isAvailable = true),
    BEER("beer", R.string.nav_beer, isAvailable = true),
    BAKING(BakingRoutes.PLANS, R.string.nav_baking, isAvailable = true),
    NOTES(NotesRoutes.LIST, R.string.nav_notes, isAvailable = true),
    CHORES(ChoresRoutes.MONTH, R.string.nav_chores, isAvailable = true),
    VOICE_NOTES(VoiceNotesRoutes.LIST, R.string.nav_voice_notes, isAvailable = true),
    KANBAN(KanbanRoutes.BOARDS, R.string.nav_kanban, isAvailable = true),
    // Placeholder only — purpose not decided yet, reserves a Home grid slot
    // the same way GOKART/PRICE_MONITOR already do for their own
    // not-yet-built features.
    K("k", R.string.nav_k, isAvailable = false),
    GOKART("gokart", R.string.nav_gokart, isAvailable = false),
    PRICE_MONITOR("price_monitor", R.string.nav_price_monitor, isAvailable = false),
    SETTINGS(SettingsRoutes.HUB, R.string.nav_settings, isAvailable = true),
}
