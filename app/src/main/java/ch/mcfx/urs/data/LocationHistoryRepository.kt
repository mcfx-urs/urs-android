package ch.mcfx.urs.data

import ch.mcfx.urs.beer.BeerStats
import ch.mcfx.urs.data.local.LocationHistoryDao
import ch.mcfx.urs.data.local.LocationHistoryEntity
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.data.remote.LocationHistoryDto
import ch.mcfx.urs.data.remote.UrsApi
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

/**
 * Pulls the authenticated user's full server-side location history down
 * into Room so the life map shows every point regardless of which
 * device/install originally captured it — the upload-only outbox
 * sync ([ch.mcfx.urs.data.sync.SyncManager.replayCreateLocationHistory])
 * never had a pull path back down.
 *
 * Pages through the endpoint's hard cap (`sanitizeLimit`'s 10000 in
 * `urs-backend`) rather than trusting a single call: an always-on periodic
 * capture (as low as 1 minute between fixes) blows past 10000 rows within
 * ~7 weeks, let alone a year, so a single page is nowhere near "the whole
 * history" once a life map has been running for a while. Fetching a single
 * short page (the endpoint's own default is only 50) or a single capped one
 * would both silently truncate the result and make
 * [LocationHistoryDao.reconcileFromServer] delete every local point past
 * whatever page boundary it stopped at, mistaking "not returned this call"
 * for "removed server-side".
 */
class LocationHistoryRepository(
    private val api: UrsApi,
    private val locationHistoryDao: LocationHistoryDao,
) {
    /**
     * Opportunistic backend refresh — run when reachable, never blocking
     * the UI or surfacing an error on failure, same best-effort shape as
     * [FuelRepository.refreshFromBackend].
     */
    suspend fun refreshFromBackend() {
        refreshQuietly {
            locationHistoryDao.reconcileFromServer(fetchAllPages().map { it.toEntity() })
        }
    }

    /**
     * Walks every page oldest-to-newest, advancing `from` to one second past
     * the last row of each page — `location_history_captured_at >= from` on
     * the backend side means re-sending that same last row's timestamp
     * as-is would just re-fetch it forever. Safe under this app's own
     * capture cadence (1 minute is the shortest interval it ever schedules,
     * see `LocationCaptureScheduler`), so no two of a single user's rows can
     * land in the same second. Stops at the first page shorter than
     * [PAGE_SIZE] — the natural end of the data, not an arbitrary cutoff.
     */
    private suspend fun fetchAllPages(): List<LocationHistoryDto> {
        val allPoints = mutableListOf<LocationHistoryDto>()
        var from = EPOCH_START
        while (true) {
            val page = emptyAsNull { api.getLocationHistory(from = from, limit = PAGE_SIZE, order = "ASC") }
            if (page.isEmpty()) break
            allPoints += page
            if (page.size < PAGE_SIZE.toInt()) break
            from = LocalDateTime.parse(page.last().capturedAt, BeerStats.DATE_FORMAT)
                .plusSeconds(1)
                .format(BeerStats.DATE_FORMAT)
        }
        return allPoints
    }

    private suspend fun refreshQuietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort only — see refreshFromBackend's doc comment.
        }
    }

    // The backend encodes empty result sets as JSON `null` instead of `[]`.
    private suspend fun <T> emptyAsNull(call: suspend () -> List<T>): List<T> =
        try {
            call()
        } catch (_: SerializationException) {
            emptyList()
        }

    companion object {
        private const val PAGE_SIZE = "10000"
        private const val EPOCH_START = "1970-01-01 00:00:00"
    }
}

private fun LocationHistoryDto.toEntity() = LocationHistoryEntity(
    serverId = id.toLongOrNull(),
    outboxId = null,
    latitude = latitude.toDouble(),
    longitude = longitude.toDouble(),
    accuracyMeters = accuracyMeters.toFloatOrNull(),
    capturedAt = LocalDateTime.parse(capturedAt, BeerStats.DATE_FORMAT)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli(),
    syncStatus = SyncStatus.SYNCED,
)
