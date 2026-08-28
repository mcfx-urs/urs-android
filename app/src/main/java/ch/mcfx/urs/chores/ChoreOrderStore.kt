package ch.mcfx.urs.chores

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "chore_order_prefs"
private const val KEY_ORDER = "type_order"

/**
 * Local, device-only manual sort order for chore types (GitHub issue #33) —
 * a list of [ch.mcfx.urs.data.local.publicId]s in the order the user
 * dragged them into. Same plain-SharedPreferences pattern as
 * [ChoreReminderSettingsStore]; not synced server-side, same as the Home
 * screen's tile layout store.
 */
class ChoreOrderStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _order = MutableStateFlow(readOrder())
    val order: StateFlow<List<String>> = _order.asStateFlow()

    fun setOrder(publicIds: List<String>) {
        prefs.edit().putString(KEY_ORDER, publicIds.joinToString(",")).apply()
        _order.value = publicIds
    }

    /** Called from [ch.mcfx.urs.auth.AuthRepository.logout], like every other per-user local cache. */
    fun clear() {
        prefs.edit().clear().apply()
        _order.value = emptyList()
    }

    private fun readOrder(): List<String> =
        prefs.getString(KEY_ORDER, null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
}
