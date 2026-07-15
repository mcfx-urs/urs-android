package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local, read-only mirror of a predefined catalog product — unlike
 * [InventoryCategoryEntity]/[InventoryProductEntity] this has no
 * offline-first/outbox shape at all: `catalog_product` is server-authoritative
 * reference data the client never creates or edits, only caches for offline
 * search, so [id] is simply the real backend id (no local-vs-server split,
 * no [SyncStatus]). [searchTerms] is kept as the raw semicolon-joined string
 * the backend sends, not split into a list — splitting only happens where a
 * search actually needs it.
 */
@Entity(tableName = "catalog_product")
data class CatalogProductEntity(
    @PrimaryKey val id: String,
    val categoryName: String,
    val name: String,
    val searchTerms: String? = null,
    val brands: String? = null,
    val popularityIndex: Int? = null,
    val catalogImageId: Int? = null,
)
