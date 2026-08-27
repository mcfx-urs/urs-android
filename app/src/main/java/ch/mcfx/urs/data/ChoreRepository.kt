package ch.mcfx.urs.data

import ch.mcfx.urs.auth.AuthTokenStore
import ch.mcfx.urs.data.local.OutboxDao
import ch.mcfx.urs.data.local.OutboxMutationEntity
import ch.mcfx.urs.data.local.OutboxTrackerEventCreatePayload
import ch.mcfx.urs.data.local.OutboxTrackerEventDeletePayload
import ch.mcfx.urs.data.local.OutboxTrackerEventUpdatePayload
import ch.mcfx.urs.data.local.OutboxTrackerTypeArchivePayload
import ch.mcfx.urs.data.local.OutboxTrackerTypeCreatePayload
import ch.mcfx.urs.data.local.OutboxTrackerTypeUpdatePayload
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.data.local.TrackerEventDao
import ch.mcfx.urs.data.local.TrackerEventEntity
import ch.mcfx.urs.data.local.TrackerTypeDao
import ch.mcfx.urs.data.local.TrackerTypeEntity
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.data.remote.TrackerEventDto
import ch.mcfx.urs.data.remote.TrackerTypeDto
import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.data.sync.SyncManager
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Offline-first write path for the "Chores and Stuff" tracker (GitHub issue
 * #27) — same shape as [ShoppingListRepository]: a type is the parent, an
 * event its child, and an event queued while its type is still offline
 * carries the type's stand-in id until replay resolves it. Types and events
 * are strictly per-user, same privacy bar as notes.
 */
class ChoreRepository(
    private val api: UrsApi,
    private val trackerTypeDao: TrackerTypeDao,
    private val trackerEventDao: TrackerEventDao,
    private val outboxDao: OutboxDao,
    private val syncManager: SyncManager,
    private val tokenStore: AuthTokenStore,
    private val applicationScope: CoroutineScope,
    private val json: Json,
) {

    fun observeTypes(): Flow<List<TrackerTypeEntity>> = trackerTypeDao.observeForUser(currentUserId())

    fun observeEvents(): Flow<List<TrackerEventEntity>> = trackerEventDao.observeForUser(currentUserId())

    suspend fun getType(localId: Long): TrackerTypeEntity? = trackerTypeDao.getById(localId)

    suspend fun getEvent(localId: Long): TrackerEventEntity? = trackerEventDao.getById(localId)

    // --- Types ---

    suspend fun createType(name: String, color: String, icon: String) {
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_TRACKER_TYPE,
                payloadJson = json.encodeToString(OutboxTrackerTypeCreatePayload(name = name, color = color, icon = icon)),
                createdAt = System.currentTimeMillis(),
            ),
        )
        trackerTypeDao.upsert(
            TrackerTypeEntity(
                outboxId = outboxId,
                userId = currentUserId(),
                name = name,
                color = color,
                icon = icon,
                syncStatus = SyncStatus.PENDING,
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    suspend fun updateType(localId: Long, name: String, color: String, icon: String) {
        val current = trackerTypeDao.getById(localId) ?: return

        val outboxId = if (current.serverId == null) {
            current.outboxId?.let {
                outboxDao.updatePayload(it, json.encodeToString(OutboxTrackerTypeCreatePayload(name = name, color = color, icon = icon)))
            }
            current.outboxId
        } else {
            current.outboxId?.let { outboxDao.delete(it) }
            outboxDao.insert(
                OutboxMutationEntity(
                    type = OutboxMutationEntity.TYPE_UPDATE_TRACKER_TYPE,
                    payloadJson = json.encodeToString(
                        OutboxTrackerTypeUpdatePayload(serverId = current.serverId, name = name, color = color, icon = icon),
                    ),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
        trackerTypeDao.updateFields(localId, name, color, icon, SyncStatus.PENDING, outboxId)
        applicationScope.launch { syncManager.syncNow() }
    }

    /** Soft-archive: hide from pickers, keep the type and its events for history. */
    suspend fun archiveType(localId: Long) {
        val current = trackerTypeDao.getById(localId) ?: return
        val serverId = current.serverId

        if (serverId == null) {
            // Never synced — just drop it and its still-local events.
            current.outboxId?.let { outboxDao.delete(it) }
            trackerEventDao.deleteByTypeId(current.publicId)
            trackerTypeDao.delete(localId)
            return
        }

        current.outboxId?.let { outboxDao.delete(it) }
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_ARCHIVE_TRACKER_TYPE,
                payloadJson = json.encodeToString(OutboxTrackerTypeArchivePayload(serverId = serverId)),
                createdAt = System.currentTimeMillis(),
            ),
        )
        trackerTypeDao.updateArchived(localId, System.currentTimeMillis(), SyncStatus.PENDING, outboxId)
        applicationScope.launch { syncManager.syncNow() }
    }

    // --- Events ---

    suspend fun logEvent(typeId: String, occurredOn: String, occurredAt: String?, note: String?) {
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_TRACKER_EVENT,
                payloadJson = json.encodeToString(
                    OutboxTrackerEventCreatePayload(trackerTypeId = typeId, occurredOn = occurredOn, occurredAt = occurredAt, note = note),
                ),
                createdAt = System.currentTimeMillis(),
            ),
        )
        trackerEventDao.upsert(
            TrackerEventEntity(
                outboxId = outboxId,
                userId = currentUserId(),
                trackerTypeId = typeId,
                occurredOn = occurredOn,
                occurredAt = occurredAt,
                note = note,
                syncStatus = SyncStatus.PENDING,
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    suspend fun updateEvent(localId: Long, typeId: String, occurredOn: String, occurredAt: String?, note: String?) {
        val current = trackerEventDao.getById(localId) ?: return

        val outboxId = if (current.serverId == null) {
            current.outboxId?.let {
                outboxDao.updatePayload(
                    it,
                    json.encodeToString(
                        OutboxTrackerEventCreatePayload(trackerTypeId = typeId, occurredOn = occurredOn, occurredAt = occurredAt, note = note),
                    ),
                )
            }
            current.outboxId
        } else {
            current.outboxId?.let { outboxDao.delete(it) }
            outboxDao.insert(
                OutboxMutationEntity(
                    type = OutboxMutationEntity.TYPE_UPDATE_TRACKER_EVENT,
                    payloadJson = json.encodeToString(
                        OutboxTrackerEventUpdatePayload(
                            serverId = current.serverId,
                            trackerTypeId = typeId,
                            occurredOn = occurredOn,
                            occurredAt = occurredAt,
                            note = note,
                        ),
                    ),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
        trackerEventDao.updateFields(localId, typeId, occurredOn, occurredAt, note, SyncStatus.PENDING, outboxId)
        applicationScope.launch { syncManager.syncNow() }
    }

    suspend fun deleteEvent(localId: Long) {
        val current = trackerEventDao.getById(localId) ?: return
        current.outboxId?.let { outboxDao.delete(it) }
        trackerEventDao.delete(localId)

        val serverId = current.serverId ?: return
        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_DELETE_TRACKER_EVENT,
                payloadJson = json.encodeToString(OutboxTrackerEventDeletePayload(serverId = serverId)),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Drains the outbox first (same reasoning as
     * [ShoppingListRepository.refreshFromBackend]), then pulls types before
     * events since an event references its type. Best-effort — a failure
     * leaves the cached data in place.
     */
    suspend fun refreshFromBackend() {
        syncManager.syncNow()
        val userId = currentUserId()
        try {
            val types = emptyAsNull { api.getTrackerTypes() }
            trackerTypeDao.upsertFromServer(userId, types.map { it.toEntity(userId) })
            val events = emptyAsNull { api.getTrackerEvents() }
            trackerEventDao.upsertFromServer(userId, events.map { it.toEntity(userId) })
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort only — see doc comment.
        }
    }

    private fun currentUserId(): String = tokenStore.currentUserId.orEmpty()

    // The backend encodes empty result sets as JSON `null` in some cases —
    // same guard as ShoppingListRepository.emptyAsNull.
    private suspend fun <T> emptyAsNull(call: suspend () -> List<T>): List<T> =
        try {
            call()
        } catch (_: SerializationException) {
            emptyList()
        }
}

// The backend serializes DATETIME columns as "yyyy-MM-dd HH:mm:ss".
private val trackerArchivedAtFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun String.toArchivedMillisOrNull(): Long? =
    runCatching {
        LocalDateTime.parse(this, trackerArchivedAtFormat).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()

private fun TrackerTypeDto.toEntity(userId: String) = TrackerTypeEntity(
    serverId = id,
    outboxId = null,
    userId = userId,
    name = name,
    color = color,
    icon = icon,
    archivedAtMillis = archivedAt.ifBlank { null }?.toArchivedMillisOrNull(),
    syncStatus = SyncStatus.SYNCED,
)

private fun TrackerEventDto.toEntity(userId: String) = TrackerEventEntity(
    serverId = id,
    outboxId = null,
    userId = userId,
    trackerTypeId = trackerTypeId,
    occurredOn = occurredOn,
    occurredAt = occurredAt.ifBlank { null },
    note = note.ifBlank { null },
    source = source.ifBlank { "manual" },
    syncStatus = SyncStatus.SYNCED,
)
