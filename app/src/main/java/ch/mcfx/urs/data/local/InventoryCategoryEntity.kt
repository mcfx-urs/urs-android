package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local, always-available mirror of an inventory category — mirrors
 * [FillEntity]'s offline-first shape (stable local [id], nullable
 * [serverId]/[outboxId] until confirmed, [syncStatus]). The local [id] never
 * changes once assigned, even after this row syncs — only [serverId] gets
 * filled in — so anything referencing this row locally (see
 * [InventoryProductEntity.categoryId] resolution in `SyncManager`) never
 * has to deal with its key moving out from under it.
 */
@Entity(tableName = "inventory_category")
data class InventoryCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Null until this row has been confirmed by the backend (see
    // InventoryCategoryDao.markSynced) — the real inventory_category_id.
    val serverId: String? = null,
    // Links back to the OutboxMutationEntity row still driving this
    // category's sync, null once synced.
    val outboxId: Long? = null,
    val name: String,
    val syncStatus: SyncStatus,
)

// Prefix for a not-yet-synced category's stand-in id (see [publicId]) — used
// nowhere else, so a real server id can never collide with it in practice.
private const val LOCAL_ID_PREFIX = "local-"

/**
 * The id the UI/outbox payloads address this category by: the real backend
 * id once known, otherwise a stand-in derived from the stable local [id] —
 * see [localInventoryCategoryId] for the reverse lookup a queued product
 * create uses to resolve its still-pending parent category at replay time.
 */
val InventoryCategoryEntity.publicId: String
    get() = serverId ?: "$LOCAL_ID_PREFIX$id"

/**
 * Reverses [publicId]: the local row id encoded in a not-yet-synced
 * category's stand-in id, or `null` if [value] is already a real server id
 * (nothing to resolve).
 */
fun localInventoryCategoryId(value: String): Long? =
    value.takeIf { it.startsWith(LOCAL_ID_PREFIX) }?.removePrefix(LOCAL_ID_PREFIX)?.toLongOrNull()
