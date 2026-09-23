package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import ch.mcfx.urs.data.AssetCategory

/**
 * Local, always-available mirror of a personal-possession asset
 * (mcfx-urs/urs-android#89) — same offline-first shape as [KanbanCardEntity].
 * [tags] is stored directly on this row (via a `List<String>` [Converters]
 * TypeConverter) rather than through a separate local cache table like
 * [TagEntity]: Assets has its own per-user tag pool with no dedicated
 * autocomplete endpoint to keep in sync, so there's nothing a separate table
 * would buy here.
 */
@Entity(tableName = "asset")
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Null until this row has been confirmed by the backend — the real asset_id.
    val serverId: String? = null,
    // Links back to the OutboxMutationEntity row still driving this asset's
    // sync, null once synced.
    val outboxId: Long? = null,
    val name: String,
    val category: AssetCategory,
    val location: String = "",
    val status: String = STATUS_ACTIVE,
    val tags: List<String> = emptyList(),
    // Computed sum of this asset's components' prices — mirrors the
    // backend's own computed asset_total_value, refreshed alongside the
    // components list rather than recomputed client-side from a live join.
    val totalValue: String = "0.00",
    val syncStatus: SyncStatus,
) {
    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_SOLD = "sold"
        const val STATUS_DISPOSED = "disposed"
        const val STATUS_LOST = "lost"
    }
}

/** Same stand-in-until-synced scheme as [KanbanCardEntity.publicId]. */
val AssetEntity.publicId: String
    get() = serverId ?: localIdStandIn(id)

/** Reverses [publicId] — same shape as [localKanbanCardId]. */
fun localAssetId(value: String): Long? = parseLocalIdStandIn(value)
