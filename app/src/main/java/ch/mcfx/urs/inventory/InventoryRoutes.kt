package ch.mcfx.urs.inventory

import android.net.Uri

// Inventory gained a top-level "list of inventories" screen (mirrors
// ShoppingListRoutes' own LISTS/LIST_DETAIL two-tier shape — an inventory is
// now a named, multi-instance, shareable container just like a shopping
// list). CATEGORIES/PRODUCTS both travel down the inventoryId/inventoryName
// they were opened from, plus PRODUCTS additionally the categoryId/
// categoryName drilled into — categoryId/categoryName are purely for
// display/filtering (no dynamic-title mechanism exists in AppNavigation
// today, same reasoning as this object's own pre- history), not
// re-fetched from the backend. [NO_CATEGORY] stands in for "uncategorized"
// (a null `catalog_product_catalog_category_id`), since a nav-arg path
// segment can't itself be empty.
object InventoryRoutes {
    const val NO_CATEGORY = "none"

    const val INVENTORIES = "inventory/inventories"
    const val CATEGORIES = "inventory/categories/{inventoryId}/{inventoryName}"
    const val PRODUCTS = "inventory/products/{inventoryId}/{inventoryName}/{categoryId}/{categoryName}"

    fun categories(inventoryId: String, inventoryName: String): String =
        "inventory/categories/${Uri.encode(inventoryId)}/${Uri.encode(inventoryName)}"

    fun products(inventoryId: String, inventoryName: String, categoryId: String?, categoryName: String): String =
        "inventory/products/${Uri.encode(inventoryId)}/${Uri.encode(inventoryName)}/" +
            "${Uri.encode(categoryId ?: NO_CATEGORY)}/${Uri.encode(categoryName)}"
}
