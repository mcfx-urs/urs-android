package ch.mcfx.urs.location

import ch.mcfx.urs.data.local.LocationCaptureLogDao
import ch.mcfx.urs.data.local.LocationCaptureLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fire-and-forget debug trail for the adaptive-interval feature (GitHub
 * issue #60) — the About screen's "Location capture" card reads this back
 * so it's traceable why a measurement did or didn't happen. Best-effort
 * only: a lost log entry never affects the actual capture logic.
 */
class LocationCaptureDebugLog(
    private val dao: LocationCaptureLogDao,
    private val scope: CoroutineScope,
) {
    fun log(eventType: String, detail: String) {
        scope.launch(Dispatchers.IO) {
            dao.insert(LocationCaptureLogEntity(timestampMillis = System.currentTimeMillis(), eventType = eventType, detail = detail))
            dao.trim()
        }
    }
}
