package ch.mcfx.urs.data.local

import kotlinx.serialization.Serializable

/** What gets JSON-encoded into [OutboxMutationEntity.payloadJson] for a queued work-time entry creation. */
@Serializable
data class OutboxWorkTimeEntryPayload(
    val userId: String,
    val date: String,
    val workStart: String,
    val workEnd: String,
    val targetDailyHours: String = "",
    val paidBreak: Boolean = true,
    val breaks: List<OutboxWorkTimeBreakPayload> = emptyList(),
)

@Serializable
data class OutboxWorkTimeBreakPayload(
    val startTime: String,
    val endTime: String,
)

/**
 * What gets JSON-encoded for a queued update to an entry that's already
 * confirmed by the backend — [serverId] identifies it directly, so replay
 * never needs to look up a local row's current server id.
 */
@Serializable
data class OutboxWorkTimeEntryUpdatePayload(
    val serverId: String,
    val userId: String,
    val date: String,
    val workStart: String,
    val workEnd: String,
    val targetDailyHours: String = "",
    val paidBreak: Boolean = true,
    val breaks: List<OutboxWorkTimeBreakPayload> = emptyList(),
)

/**
 * What gets JSON-encoded for a queued delete. Carries [serverId] directly
 * rather than a local row reference, since the local row is removed
 * immediately (offline-first) and won't exist any more by replay time.
 */
@Serializable
data class OutboxWorkTimeEntryDeletePayload(val serverId: String)
