package ch.mcfx.urs.fuel

// Sub-routes within the Fuel section, registered in the same top-level
// NavHost as Destination's routes (ch.mcfx.urs.navigation.AppNavigation) but
// deliberately not part of the Destination enum — they don't appear in the
// drawer or the Home tile grid, only inside the Fuel hub.
object FuelRoutes {
    const val HUB = "fuel/hub"
    const val FILLS = "fuel/fills"
    const val ADD = "fuel/add"
    const val STATIONS = "fuel/stations"
    const val STATS = "fuel/stats"
}
