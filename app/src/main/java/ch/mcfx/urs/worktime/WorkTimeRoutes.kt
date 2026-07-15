package ch.mcfx.urs.worktime

// The "add"/"edit" sub-routes, registered in the same top-level NavHost as
// Destination's routes (ch.mcfx.urs.navigation.AppNavigation) but
// deliberately not part of the Destination enum — mirrors FuelRoutes.ADD.
// EDIT follows InventoryRoutes.PRODUCTS' parameterized-route convention.
object WorkTimeRoutes {
    const val HISTORY = "work_time"
    const val ADD = "work_time/add"
    const val EDIT = "work_time/edit/{entryId}"

    fun edit(entryId: Long): String = "work_time/edit/$entryId"
}
