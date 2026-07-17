package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local, always-available mirror of one entry on a shopping list — same
 * offline-first shape as [ListEntity]. [listId] holds the parent
 * [ListEntity.publicId] as it stood at creation time (a real backend id, or
 * a not-yet-synced stand-in) — a queued item create resolves it to the
 * list's real backend id at replay time (see `SyncManager`) and corrects
 * this column then. [catalogProductId] is always a real `catalog_product`
 * id — see [ch.mcfx.urs.data.local.OutboxListItemPayload]'s doc
 * comment for why that one never needs the same stand-in treatment.
 * `checked` is gone entirely — adding/removing an item from a list
 * is the only state transition now.
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
    val catalogProductId: String,
    // Null = no note — mirrors the backend's nullable-column convention.
    val note: String? = null,
    val syncStatus: SyncStatus,
)

/** Same stand-in-until-synced scheme as [ListEntity.publicId]. */
val ListItemEntity.publicId: String
    get() = serverId ?: localIdStandIn(id)
