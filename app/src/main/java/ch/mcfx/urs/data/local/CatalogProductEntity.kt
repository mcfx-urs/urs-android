package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local, read-only mirror of a predefined catalog product — unlike
 * [InventoryEntity]/[InventoryProductEntity] this has no offline-first/
 * outbox shape at all: `catalog_product` is server-authoritative reference
 * data the client never creates or edits through this cache (see
 * [ch.mcfx.urs.data.CatalogRepository.createProduct] for the one exception —
 * a direct, synchronous REST create, not an offline-first write against
 * this entity), only caches for offline search, so [id] is simply the real
 * backend id (no local-vs-server split, no [SyncStatus]). [searchTerms] is
 * kept as the raw semicolon-joined string the backend sends, not split into
 * a list — splitting only happens where a search actually needs it.
 * [catalogCategoryId] is nullable — a product can be uncategorized — and
 * points at a [CatalogCategoryEntity] row, replacing the old
 * `inventory_product`-owned category link. [recentNote1]/[recentNote2]/
 * [recentNote3] moved here from the old `inventory_product` — they're
 * now global per catalog product, since `list_item` no longer references
 * `inventory_product` at all. [source] ("manual" vs an external-catalog import) gates
 * whether the Product Management screen offers edit/delete for this row.
 */
@Entity(tableName = "catalog_product")
data class CatalogProductEntity(
    @PrimaryKey val id: String,
    val categoryName: String,
    val catalogCategoryId: String? = null,
    val name: String,
    val searchTerms: String? = null,
    val brands: String? = null,
    val popularityIndex: Int? = null,
    val catalogImageId: Int? = null,
    val recentNote1: String? = null,
    val recentNote2: String? = null,
    val recentNote3: String? = null,
    val source: String = "",
)
