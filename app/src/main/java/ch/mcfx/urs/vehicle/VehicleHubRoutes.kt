package ch.mcfx.urs.vehicle

// Drawer-level hub grouping vehicle-related sections (Fuel, OBD, ...).
// Sub-routes (fuel/*, obd/*) are registered directly in the same
// top-level NavHost (ch.mcfx.urs.navigation.AppNavigation) but aren't
// part of the Destination enum themselves - same pattern as FuelRoutes'
// own sub-routes.
object VehicleHubRoutes {
    const val HUB = "vehicle/hub"
}
