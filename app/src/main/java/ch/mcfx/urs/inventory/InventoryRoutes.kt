package ch.mcfx.urs.inventory

import android.net.Uri

// Inventory's top-level screen is the category list itself (unlike Fuel/
// Settings, which are hubs with fixed tiles) — Destination.INVENTORY points
// straight at CATEGORIES. PRODUCTS is the app's first parameterized route:
// categoryName travels along as a plain nav arg purely for display (no
// dynamic-title mechanism exists in AppNavigation today), not re-fetched
// from the backend. Encoded since category names may contain spaces.
object InventoryRoutes {
    const val CATEGORIES = "inventory/categories"
    const val PRODUCTS = "inventory/products/{categoryId}/{categoryName}"

    fun products(categoryId: String, categoryName: String): String =
        "inventory/products/$categoryId/${Uri.encode(categoryName)}"
}
