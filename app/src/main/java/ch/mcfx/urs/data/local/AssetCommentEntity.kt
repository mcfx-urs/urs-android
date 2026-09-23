package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One free-text comment on an [AssetEntity] — same shape as
 * [AssetComponentEntity], against the backend's independent
 * `POST/PUT/DELETE /asset-comment` endpoints.
 */
@Entity(tableName = "asset_comment")
data class AssetCommentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Null until this row has been confirmed by the backend — the real
    // asset_comment_id.
    val serverId: String? = null,
    // Links back to the OutboxMutationEntity row still driving this
    // comment's sync, null once synced.
    val outboxId: Long? = null,
    val assetId: String,
    val text: String,
    val date: String,
    val syncStatus: SyncStatus,
)

/** Same stand-in-until-synced scheme as [AssetComponentEntity.publicId]. */
val AssetCommentEntity.publicId: String
    get() = serverId ?: localIdStandIn(id)

/** Reverses [publicId] — same shape as [localAssetComponentId]. */
fun localAssetCommentId(value: String): Long? = parseLocalIdStandIn(value)
