package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class OutboxStatus { PENDING, SYNCING, FAILED }

/**
 * One queued, not-yet-confirmed write. [payloadJson] is a
 * kotlinx.serialization-encoded [OutboxFillPayload] for every mutation type
 * today (only [TYPE_CREATE_FILL] exists so far — the backend already
 * resolves ad-hoc station creation server-side as part of that same
 * request, see [OutboxFillPayload], so one row per submitted fill is
 * enough). [createdAt] drives strict FIFO replay order (see
 * `SyncManager`), not wall-clock display.
 */
@Entity(tableName = "outbox_mutation")
data class OutboxMutationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val payloadJson: String,
    val createdAt: Long,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val status: OutboxStatus = OutboxStatus.PENDING,
) {
    companion object {
        const val TYPE_CREATE_FILL = "CREATE_FILL"
    }
}
