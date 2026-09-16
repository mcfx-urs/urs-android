package ch.mcfx.urs.data

import android.content.Context
import ch.mcfx.urs.auth.AuthTokenStore
import ch.mcfx.urs.beer.BeerStats
import ch.mcfx.urs.data.local.NoteDao
import ch.mcfx.urs.data.local.NoteEntity
import ch.mcfx.urs.data.local.NoteTagDao
import ch.mcfx.urs.data.local.NoteTagEntity
import ch.mcfx.urs.data.local.NoteWithTags
import ch.mcfx.urs.data.local.OutboxDao
import ch.mcfx.urs.data.local.OutboxMutationEntity
import ch.mcfx.urs.data.local.OutboxNoteCreatePayload
import ch.mcfx.urs.data.local.OutboxNoteDeletePayload
import ch.mcfx.urs.data.local.OutboxNoteStatusPayload
import ch.mcfx.urs.data.local.OutboxNoteUpdatePayload
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.data.local.localIdStandIn
import ch.mcfx.urs.data.local.localNoteId
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.data.remote.NoteDto
import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.data.sync.SyncManager
import ch.mcfx.urs.notifications.NoteAlarmScheduler
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Offline-first write path for notes (GitHub issue #10) — same shape as
 * [BakingRepository], with the same device-alarm side effect (one exact
 * reminder alarm per note, [NoteAlarmScheduler]) but simpler: a note has no
 * child rows of its own kind and no parent/child sync ordering to worry
 * about, just a flat list of tags riding along in its own payload.
 */
class NoteRepository(
    private val context: Context,
    private val api: UrsApi,
    private val noteDao: NoteDao,
    private val noteTagDao: NoteTagDao,
    private val outboxDao: OutboxDao,
    private val syncManager: SyncManager,
    private val tokenStore: AuthTokenStore,
    private val applicationScope: CoroutineScope,
    private val json: Json,
) {

    fun observeActive(): Flow<List<NoteWithTags>> = noteDao.observeWithTagsByStatus(currentUserId(), STATUS_ACTIVE)

    fun observeCompleted(): Flow<List<NoteWithTags>> = noteDao.observeWithTagsByStatus(currentUserId(), STATUS_COMPLETED)

    fun observeNote(localId: Long): Flow<NoteWithTags?> = noteDao.observeWithTagsById(localId)

    suspend fun getNote(localId: Long): NoteEntity? = noteDao.getById(localId)

    /** Resolves a note's [publicId] (a real serverId, or a not-yet-synced stand-in) back to its stable local row id — same shape as `BakingRepository.resolveLocalPlanId`. */
    suspend fun resolveLocalNoteId(noteId: String): Long? = localNoteId(noteId) ?: noteDao.findLocalIdByServerId(noteId)

    suspend fun getTags(localNoteId: Long): List<NoteTagEntity> = noteTagDao.getByNoteId(localNoteId)

    suspend fun suggestTags(query: String): List<String> = noteTagDao.suggestTagNames(currentUserId(), query)

    suspend fun createNote(title: String, content: String, reminderAtMillis: Long?, tags: List<String>) {
        val payload = OutboxNoteCreatePayload(title = title, content = content, reminderAtMillis = reminderAtMillis, tags = tags)
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_NOTE,
                payloadJson = json.encodeToString(payload),
                createdAt = System.currentTimeMillis(),
            ),
        )
        val localId = noteDao.upsert(
            NoteEntity(
                outboxId = outboxId,
                userId = currentUserId(),
                title = title,
                content = content,
                reminderAtMillis = reminderAtMillis,
                status = STATUS_ACTIVE,
                syncStatus = SyncStatus.PENDING,
            ),
        )
        noteTagDao.insertAll(tags.map { NoteTagEntity(noteId = localId, tagName = it) })
        reminderAtMillis?.let {
            NoteAlarmScheduler.scheduleNoteAlarm(context, alarmIdFor(localId), it, localIdStandIn(localId), title)
        }
        applicationScope.launch { syncManager.syncNow() }
    }

    suspend fun updateNote(localId: Long, title: String, content: String, reminderAtMillis: Long?, tags: List<String>) {
        val current = noteDao.getById(localId) ?: return
        noteDao.updateFields(localId, title, content, reminderAtMillis, SyncStatus.PENDING)
        noteTagDao.replaceTags(localId, tags.map { NoteTagEntity(noteId = localId, tagName = it) })

        NoteAlarmScheduler.cancel(context, alarmIdFor(localId))
        if (reminderAtMillis != null && current.status == STATUS_ACTIVE) {
            NoteAlarmScheduler.scheduleNoteAlarm(context, alarmIdFor(localId), reminderAtMillis, current.publicId, title)
        }

        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_UPDATE_NOTE,
                payloadJson = json.encodeToString(
                    OutboxNoteUpdatePayload(localNoteId = localId, title = title, content = content, reminderAtMillis = reminderAtMillis, tags = tags),
                ),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Clears just the reminder, leaving title/content/tags/status untouched
     * — routes through [updateNote] so the change is bundled into the same
     * kind of outbox update a manual edit-and-save already produces (a bare
     * local DAO write would leave the backend's copy of `reminderAtMillis`
     * unchanged, which the next sync would pull back down). Used by the
     * note-detail completion path in [setStatus] and by the reminder
     * notification's "Done" action ([ch.mcfx.urs.notifications.NoteReminderDoneReceiver]).
     */
    suspend fun clearReminder(localId: Long) {
        val current = noteDao.getById(localId) ?: return
        if (current.reminderAtMillis == null) return
        val tags = noteTagDao.getByNoteId(localId).map { it.tagName }
        updateNote(localId, current.title, current.content, reminderAtMillis = null, tags = tags)
    }

    /**
     * Toggles between active and completed — reopening is explicitly
     * supported, not final (GitHub issue #10's own open question, resolved
     * "yes"). A reminder on a reopened note is re-armed exactly like
     * [ch.mcfx.urs.data.BakingRepository.rearmPendingStepAlarms] handles a
     * trigger time already in the past: still handed to AlarmManager rather
     * than special-cased, so it fires almost immediately instead of being
     * silently suppressed. Completing a note clears its reminder outright
     * (GitHub issue #52) — nothing left to re-arm if it's ever reopened.
     */
    suspend fun setStatus(localId: Long, status: String) {
        if (status == STATUS_COMPLETED) clearReminder(localId)
        val current = noteDao.getById(localId) ?: return
        val completedAtMillis = if (status == STATUS_COMPLETED) System.currentTimeMillis() else null
        noteDao.updateStatus(localId, status, completedAtMillis, SyncStatus.PENDING)

        if (status == STATUS_COMPLETED) {
            NoteAlarmScheduler.cancel(context, alarmIdFor(localId))
        } else {
            current.reminderAtMillis?.let {
                NoteAlarmScheduler.scheduleNoteAlarm(context, alarmIdFor(localId), it, current.publicId, current.title)
            }
        }

        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_UPDATE_NOTE_STATUS,
                payloadJson = json.encodeToString(OutboxNoteStatusPayload(localNoteId = localId, status = status)),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    suspend fun deleteNote(localId: Long) {
        val current = noteDao.getById(localId) ?: return
        NoteAlarmScheduler.cancel(context, alarmIdFor(localId))
        current.outboxId?.let { outboxDao.delete(it) }
        noteTagDao.deleteByNoteId(localId)
        noteDao.delete(localId)

        val serverId = current.serverId ?: return
        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_DELETE_NOTE,
                payloadJson = json.encodeToString(OutboxNoteDeletePayload(serverId = serverId)),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Re-arms every active note's reminder — called after a reboot (exact
     * alarms don't survive it) from
     * [ch.mcfx.urs.notifications.BootCompletedReceiver].
     */
    suspend fun rearmPendingReminders() {
        noteDao.getActiveWithReminder(currentUserId()).forEach { note ->
            note.reminderAtMillis?.let {
                NoteAlarmScheduler.scheduleNoteAlarm(context, alarmIdFor(note.id), it, note.publicId, note.title)
            }
        }
    }

    /**
     * Pulls the authenticated user's notes down into Room — the outbox
     * above only ever pushes local changes up, same gap
     * [LocationHistoryRepository.refreshFromBackend] closed for location
     * history. Without this, a note created on one device/install never
     * appears on another.
     */
    /**
     * [Mutex]-guarded, same shape as [SyncManager.syncNow] and
     * [PullCoordinator.pullAll] — [NotesViewModel][ch.mcfx.urs.notes.NotesViewModel]'s
     * own refresh-on-entry and [PullCoordinator]'s app-wide pull can both call
     * this independently, and without serializing them, two concurrent passes
     * interleaved their `note`/`note_tag` upserts (GitHub issue #71).
     */
    private val refreshMutex = Mutex()

    /** @return `true` if the refresh completed cleanly (see [PullCoordinator]). */
    suspend fun refreshFromBackend(): Boolean = refreshMutex.withLock {
        try {
            api.getNotes().forEach { dto ->
                val localId = noteDao.upsertFromServer(dto.toEntity(currentUserId()))
                if (localId >= 0) {
                    noteTagDao.replaceTags(localId, dto.tags.map { NoteTagEntity(noteId = localId, tagName = it) })
                }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best-effort only, same shape as ServiceRepository.refreshFromBackend
            // — stale cached data beats an empty or error screen.
            android.util.Log.w("NoteRepository", "refreshFromBackend failed", e)
            false
        }
    }

    private fun currentUserId(): String = tokenStore.currentUserId.orEmpty()

    // Reserved id range — see BakingRepository.BAKING_STEP_ALARM_ID_BASE's
    // doc comment for the sibling ranges this must not collide with.
    private fun alarmIdFor(localId: Long) = NOTE_REMINDER_ID_BASE + (localId.toInt() and 0xFFFF)

    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_COMPLETED = "completed"
        private const val NOTE_REMINDER_ID_BASE = 300_000
    }
}

private fun String.toMillisOrNull(): Long? =
    BeerStats.parseDateTime(this)?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

private fun NoteDto.toEntity(userId: String) = NoteEntity(
    serverId = id,
    outboxId = null,
    userId = userId,
    title = title,
    content = content,
    reminderAtMillis = reminderAt.toMillisOrNull(),
    status = status,
    completedAtMillis = completedAt.toMillisOrNull(),
    syncStatus = SyncStatus.SYNCED,
)
