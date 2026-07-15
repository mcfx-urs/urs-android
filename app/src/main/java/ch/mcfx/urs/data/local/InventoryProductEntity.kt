package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local, always-available mirror of an inventory product — same
 * offline-first shape as [InventoryCategoryEntity]/[FillEntity]. [categoryId]
 * holds the parent category's [InventoryCategoryEntity.publicId] as it stood
 * at creation time (a real backend id, or a not-yet-synced stand-in) — a
 * queued product create resolves it to the parent's real id at replay time
 * (see `SyncManager`) and corrects this column then, so a category that was
 * still offline when its products were added ends up consistent once both
 * have synced.
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
    val categoryId: String,
    val name: String,
    // Null means "not currently tracked" — mirrors the backend's
    // nullable-column convention (see InventoryRepository.updateProductQuantity).
    val quantity: Int? = null,
    val firstThreshold: Int? = null,
    val secondThreshold: Int? = null,
    val reminderThreshold: Int? = null,
    val reminderHour: Int? = null,
    val reminderMinute: Int? = null,
    // Set when this product was created from a catalog entry (see
    // ShoppingListRepository.addCatalogProduct) — the backend accepts this
    // on create (inventory_product_catalog_product_id) but never echoes it
    // back on any GET, so [InventoryProductDao.upsertFromServer] preserves
    // whatever value a local row already has across a backend refresh
    // rather than trusting the (always-absent) server value.
    val catalogProductId: String? = null,
    val syncStatus: SyncStatus,
)

/**
 * Same stand-in-until-synced scheme as [InventoryCategoryEntity.publicId] —
 * see [localInventoryProductId] for the reverse lookup a queued list-item
 * create uses to resolve its still-pending parent product at replay time.
 */
val InventoryProductEntity.publicId: String
    get() = serverId ?: localIdStandIn(id)

/** Reverses [publicId] — same shape as [localInventoryCategoryId]. */
fun localInventoryProductId(value: String): Long? = parseLocalIdStandIn(value)
