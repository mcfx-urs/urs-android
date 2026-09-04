package ch.mcfx.urs.vehicle

// Vehicle CRUD routes, registered directly in the same top-level NavHost
// (ch.mcfx.urs.navigation.AppNavigation) but not part of the Destination
// enum — same pattern as FuelRoutes' own sub-routes. Deliberately not
// named the same as VehicleHubRoutes (the Vehicle hub's own single HUB
// route) to avoid a naming collision between the two.
object VehicleRoutes {
    const val LIST = "vehicle/list"
    const val ADD = "vehicle/add"
    const val EDIT = "vehicle/edit/{vehicleId}"
    const val VIEW = "vehicle/view/{vehicleId}"

    fun edit(vehicleId: String) = "vehicle/edit/$vehicleId"
    fun view(vehicleId: String) = "vehicle/view/$vehicleId"
}
