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
    val breaks: List<OutboxWorkTimeBreakPayload> = emptyList(),
)

@Serializable
data class OutboxWorkTimeBreakPayload(
    val startTime: String,
    val endTime: String,
)
