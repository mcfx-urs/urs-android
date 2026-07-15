package ch.mcfx.urs.shoppinglist

import android.net.Uri

// Mirrors InventoryRoutes' shape: the hub screen (the list of lists) is
// Destination.SHOPPING_LIST's own route, LIST_DETAIL is the app's second
// parameterized route. Unlike InventoryRoutes.PRODUCTS, no list name travels
// along as a nav arg — ListDetailScreen looks its own list up from Room by
// id instead (see ListDetailViewModel), so the top app bar shows a static
// generic title (same "no dynamic-title mechanism" reasoning as
// InventoryRoutes' own doc comment) while the screen body renders the real
// name. Encoded since a list's stand-in id could, in principle, collide with
// path-reserved characters no differently than a category id could.
object ShoppingListRoutes {
    const val LISTS = "shoppinglist/lists"
    const val LIST_DETAIL = "shoppinglist/{listId}"

    fun listDetail(listId: String): String = "shoppinglist/${Uri.encode(listId)}"
}
