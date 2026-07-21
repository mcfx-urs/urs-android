package ch.mcfx.urs.car

// Drawer-level hub grouping car-related sections (Fuel, OBD, ...). Sub-routes
// (fuel/*, obd/*) are registered directly in the same top-level NavHost
// (ch.mcfx.urs.navigation.AppNavigation) but aren't part of the Destination
// enum themselves - same pattern as FuelRoutes' own sub-routes.
object CarRoutes {
    const val HUB = "car/hub"
}
