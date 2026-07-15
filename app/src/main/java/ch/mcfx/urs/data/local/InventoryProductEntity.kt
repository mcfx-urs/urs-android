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
    // Not written by anything yet — the column exists so this entity already
    // round-trips correctly once the backend field it mirrors lands
    // (tracked separately from this offline-cache phase).
    val catalogProductId: String? = null,
    val syncStatus: SyncStatus,
)

/** Same stand-in-until-synced scheme as [InventoryCategoryEntity.publicId]. */
val InventoryProductEntity.publicId: String
    get() = serverId ?: "local-$id"
