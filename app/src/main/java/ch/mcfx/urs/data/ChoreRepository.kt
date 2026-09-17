package ch.mcfx.urs.data

import ch.mcfx.urs.auth.AuthTokenStore
import ch.mcfx.urs.data.local.OutboxDao
import ch.mcfx.urs.data.local.OutboxMutationEntity
import ch.mcfx.urs.data.local.OutboxTrackerDomainCreatePayload
import ch.mcfx.urs.data.local.OutboxTrackerDomainDeletePayload
import ch.mcfx.urs.data.local.OutboxTrackerDomainUpdatePayload
import ch.mcfx.urs.data.local.OutboxTrackerEventCreatePayload
import ch.mcfx.urs.data.local.OutboxTrackerEventDeletePayload
import ch.mcfx.urs.data.local.OutboxTrackerEventUpdatePayload
import ch.mcfx.urs.data.local.OutboxTrackerTypeArchivePayload
import ch.mcfx.urs.data.local.OutboxTrackerTypeCreatePayload
import ch.mcfx.urs.data.local.OutboxTrackerTypeReactivatePayload
import ch.mcfx.urs.data.local.OutboxTrackerTypeUpdatePayload
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.data.local.TrackerDomainDao
import ch.mcfx.urs.data.local.TrackerDomainEntity
import ch.mcfx.urs.data.local.TrackerEventDao
import ch.mcfx.urs.data.local.TrackerEventEntity
import ch.mcfx.urs.data.local.TrackerTypeDao
import ch.mcfx.urs.data.local.TrackerTypeEntity
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.data.remote.TrackerDomainDto
import ch.mcfx.urs.data.remote.TrackerDomainPayload
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
import kotlinx.coroutines.flow.first
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
    private val trackerDomainDao: TrackerDomainDao,
    private val outboxDao: OutboxDao,
    private val syncManager: SyncManager,
    private val tokenStore: AuthTokenStore,
    private val applicationScope: CoroutineScope,
    private val json: Json,
) {

    fun observeTypes(): Flow<List<TrackerTypeEntity>> = trackerTypeDao.observeForUser(currentUserId())

    fun observeEvents(): Flow<List<TrackerEventEntity>> = trackerEventDao.observeForUser(currentUserId())

    fun observeDomains(): Flow<List<TrackerDomainEntity>> = trackerDomainDao.observeForUser(currentUserId())

    suspend fun getType(localId: Long): TrackerTypeEntity? = trackerTypeDao.getById(localId)

    suspend fun getEvent(localId: Long): TrackerEventEntity? = trackerEventDao.getById(localId)

    suspend fun getDomain(localId: Long): TrackerDomainEntity? = trackerDomainDao.getById(localId)

    // --- Journal domains (GitHub issue #83) ---

    suspend fun createDomain(name: String, color: String, icon: String) {
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_TRACKER_DOMAIN,
                payloadJson = json.encodeToString(OutboxTrackerDomainCreatePayload(name = name, color = color, icon = icon)),
                createdAt = System.currentTimeMillis(),
            ),
        )
        trackerDomainDao.upsert(
            TrackerDomainEntity(
                outboxId = outboxId,
                userId = currentUserId(),
                name = name,
                color = color,
                icon = icon,
                position = trackerDomainDao.nextPosition(currentUserId()),
                syncStatus = SyncStatus.PENDING,
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    suspend fun updateDomain(localId: Long, name: String, color: String, icon: String) {
        val current = trackerDomainDao.getById(localId) ?: return

        val outboxId = if (current.serverId == null) {
            current.outboxId?.let {
                outboxDao.updatePayload(it, json.encodeToString(OutboxTrackerDomainCreatePayload(name = name, color = color, icon = icon)))
            }
            current.outboxId
        } else {
            current.outboxId?.let { outboxDao.delete(it) }
            outboxDao.insert(
                OutboxMutationEntity(
                    type = OutboxMutationEntity.TYPE_UPDATE_TRACKER_DOMAIN,
                    payloadJson = json.encodeToString(
                        OutboxTrackerDomainUpdatePayload(serverId = current.serverId, name = name, color = color, icon = icon),
                    ),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
        trackerDomainDao.updateFields(localId, name, color, icon, SyncStatus.PENDING, outboxId)
        applicationScope.launch { syncManager.syncNow() }
    }

    suspend fun deleteDomain(localId: Long) {
        val current = trackerDomainDao.getById(localId) ?: return
        current.outboxId?.let { outboxDao.delete(it) }
        trackerDomainDao.delete(localId)

        val serverId = current.serverId ?: return
        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_DELETE_TRACKER_DOMAIN,
                payloadJson = json.encodeToString(OutboxTrackerDomainDeletePayload(serverId = serverId)),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    // --- Types ---

    suspend fun createType(
        name: String,
        color: String,
        icon: String,
        calendar: String?,
        expectedIntervalDays: Int?,
        domainId: String? = null,
    ) {
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_TRACKER_TYPE,
                payloadJson = json.encodeToString(
                    OutboxTrackerTypeCreatePayload(
                        name = name,
                        color = color,
                        icon = icon,
                        calendar = calendar,
                        expectedIntervalDays = expectedIntervalDays,
                        domainId = domainId,
                    ),
                ),
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
                calendar = calendar,
                expectedIntervalDays = expectedIntervalDays,
                domainId = domainId,
                syncStatus = SyncStatus.PENDING,
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    suspend fun updateType(
        localId: Long,
        name: String,
        color: String,
        icon: String,
        calendar: String?,
        expectedIntervalDays: Int?,
        domainId: String? = null,
    ) {
        val current = trackerTypeDao.getById(localId) ?: return
        val effectiveDomainId = domainId ?: current.domainId

        val outboxId = if (current.serverId == null) {
            current.outboxId?.let {
                outboxDao.updatePayload(
                    it,
                    json.encodeToString(
                        OutboxTrackerTypeCreatePayload(
                            name = name,
                            color = color,
                            icon = icon,
                            calendar = calendar,
                            expectedIntervalDays = expectedIntervalDays,
                            domainId = effectiveDomainId,
                        ),
                    ),
                )
            }
            current.outboxId
        } else {
            current.outboxId?.let { outboxDao.delete(it) }
            outboxDao.insert(
                OutboxMutationEntity(
                    type = OutboxMutationEntity.TYPE_UPDATE_TRACKER_TYPE,
                    payloadJson = json.encodeToString(
                        OutboxTrackerTypeUpdatePayload(
                            serverId = current.serverId,
                            name = name,
                            color = color,
                            icon = icon,
                            calendar = calendar,
                            expectedIntervalDays = expectedIntervalDays,
                            domainId = effectiveDomainId,
                        ),
                    ),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
        trackerTypeDao.updateFields(localId, name, color, icon, calendar, expectedIntervalDays, effectiveDomainId, SyncStatus.PENDING, outboxId)
        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Stamps the given types as exported — locally right away, and
     * best-effort on the backend for the ones that have synced. A failed
     * server call is harmless: the next "only new" export re-includes a few
     * events, which a calendar app dedupes on their stable UID.
     */
    suspend fun markExported(localIds: List<Long>) {
        val now = System.currentTimeMillis()
        for (localId in localIds) {
            trackerTypeDao.updateLastExported(localId, now)
            val serverId = trackerTypeDao.getById(localId)?.serverId ?: continue
            try {
                api.markTrackerTypeExported(serverId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort — see doc comment.
            }
        }
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

    /** Undo a soft-archive — bring the type back into pickers (GitHub issue #37). */
    suspend fun reactivateType(localId: Long) {
        val current = trackerTypeDao.getById(localId) ?: return
        // An archived type is always one that synced (archiveType drops
        // never-synced ones outright), so serverId is present.
        val serverId = current.serverId ?: return

        current.outboxId?.let { outboxDao.delete(it) }
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_REACTIVATE_TRACKER_TYPE,
                payloadJson = json.encodeToString(OutboxTrackerTypeReactivatePayload(serverId = serverId)),
                createdAt = System.currentTimeMillis(),
            ),
        )
        trackerTypeDao.updateArchived(localId, null, SyncStatus.PENDING, outboxId)
        applicationScope.launch { syncManager.syncNow() }
    }

    // --- Events ---

    suspend fun logEvent(
        typeId: String,
        occurredOn: String,
        occurredAt: String?,
        note: String?,
        source: String = "manual",
        occurredOnEnd: String? = null,
        occurredAtEnd: String? = null,
    ) {
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_TRACKER_EVENT,
                payloadJson = json.encodeToString(
                    OutboxTrackerEventCreatePayload(
                        trackerTypeId = typeId,
                        occurredOn = occurredOn,
                        occurredOnEnd = occurredOnEnd,
                        occurredAt = occurredAt,
                        occurredAtEnd = occurredAtEnd,
                        note = note,
                        source = source,
                    ),
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
                occurredOnEnd = occurredOnEnd,
                occurredAt = occurredAt,
                occurredAtEnd = occurredAtEnd,
                note = note,
                source = source,
                syncStatus = SyncStatus.PENDING,
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    suspend fun updateEvent(
        localId: Long,
        typeId: String,
        occurredOn: String,
        occurredAt: String?,
        note: String?,
        occurredOnEnd: String? = null,
        occurredAtEnd: String? = null,
    ) {
        val current = trackerEventDao.getById(localId) ?: return

        val outboxId = if (current.serverId == null) {
            current.outboxId?.let {
                outboxDao.updatePayload(
                    it,
                    json.encodeToString(
                        OutboxTrackerEventCreatePayload(
                            trackerTypeId = typeId,
                            occurredOn = occurredOn,
                            occurredOnEnd = occurredOnEnd,
                            occurredAt = occurredAt,
                            occurredAtEnd = occurredAtEnd,
                            note = note,
                        ),
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
                            occurredOnEnd = occurredOnEnd,
                            occurredAt = occurredAt,
                            occurredAtEnd = occurredAtEnd,
                            note = note,
                        ),
                    ),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
        trackerEventDao.updateFields(localId, typeId, occurredOn, occurredOnEnd, occurredAt, occurredAtEnd, note, SyncStatus.PENDING, outboxId)
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
     * [ShoppingListRepository.refreshFromBackend]), then pulls domains, then
     * types (which reference a domain), then events (which reference a
     * type). Best-effort — a failure leaves the cached data in place.
     */
    /** @return `true` if the refresh completed cleanly (see [PullCoordinator]). */
    suspend fun refreshFromBackend(): Boolean {
        syncManager.syncNow()
        val userId = currentUserId()
        try {
            val domains = emptyAsNull { api.getTrackerDomains() }
            trackerDomainDao.upsertFromServer(userId, domains.map { it.toEntity(userId) })
            val types = emptyAsNull { api.getTrackerTypes() }
            trackerTypeDao.upsertFromServer(userId, types.map { it.toEntity(userId) })
            val events = emptyAsNull { api.getTrackerEvents() }
            trackerEventDao.upsertFromServer(userId, events.map { it.toEntity(userId) })
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best-effort only — see doc comment.
            android.util.Log.w("ChoreRepository", "refreshFromBackend failed", e)
            return false
        }
    }

    /**
     * The active (non-archived) chore types the watch menu is built from: a
     * best-effort [refreshFromBackend] first so a type created, renamed or
     * archived on another device shows up, then the local cache — which
     * still answers when offline. Each pair is (publicId, name); the
     * publicId is what [logEvent] and the watch relay's chore-event
     * endpoint expect.
     */
    suspend fun listActiveTypesForWatch(): List<Pair<String, String>> {
        refreshFromBackend()
        return observeTypes().first()
            .filter { it.archivedAtMillis == null }
            .sortedBy { it.name.lowercase() }
            .map { it.publicId to it.name }
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
    calendar = calendar.ifBlank { null },
    expectedIntervalDays = expectedIntervalDays.toIntOrNull(),
    archivedAtMillis = archivedAt.ifBlank { null }?.toArchivedMillisOrNull(),
    lastExportedAtMillis = lastExportedAt.ifBlank { null }?.toArchivedMillisOrNull(),
    domainId = domainId.ifBlank { null },
    syncStatus = SyncStatus.SYNCED,
)

private fun TrackerDomainDto.toEntity(userId: String) = TrackerDomainEntity(
    serverId = id,
    outboxId = null,
    userId = userId,
    name = name,
    color = color,
    icon = icon,
    position = position,
    syncStatus = SyncStatus.SYNCED,
)

private fun TrackerEventDto.toEntity(userId: String) = TrackerEventEntity(
    serverId = id,
    outboxId = null,
    userId = userId,
    trackerTypeId = trackerTypeId,
    occurredOn = occurredOn,
    occurredOnEnd = occurredOnEnd.ifBlank { null },
    occurredAt = occurredAt.ifBlank { null },
    occurredAtEnd = occurredAtEnd.ifBlank { null },
    note = note.ifBlank { null },
    source = source.ifBlank { "manual" },
    syncStatus = SyncStatus.SYNCED,
)
