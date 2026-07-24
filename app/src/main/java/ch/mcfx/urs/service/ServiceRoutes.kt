package ch.mcfx.urs.service

// Service/maintenance-history CRUD routes, registered directly in the same
// top-level NavHost (ch.mcfx.urs.navigation.AppNavigation) but not part of
// the Destination enum — same pattern as VehicleRoutes' own sub-routes.
object ServiceRoutes {
    const val LIST = "service/list"
    const val ADD = "service/add"
    const val EDIT = "service/edit/{serviceId}"

    fun edit(serviceId: Long) = "service/edit/$serviceId"
}
