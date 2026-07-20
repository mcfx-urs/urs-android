package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local, read-only mirror of a shared catalog category — same
 * "server-authoritative reference data, no offline-first/outbox shape at
 * all" reasoning as [CatalogProductEntity]: [id] is simply the real backend
 * `catalog_category_id`, no local-vs-server split, no [SyncStatus].
 * Replaces the old per-user `inventory_category` entirely — grouping now
 * comes from [CatalogProductEntity.catalogCategoryId] pointing at a row
 * here, shared across every inventory/list rather than owned by one user.
 * [source] ("manual" vs "external_catalog", ) gates whether the Product
 * Management screen offers edit/delete for this row. [catalogImageId] is
 * only ever set through that same edit flow — categories have no image at
 * creation time.
 */
@Entity(tableName = "catalog_category")
data class CatalogCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val source: String = "",
    val catalogImageId: Int? = null,
)

