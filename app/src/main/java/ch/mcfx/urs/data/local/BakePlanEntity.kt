package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local, always-available mirror of a baking plan — same
 * offline-first shape as [ListEntity]. [status] is `"active"`/`"completed"`/
 * `"cancelled"`, mirroring the backend's plain-string convention rather
 * than a Room enum, so no extra type converter is needed to round-trip it
 * through the outbox JSON payloads unchanged.
 */
@Entity(tableName = "bake_plan")
data class BakePlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Null until this row has been confirmed by the backend — the real bake_plan_id.
    val serverId: String? = null,
    // Links back to the OutboxMutationEntity row still driving this plan's
    // sync, null once synced.
    val outboxId: Long? = null,
    val templateKey: String,
    val anchorAtMillis: Long,
    val status: String,
    val completedAtMillis: Long? = null,
    val syncStatus: SyncStatus,
)

/** Same stand-in-until-synced scheme as [ListEntity.publicId]. */
val BakePlanEntity.publicId: String
    get() = serverId ?: localIdStandIn(id)

/** Reverses [publicId] — same shape as [localListId]. */
fun localBakePlanId(value: String): Long? = parseLocalIdStandIn(value)
