package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local, always-available mirror of one entry on a shopping list — same
 * offline-first shape as [InventoryProductEntity]. [listId] and [productId]
 * hold their parent rows' [ListEntity.publicId]/[InventoryProductEntity
 * .publicId] as they stood at creation time (a real backend id, or a
 * not-yet-synced stand-in) — a queued item create resolves both to their
 * real backend ids at replay time (see `SyncManager`) and corrects these
 * columns then, same reasoning as [InventoryProductEntity.categoryId]'s doc
 * comment. Also supports offline-first update/delete via the outbox — see
 * [ListEntity]'s doc comment for why this differs from inventory's
 * create-only shape.
 */
@Entity(tableName = "list_item")
data class ListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Null until this row has been confirmed by the backend — the real list_item_id.
    val serverId: String? = null,
    // Links back to the OutboxMutationEntity row still driving this item's
    // sync, null once synced.
    val outboxId: Long? = null,
    val listId: String,
    val productId: String,
    // Null = no note — mirrors the backend's nullable-column convention.
    val note: String? = null,
    val checked: Boolean = false,
    val syncStatus: SyncStatus,
)

/** Same stand-in-until-synced scheme as [ListEntity.publicId]. */
val ListItemEntity.publicId: String
    get() = serverId ?: localIdStandIn(id)
