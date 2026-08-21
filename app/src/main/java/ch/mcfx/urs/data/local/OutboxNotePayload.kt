package ch.mcfx.urs.data.local

import kotlinx.serialization.Serializable

@Serializable
data class OutboxNoteCreatePayload(
    val title: String,
    val content: String,
    val reminderAtMillis: Long? = null,
    val tags: List<String> = emptyList(),
)

// Identifies its target by the note's stable local row id, not its
// (possibly still-null) serverId — the user can edit a note before its own
// create mutation has synced, same "resolved at replay time, retry if the
// parent isn't synced yet" shape as OutboxBakePlanStepUpdatePayload.
@Serializable
data class OutboxNoteUpdatePayload(
    val localNoteId: Long,
    val title: String,
    val content: String,
    val reminderAtMillis: Long? = null,
    val tags: List<String> = emptyList(),
)

/** Same not-yet-synced-parent handling as [OutboxNoteUpdatePayload] — resolved by local row id at replay time. */
@Serializable
data class OutboxNoteStatusPayload(
    val localNoteId: Long,
    val status: String,
)

// Unlike update/status above, a delete removes the local row immediately
// (offline-first delete), so there's nothing left to resolve a serverId
// from at replay time — it's captured eagerly at queue time instead, same
// as OutboxInventoryDeletePayload. A note deleted before ever syncing simply
// never queues one of these at all (see NoteRepository.deleteNote).
@Serializable
data class OutboxNoteDeletePayload(
    val serverId: String,
)
