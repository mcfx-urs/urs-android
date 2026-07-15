package ch.mcfx.urs.data

import ch.mcfx.urs.data.local.OutboxDao
import ch.mcfx.urs.data.local.OutboxMutationEntity
import ch.mcfx.urs.data.local.OutboxWorkTimeBreakPayload
import ch.mcfx.urs.data.local.OutboxWorkTimeEntryPayload
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.data.local.WorkTimeBreakEntity
import ch.mcfx.urs.data.local.WorkTimeDao
import ch.mcfx.urs.data.local.WorkTimeEntryEntity
import ch.mcfx.urs.data.local.WorkTimeEntryWithBreaks
import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.data.remote.WorkTimeBreakDto
import ch.mcfx.urs.data.remote.WorkTimeEntryDto
import ch.mcfx.urs.data.sync.SyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WorkTimeRepository(
    private val api: UrsApi,
    private val workTimeDao: WorkTimeDao,
    private val outboxDao: OutboxDao,
    private val syncManager: SyncManager,
    private val applicationScope: CoroutineScope,
    private val json: Json,
) {

    fun observeEntries(): Flow<List<WorkTimeEntryWithBreaks>> = workTimeDao.observeAll()

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
        breaks: List<Pair<String, String>>,
    ) {
        val payload = OutboxWorkTimeEntryPayload(
            userId = UserDefaults.DEFAULT_USER_ID,
            date = date,
            workStart = workStart,
            workEnd = workEnd,
            targetDailyHours = targetDailyHours,
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
                syncStatus = SyncStatus.PENDING,
            ),
        )
        workTimeDao.insertBreaks(
            breaks.map { (start, end) -> WorkTimeBreakEntity(entryId = entryId, startTime = start, endTime = end) },
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
        try {
            api.getWorkTimeEntries(UserDefaults.DEFAULT_USER_ID, "100", "DESC")
                .forEach { dto ->
                    workTimeDao.upsertFromServer(dto.toEntity(), dto.breaks.map { it.toEntity() })
                }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort only — see doc comment above.
        }
    }
}

private fun WorkTimeEntryDto.toEntity() = WorkTimeEntryEntity(
    serverId = id.toLongOrNull(),
    outboxId = null,
    date = date,
    workStart = workStart,
    workEnd = workEnd,
    targetDailyHours = targetDailyHours,
    syncStatus = SyncStatus.SYNCED,
)

private fun WorkTimeBreakDto.toEntity() = WorkTimeBreakEntity(
    entryId = 0, // overwritten by WorkTimeDao.upsertFromServer once the parent's local id is known
    startTime = startTime,
    endTime = endTime,
)
