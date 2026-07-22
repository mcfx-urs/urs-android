package ch.mcfx.urs.inventory

import android.net.Uri

// Inventory has a top-level "list of inventories" screen (mirrors
// ShoppingListRoutes' own LISTS/LIST_DETAIL two-tier shape — an inventory is
// a named, multi-instance, shareable container just like a shopping
// list). PRODUCTS travels down the inventoryId/inventoryName it was opened
// from — inventoryName is purely for display (no dynamic-title mechanism
// exists in AppNavigation today, same reasoning as this object's own
// earlier history), not re-fetched from the backend. An inventory's
// tracked products are one flat tile grid, with no category-tile
// drill-down step in between.
object InventoryRoutes {
    const val INVENTORIES = "inventory/inventories"
    const val PRODUCTS = "inventory/products/{inventoryId}/{inventoryName}"

    fun products(inventoryId: String, inventoryName: String): String =
        "inventory/products/${Uri.encode(inventoryId)}/${Uri.encode(inventoryName)}"
}
