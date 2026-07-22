package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local, always-available mirror of an inventory product — tracks
 * one catalog product's on-hand quantity within one [InventoryEntity].
 * Product identity and grouping come entirely through [catalogProductId] →
 * the cached [CatalogProductEntity] (and its own
 * [CatalogProductEntity.catalogCategoryId] → [CatalogCategoryEntity]) — this
 * entity itself carries only the per-inventory tracking state, mirroring the
 * backend's own `inventory_product` table exactly (no more `name`/
 * `categoryId`/recent-note columns, those moved to `catalog_product` or
 * disappeared entirely). [inventoryId] holds the parent inventory's
 * [InventoryEntity.publicId] as it stood at creation time (a real backend
 * id, or a not-yet-synced stand-in) — a queued product create resolves it to
 * the parent's real id at replay time (see `SyncManager`) and corrects this
 * column then, so an inventory that was still offline when its products
 * were added ends up consistent once both have synced.
 */
@Entity(tableName = "inventory_product")
data class InventoryProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Null until this row has been confirmed by the backend (see
    // InventoryProductDao.markSynced) — the real inventory_product_id.
    val serverId: String? = null,
    // Links back to the OutboxMutationEntity row still driving this
    // product's sync, null once synced.
    val outboxId: Long? = null,
    val inventoryId: String,
    // Always set — no more "not from catalog" case, every
    // inventory product now tracks a real catalog_product.
    val catalogProductId: String,
    // Null means "not currently tracked" — mirrors the backend's
    // nullable-column convention (see InventoryRepository.updateProductQuantity).
    val quantity: Int? = null,
    val firstThreshold: Int? = null,
    val secondThreshold: Int? = null,
    val reminderThreshold: Int? = null,
    val reminderHour: Int? = null,
    val reminderMinute: Int? = null,
    val syncStatus: SyncStatus,
)

/**
 * Same stand-in-until-synced scheme as [InventoryEntity.publicId] — see
 * [localInventoryProductId] for the reverse lookup a caller uses to resolve
 * its still-pending row at replay time.
 */
val InventoryProductEntity.publicId: String
    get() = serverId ?: localIdStandIn(id)

/** Reverses [publicId] — same shape as [localInventoryId]. */
fun localInventoryProductId(value: String): Long? = parseLocalIdStandIn(value)
