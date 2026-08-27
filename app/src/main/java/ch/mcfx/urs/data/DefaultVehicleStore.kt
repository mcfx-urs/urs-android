package ch.mcfx.urs.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "default_vehicle_prefs"
private const val KEY_DEFAULT_VEHICLE_ID = "default_vehicle_id"

/**
 * Last-known value of the server-stored per-user default vehicle
 * (`user_default_vehicle_id`), cached locally so the Home quick-stat and the
 * fuel pickers/stats don't blank out on a cold start before
 * `GET /api/v1/getuser` resolves. Same plain-SharedPreferences +
 * observable-[StateFlow] shape as [ch.mcfx.urs.settings.ThemeSettingsStore];
 * [WorkSettingsStore] is the equivalent cache for the work-time settings.
 */
class DefaultVehicleStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _defaultVehicleId = MutableStateFlow(prefs.getString(KEY_DEFAULT_VEHICLE_ID, null)?.ifBlank { null })

    /** null when no server default has been set yet. */
    val defaultVehicleId: StateFlow<String?> = _defaultVehicleId.asStateFlow()

    fun set(vehicleId: String?) {
        val normalized = vehicleId?.ifBlank { null }
        prefs.edit().apply {
            if (normalized == null) remove(KEY_DEFAULT_VEHICLE_ID) else putString(KEY_DEFAULT_VEHICLE_ID, normalized)
        }.apply()
        _defaultVehicleId.value = normalized
    }

    /** Called from [ch.mcfx.urs.auth.AuthRepository.logout], like every other per-user local cache. */
    fun clear() {
        prefs.edit().clear().apply()
        _defaultVehicleId.value = null
    }
}

/**
 * The vehicle a screen should actually treat as the default: the stored
 * preference while it still points at an existing vehicle, otherwise the
 * earliest-created one (lowest server id) so a fresh install or a deleted
 * previous default never surfaces an empty state. Returns null only when
 * there are no vehicles at all.
 */
fun resolveDefaultVehicleId(preferredId: String?, vehicleIds: List<String>): String? {
    if (vehicleIds.isEmpty()) return null
    if (preferredId != null && preferredId in vehicleIds) return preferredId
    return vehicleIds.minByOrNull { it.toIntOrNull() ?: Int.MAX_VALUE }
}
