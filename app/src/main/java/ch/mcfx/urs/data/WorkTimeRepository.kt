package ch.mcfx.urs.data

import ch.mcfx.urs.auth.AuthTokenStore
import ch.mcfx.urs.data.local.OutboxDao
import ch.mcfx.urs.data.local.OutboxMutationEntity
import ch.mcfx.urs.data.local.OutboxWorkTimeBreakPayload
import ch.mcfx.urs.data.local.OutboxWorkTimeEntryDeletePayload
import ch.mcfx.urs.data.local.OutboxWorkTimeEntryPayload
import ch.mcfx.urs.data.local.OutboxWorkTimeEntryUpdatePayload
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.data.local.WorkTimeBreakEntity
import ch.mcfx.urs.data.local.WorkTimeDao
import ch.mcfx.urs.data.local.WorkTimeEntryEntity
import ch.mcfx.urs.data.local.WorkTimeEntryWithBreaks
import ch.mcfx.urs.data.local.WorkTimeMonthOverrideDao
import ch.mcfx.urs.data.local.WorkTimeMonthOverrideEntity
import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.data.remote.WorkTimeBreakDto
import ch.mcfx.urs.data.remote.WorkTimeEntryDto
import ch.mcfx.urs.data.remote.WorkTimeMonthOverridePayload
import ch.mcfx.urs.data.sync.SyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// The work-time endpoints below still take an explicit user_id path segment
// on the backend — see the same note in UserRepository. The id comes from
// the logged-in session (AuthTokenStore.currentUserId) instead of the old
// hardcoded UserDefaults.DEFAULT_USER_ID placeholder.
class WorkTimeRepository(
    private val api: UrsApi,
    private val workTimeDao: WorkTimeDao,
    private val workTimeMonthOverrideDao: WorkTimeMonthOverrideDao,
    private val outboxDao: OutboxDao,
    private val syncManager: SyncManager,
    private val applicationScope: CoroutineScope,
    private val json: Json,
    private val tokenStore: AuthTokenStore,
) {

    fun observeEntries(): Flow<List<WorkTimeEntryWithBreaks>> = workTimeDao.observeAll()

    fun observeMonthOverrides(): Flow<List<WorkTimeMonthOverrideEntity>> = workTimeMonthOverrideDao.observeAll()

    /**
     * Offline-first write path, same shape as [FuelRepository.createFill]:
     * both writes below are local-only and instant, a background sync
     * attempt fires immediately afterwards but is never awaited here.
     */
    suspend fun createEntry(
        date: String,
        workStart: String,
        workEnd: String,
        targetDailyHours: String,
        paidBreak: Boolean,
        breaks: List<Pair<String, String>>,
    ) {
        val userId = tokenStore.currentUserId ?: return
        val payload = OutboxWorkTimeEntryPayload(
            userId = userId,
            date = date,
            workStart = workStart,
            workEnd = workEnd,
            targetDailyHours = targetDailyHours,
            paidBreak = paidBreak,
            breaks = breaks.map { (start, end) -> OutboxWorkTimeBreakPayload(startTime = start, endTime = end) },
        )

        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_WORK_TIME_ENTRY,
                payloadJson = json.encodeToString(payload),
                createdAt = System.currentTimeMillis(),
            ),
        )

        val entryId = workTimeDao.insertEntry(
            WorkTimeEntryEntity(
                outboxId = outboxId,
                date = date,
                workStart = workStart,
                workEnd = workEnd,
                targetDailyHours = targetDailyHours,
                paidBreak = paidBreak,
                syncStatus = SyncStatus.PENDING,
            ),
        )
        workTimeDao.insertBreaks(
            breaks.map { (start, end) -> WorkTimeBreakEntity(entryId = entryId, startTime = start, endTime = end) },
        )

        applicationScope.launch { syncManager.syncNow() }
    }

    suspend fun getEntry(id: Long): WorkTimeEntryWithBreaks? = workTimeDao.getWithBreaksById(id)

    /**
     * Offline-first edit path. An entry that hasn't reached the server yet
     * (no [WorkTimeEntryEntity.serverId]) has no business getting its own
     * `PUT` round-trip — its still-pending create mutation's payload is
     * rewritten in place instead, so it creates with the latest values
     * whenever it eventually syncs. An already-synced entry cancels
     * whatever mutation is still pending for it (e.g. an earlier edit that
     * hasn't replayed yet — only the latest edit should ever apply) and
     * queues a fresh update.
     */
    suspend fun updateEntry(
        localId: Long,
        date: String,
        workStart: String,
        workEnd: String,
        targetDailyHours: String,
        paidBreak: Boolean,
        breaks: List<Pair<String, String>>,
    ) {
        val current = workTimeDao.getById(localId) ?: return
        val userId = tokenStore.currentUserId ?: return
        val breakPayloads = breaks.map { (start, end) -> OutboxWorkTimeBreakPayload(startTime = start, endTime = end) }

        val outboxId = if (current.serverId == null) {
            val payload = OutboxWorkTimeEntryPayload(
                userId = userId,
                date = date,
                workStart = workStart,
                workEnd = workEnd,
                targetDailyHours = targetDailyHours,
                paidBreak = paidBreak,
                breaks = breakPayloads,
            )
            current.outboxId?.let { outboxDao.updatePayload(it, json.encodeToString(payload)) }
            current.outboxId
        } else {
            current.outboxId?.let { outboxDao.delete(it) }
            val payload = OutboxWorkTimeEntryUpdatePayload(
                serverId = current.serverId.toString(),
                userId = userId,
                date = date,
                workStart = workStart,
                workEnd = workEnd,
                targetDailyHours = targetDailyHours,
                paidBreak = paidBreak,
                breaks = breakPayloads,
            )
            outboxDao.insert(
                OutboxMutationEntity(
                    type = OutboxMutationEntity.TYPE_UPDATE_WORK_TIME_ENTRY,
                    payloadJson = json.encodeToString(payload),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }

        workTimeDao.updateFields(localId, date, workStart, workEnd, targetDailyHours, paidBreak, SyncStatus.PENDING, outboxId)
        workTimeDao.replaceBreaks(
            localId,
            breaks.map { (start, end) -> WorkTimeBreakEntity(entryId = localId, startTime = start, endTime = end) },
        )

        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Offline-first delete path. The local row is always removed
     * immediately; a server-side delete is only queued if the server ever
     * actually learned about this entry ([WorkTimeEntryEntity.serverId] set)
     * — otherwise there's nothing to reconcile remotely. Any mutation still
     * pending for this entry (a queued create or edit) is cancelled first,
     * since it would otherwise resurrect or edit a row the user just deleted.
     */
    suspend fun deleteEntry(localId: Long) {
        val current = workTimeDao.getById(localId) ?: return
        current.outboxId?.let { outboxDao.delete(it) }
        workTimeDao.deleteEntry(localId)

        val serverId = current.serverId ?: return
        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_DELETE_WORK_TIME_ENTRY,
                payloadJson = json.encodeToString(OutboxWorkTimeEntryDeletePayload(serverId = serverId.toString())),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Opportunistic backend refresh, same best-effort shape as
     * [FuelRepository.refreshFromBackend] — never blocks the UI or surfaces
     * an error, stale cached data beats an empty or error screen. Never
     * touches PENDING/FAILED rows, which exist solely via the outbox replay
     * path above.
     */
    suspend fun refreshFromBackend() {
        val userId = tokenStore.currentUserId ?: return
        try {
            api.getWorkTimeEntries(userId, "100", "DESC")
                .forEach { dto ->
                    workTimeDao.upsertFromServer(dto.toEntity(), dto.breaks.map { it.toEntity() })
                }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort only — see doc comment above.
        }
        try {
            val overrides = api.getWorkTimeMonthOverrides(userId)
            workTimeMonthOverrideDao.replaceAll(
                overrides.mapNotNull { dto ->
                    val year = dto.year.toIntOrNull() ?: return@mapNotNull null
                    val month = dto.month.toIntOrNull() ?: return@mapNotNull null
                    WorkTimeMonthOverrideEntity(year = year, month = month, daysWorked = dto.daysWorked)
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort only — see doc comment above.
        }
    }

    /**
     * Direct REST write, no outbox — a manual days-worked override is a
     * low-frequency, settings-adjacent edit, not offline-first write data
     * like a work-time entry itself (see [WorkTimeMonthOverrideEntity]).
     */
    suspend fun setMonthOverride(year: Int, month: Int, daysWorked: String) {
        val userId = tokenStore.currentUserId ?: return
        api.updateWorkTimeMonthOverride(
            userId,
            year.toString(),
            month.toString(),
            WorkTimeMonthOverridePayload(daysWorked = daysWorked),
        )
        workTimeMonthOverrideDao.upsert(WorkTimeMonthOverrideEntity(year = year, month = month, daysWorked = daysWorked))
    }

    suspend fun clearMonthOverride(year: Int, month: Int) {
        val userId = tokenStore.currentUserId ?: return
        api.deleteWorkTimeMonthOverride(userId, year.toString(), month.toString())
        workTimeMonthOverrideDao.delete(year, month)
    }
}

private fun WorkTimeEntryDto.toEntity() = WorkTimeEntryEntity(
    serverId = id.toLongOrNull(),
    outboxId = null,
    date = date,
    workStart = workStart,
    workEnd = workEnd,
    targetDailyHours = targetDailyHours,
    paidBreak = paidBreak == "1",
    syncStatus = SyncStatus.SYNCED,
)

private fun WorkTimeBreakDto.toEntity() = WorkTimeBreakEntity(
    entryId = 0, // overwritten by WorkTimeDao.upsertFromServer once the parent's local id is known
    startTime = startTime,
    endTime = endTime,
)
