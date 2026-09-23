package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One purchased component of an [AssetEntity] — its own entity with full
 * create/update/delete outbox coverage, same reasoning as
 * [KanbanChecklistItemEntity]: the backend exposes independent
 * `POST/PUT/DELETE /asset-component` endpoints. [assetId] holds the parent
 * [AssetEntity.publicId] as it stood at creation time, resolved to the real
 * backend id at replay time same as [KanbanChecklistItemEntity.cardId].
 */
@Entity(tableName = "asset_component")
data class AssetComponentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Null until this row has been confirmed by the backend — the real
    // asset_component_id.
    val serverId: String? = null,
    // Links back to the OutboxMutationEntity row still driving this
    // component's sync, null once synced.
    val outboxId: Long? = null,
    val assetId: String,
    val description: String,
    val manufacturer: String = "",
    val price: String,
    val purchaseDate: String,
    val dealer: String = "",
    val syncStatus: SyncStatus,
)

/** Same stand-in-until-synced scheme as [KanbanChecklistItemEntity.publicId]. */
val AssetComponentEntity.publicId: String
    get() = serverId ?: localIdStandIn(id)

/** Reverses [publicId] — same shape as [localKanbanChecklistItemId]. */
fun localAssetComponentId(value: String): Long? = parseLocalIdStandIn(value)
