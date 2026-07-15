package ch.mcfx.urs.worktime

// The "add entry" sub-route, registered in the same top-level NavHost as
// Destination's routes (ch.mcfx.urs.navigation.AppNavigation) but
// deliberately not part of the Destination enum — mirrors FuelRoutes.ADD.
object WorkTimeRoutes {
    const val HISTORY = "work_time"
    const val ADD = "work_time/add"
}
