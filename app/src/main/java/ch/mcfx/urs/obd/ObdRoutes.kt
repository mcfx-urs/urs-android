package ch.mcfx.urs.obd

// Sub-routes within the OBD section, registered in the same top-level NavHost
// as Destination's routes (ch.mcfx.urs.navigation.AppNavigation) but not part
// of the Destination enum - reached only via the Car hub (ch.mcfx.urs.car).
object ObdRoutes {
    const val LIVE = "obd/live"
    const val SETUP = "obd/setup"
}
