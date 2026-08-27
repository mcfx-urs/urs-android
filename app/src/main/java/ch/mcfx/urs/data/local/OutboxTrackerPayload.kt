package ch.mcfx.urs.data.local

import kotlinx.serialization.Serializable

@Serializable
data class OutboxTrackerTypeCreatePayload(
    val name: String,
    val color: String,
    val icon: String,
    val calendar: String? = null,
    val expectedIntervalDays: Int? = null,
)

// serverId identifies the target directly (rename/recolour only happens
// once the type has synced — the pending-create is rewritten in place
// otherwise, same shape as OutboxListUpdatePayload).
@Serializable
data class OutboxTrackerTypeUpdatePayload(
    val serverId: String,
    val name: String,
    val color: String,
    val icon: String,
    val calendar: String? = null,
    val expectedIntervalDays: Int? = null,
)

@Serializable
data class OutboxTrackerTypeArchivePayload(
    val serverId: String,
)

// trackerTypeId is the parent type's publicId — a real id or a stand-in
// resolved at replay time (same as OutboxListItemPayload.listId).
@Serializable
data class OutboxTrackerEventCreatePayload(
    val trackerTypeId: String,
    val occurredOn: String,
    val occurredAt: String? = null,
    val note: String? = null,
    val source: String = "manual",
)

@Serializable
data class OutboxTrackerEventUpdatePayload(
    val serverId: String,
    val trackerTypeId: String,
    val occurredOn: String,
    val occurredAt: String? = null,
    val note: String? = null,
)

// Delete removes the local row immediately (offline-first), so the serverId
// is captured eagerly here — same as OutboxListItemDeletePayload.
@Serializable
data class OutboxTrackerEventDeletePayload(
    val serverId: String,
)
