package ch.mcfx.urs.data.local

import androidx.room.Entity

/**
 * Local, read-only mirror of one entry of `GET /recently-used-product?list_id=` —
 * the single data source behind both ListDetailScreen's
 * "recently used" tail section and AddProductScreen's "Zuletzt" tab.
 * Server-authoritative and small (capped at 50 server-side per list), so
 * this is a plain full-replace-on-refresh cache, same "no offline-first
 * shape at all" reasoning as [CatalogProductEntity] — no [SyncStatus], no
 * local writes. [rank] is assigned 0..49 in the response's own
 * most-recent-first order at refresh time, since string-sorting
 * [lastUsedAt] after a Room read isn't a reliable stand-in for the
 * backend's own `MAX(created_at)` ordering.
 *
 * Keyed by ([catalogProductId], [listId]) rather than [catalogProductId]
 * alone — the same product can be "recently used" independently in
 * different lists (e.g. groceries in a food list, screws in a hardware
 * list), so one product may legitimately have several rows, one per list.
 */
@Entity(tableName = "recently_used_product", primaryKeys = ["catalogProductId", "listId"])
data class RecentlyUsedProductEntity(
    val catalogProductId: String,
    val listId: String,
    val lastUsedAt: String,
    val rank: Int,
)
