package ch.mcfx.urs.assets

// Assets CRUD routes, registered directly in the same top-level NavHost
// (ch.mcfx.urs.navigation.AppNavigation) — same pattern as ServiceRoutes.
object AssetsRoutes {
    const val LIST = "assets/list"
    const val ADD = "assets/add"
    const val EDIT = "assets/edit/{assetId}"

    fun edit(assetId: Long) = "assets/edit/$assetId"
}
