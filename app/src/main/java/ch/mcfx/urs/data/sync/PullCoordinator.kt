package ch.mcfx.urs.data.sync

import android.util.Log
import ch.mcfx.urs.data.CatalogRepository
import ch.mcfx.urs.data.ChoreRepository
import ch.mcfx.urs.data.FuelRepository
import ch.mcfx.urs.data.InventoryRepository
import ch.mcfx.urs.data.KanbanRepository
import ch.mcfx.urs.data.LocationHistoryRepository
import ch.mcfx.urs.data.NoteRepository
import ch.mcfx.urs.data.ServiceRepository
import ch.mcfx.urs.data.ShoppingListRepository
import ch.mcfx.urs.data.TagRepository
import ch.mcfx.urs.data.VehicleRepository
import ch.mcfx.urs.data.WorkTimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Live sync state for UI surfaces (see the Home screen's status indicator). */
enum class SyncPhase { IDLE_OK, SYNCING, IDLE_ERROR }

/**
 * Single entry point for "pull everything from the server" (GitHub issue
 * #53): until now, every domain's `refreshFromBackend()` only ever ran when
 * its own screen happened to be opened, so a fresh install (or a destructive
 * schema-migration wipe, see [AppDatabase][ch.mcfx.urs.data.local.AppDatabase])
 * showed empty screens until each one was visited manually, and a pull could
 * race the WireGuard tunnel coming up.
 *
 * Callers: [AppContainer][ch.mcfx.urs.AppContainer]'s `networkGate`
 * `onConnectivityAvailable` hook (fires once the tunnel is confirmed
 * reachable — the same signal push already uses, closing the race), the
 * periodic [SyncWorker] run (the systematic retry backstop for a domain that
 * failed on the first attempt — same 15-minute floor push already relies
 * on), and the manual "Sync now" actions (`AboutViewModel`, `FuelHubScreen`),
 * which now do push-then-pull instead of push-only.
 *
 * Every domain's own `refreshFromBackend()` is best-effort internally (one
 * failing domain never stops the rest of the pass, and never throws out of
 * this coordinator) — [BakingRepository][ch.mcfx.urs.data.BakingRepository]
 * is deliberately not included yet: it has no `refreshFromBackend()` at all
 * (bake plans are only ever pushed, never pulled — a real gap, arguably
 * worse than anything else issue #53 found), but wiring one up needs to
 * reconcile pulled steps against already-scheduled step alarms
 * ([ch.mcfx.urs.notifications.BakingStepAlarmScheduler]), which is a
 * separate design problem from every other domain here (plain Room upsert,
 * no side effects) and deserves its own pass rather than being rushed in
 * alongside this change.
 */
class PullCoordinator(
    private val fuelRepository: FuelRepository,
    private val vehicleRepository: VehicleRepository,
    private val serviceRepository: ServiceRepository,
    private val inventoryRepository: InventoryRepository,
    private val catalogRepository: CatalogRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val workTimeRepository: WorkTimeRepository,
    private val locationHistoryRepository: LocationHistoryRepository,
    private val noteRepository: NoteRepository,
    private val choreRepository: ChoreRepository,
    private val kanbanRepository: KanbanRepository,
    private val tagRepository: TagRepository,
    private val syncStatusStore: SyncStatusStore,
) {
    // Guards against two pullAll() calls (e.g. a connectivity event and a
    // manual "Sync now" tap) interleaving their upserts against the same
    // Room tables.
    private val mutex = Mutex()

    private val _phase = MutableStateFlow(
        if (syncStatusStore.didLastPullHaveErrors()) SyncPhase.IDLE_ERROR else SyncPhase.IDLE_OK,
    )
    val phase: StateFlow<SyncPhase> = _phase

    /**
     * Pulls every domain from the server. Safe to call concurrently — a call
     * that arrives while one is already running just waits for it, then
     * runs its own fresh pass (this isn't request-coalesced, since a caller
     * that explicitly asked to sync — e.g. a manual "Sync now" tap — should
     * get its own pass, not silently ride along on one that may have started
     * before its own most recent local change).
     */
    /** @return `true` if every domain refreshed cleanly. */
    suspend fun pullAll(): Boolean = mutex.withLock {
        _phase.value = SyncPhase.SYNCING
        var anyFailed = false

        suspend fun pull(domain: String, block: suspend () -> Boolean) {
            if (!block()) {
                Log.w("PullCoordinator", "pull failed for $domain")
                anyFailed = true
            }
        }

        pull("Vehicle") { vehicleRepository.refreshFromBackend() }
        pull("Fuel") { fuelRepository.refreshFromBackend() }
        pull("Service") { serviceRepository.refreshFromBackend() }
        pull("Catalog") { catalogRepository.refreshFromBackend() }
        pull("Inventory") { inventoryRepository.refreshFromBackend() }
        pull("ShoppingList") { shoppingListRepository.refreshFromBackend() }
        pull("WorkTime") { workTimeRepository.refreshFromBackend() }
        pull("LocationHistory") { locationHistoryRepository.refreshFromBackend() }
        pull("Note") { noteRepository.refreshFromBackend() }
        pull("Chore") { choreRepository.refreshFromBackend() }
        pull("Kanban") { kanbanRepository.refreshFromBackend() }
        pull("Tag") { tagRepository.refreshFromBackend() }

        syncStatusStore.recordPullFinished(hadErrors = anyFailed)
        _phase.value = if (anyFailed) SyncPhase.IDLE_ERROR else SyncPhase.IDLE_OK
        !anyFailed
    }
}
