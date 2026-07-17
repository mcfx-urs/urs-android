package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local, always-available mirror of a shopping list — same offline-first
 * shape as [InventoryEntity] (stable local [id], nullable [serverId]/
 * [outboxId] until confirmed, [syncStatus]), including full create/rename/
 * delete outbox coverage (see `ShoppingListRepository.renameList`/
 * `deleteList`), mirroring [ch.mcfx.urs.data.local.WorkTimeEntryEntity]'s
 * full create/update/delete outbox shape — this was true even before
 *  gave inventory the identical shape (`InventoryEntity` replacing
 * the old create-only `InventoryCategoryEntity`).
 */
@Entity(tableName = "list")
data class ListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Null until this row has been confirmed by the backend — the real list_id.
    val serverId: String? = null,
    // Links back to the OutboxMutationEntity row still driving this list's
    // sync, null once synced.
    val outboxId: Long? = null,
    val name: String,
    val syncStatus: SyncStatus,
)

/**
 * The id the UI/outbox payloads address this list by: the real backend id
 * once known, otherwise a stand-in derived from the stable local [id] — see
 * [localListId] for the reverse lookup a queued list-item create uses to
 * resolve its still-pending parent list at replay time.
 */
val ListEntity.publicId: String
    get() = serverId ?: localIdStandIn(id)

/** Reverses [publicId] — same shape as [localInventoryId]. */
fun localListId(value: String): Long? = parseLocalIdStandIn(value)
