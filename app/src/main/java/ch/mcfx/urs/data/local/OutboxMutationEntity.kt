package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class OutboxStatus { PENDING, SYNCING, FAILED }

/**
 * One queued, not-yet-confirmed write. [payloadJson] is a
 * kotlinx.serialization-encoded payload matching [type] — [OutboxFillPayload]
 * for [TYPE_CREATE_FILL], [OutboxWorkTimeEntryPayload] for
 * [TYPE_CREATE_WORK_TIME_ENTRY] (the backend resolves ad-hoc station
 * creation / daily-total computation server-side as part of that same
 * request in both cases, so one row per submission is enough).
 * [createdAt] drives strict FIFO replay order (see `SyncManager`), not
 * wall-clock display.
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
        const val TYPE_CREATE_WORK_TIME_ENTRY = "CREATE_WORK_TIME_ENTRY"
    }
}
