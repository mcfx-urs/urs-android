package ch.mcfx.urs.data

import ch.mcfx.urs.beer.BeerStats
import ch.mcfx.urs.data.local.LocationHistoryDao
import ch.mcfx.urs.data.local.LocationHistoryEntity
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.data.remote.LocationHistoryDto
import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.data.sync.PullCursorStore
import ch.mcfx.urs.data.sync.pullCatching
import ch.mcfx.urs.data.sync.PullOutcome
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Pulls the authenticated user's server-side location history down into
 * Room so Breadcrumbs shows every point regardless of which device/install
 * originally captured it — the upload-only outbox sync
 * ([ch.mcfx.urs.data.sync.SyncManager.replayCreateLocationHistory]) never
 * had a pull path back down.
 *
 * Delta pull: the backend's `since` cursor is the last pulled
 * `location_history_id`, so a normal pull only fetches points added since.
 * A full pull (no stored cursor, no synced rows locally, or a cursor the
 * backend rejected and answered with `full`) walks every page from the start
 * and reconciles the local SYNCED set against it, as the pre-delta pull did.
 * Pages are capped by the backend's `sanitizeLimit` (10000), so the loop
 * follows `has_more` instead of trusting a single call.
 */
class LocationHistoryRepository(
    private val api: UrsApi,
    private val locationHistoryDao: LocationHistoryDao,
    private val cursorStore: PullCursorStore,
) {
    /**
     * Opportunistic backend refresh — run when reachable, never blocking
     * the UI or surfacing an error on failure, same best-effort shape as
     * [FuelRepository.refreshFromBackend].
     */
    /** @return an ok [PullOutcome] if the refresh completed cleanly (see [PullCoordinator]). */
    suspend fun refreshFromBackend(): PullOutcome = refreshQuietly {
        val storedCursor = cursorStore.get(CURSOR_KEY)
        var cursor = if (storedCursor != null && locationHistoryDao.syncedCount() > 0) storedCursor else INITIAL_CURSOR
        val fullPoints = mutableListOf<LocationHistoryEntity>()
        var isFull = false
        var firstPage = true
        while (true) {
            val page = api.getLocationHistoryDelta(since = cursor, limit = PAGE_SIZE)
            if (firstPage) {
                isFull = page.full
                firstPage = false
            }
            val points = page.items.map { it.toEntity() }
            if (isFull) {
                fullPoints += points
            } else {
                locationHistoryDao.upsertFromServer(points)
                cursorStore.set(CURSOR_KEY, page.cursor)
            }
            cursor = page.cursor
            if (!page.hasMore) break
        }
        if (isFull) {
            locationHistoryDao.reconcileFromServer(fullPoints)
            cursorStore.set(CURSOR_KEY, cursor)
        }
    }

    private suspend fun refreshQuietly(block: suspend () -> Unit): PullOutcome = pullCatching("LocationHistoryRepository", block)

    companion object {
        private const val PAGE_SIZE = "10000"
        private const val INITIAL_CURSOR = "0"
        private const val CURSOR_KEY = "LocationHistory"
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
