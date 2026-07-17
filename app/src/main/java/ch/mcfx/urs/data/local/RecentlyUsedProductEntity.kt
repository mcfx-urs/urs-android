package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local, read-only mirror of one entry of `GET /recently-used-product`
 * — the single data source behind both ListDetailScreen's
 * "recently used" tail section and AddProductScreen's "Zuletzt" tab.
 * Server-authoritative and small (capped at 50 server-side), so this is a
 * plain full-replace-on-refresh cache, same "no offline-first shape at all"
 * reasoning as [CatalogProductEntity] — no [SyncStatus], no local writes.
 * [rank] is assigned 0..49 in the response's own most-recent-first order at
 * refresh time, since string-sorting [lastUsedAt] after a Room read isn't a
 * reliable stand-in for the backend's own `MAX(created_at)` ordering.
 */
@Entity(tableName = "recently_used_product")
data class RecentlyUsedProductEntity(
    @PrimaryKey val catalogProductId: String,
    val lastUsedAt: String,
    val rank: Int,
)
