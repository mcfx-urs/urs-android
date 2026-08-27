package ch.mcfx.urs.chores

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "chore_reminder_prefs"
private const val KEY_GLOBAL_ENABLED = "global_enabled"
private const val KEY_NOTIFY_TYPE_IDS = "notify_type_ids"
private const val KEY_NOTIFIED_TYPE_IDS = "notified_type_ids"

/**
 * Local, opt-in state for the Chores overdue reminder (GitHub issue #29).
 * Same plain-SharedPreferences pattern as
 * [ch.mcfx.urs.settings.ThemeSettingsStore].
 *
 * - [globalEnabled] — the single Settings toggle; off by default.
 * - [notifyTypeIds] — per-type opt-in, keyed by
 *   [ch.mcfx.urs.data.local.publicId]. A type only notifies when it is in
 *   this set *and* [globalEnabled] is on *and* it has an expected interval.
 * - [notifiedTypeIds] — episode dedup: a type is added once its reminder
 *   has fired and removed again once it is no longer overdue, so each
 *   overdue stretch produces exactly one notification.
 */
class ChoreReminderSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _globalEnabled = MutableStateFlow(prefs.getBoolean(KEY_GLOBAL_ENABLED, false))
    val globalEnabled: StateFlow<Boolean> = _globalEnabled.asStateFlow()

    private val _notifyTypeIds = MutableStateFlow(readSet(KEY_NOTIFY_TYPE_IDS))
    val notifyTypeIds: StateFlow<Set<String>> = _notifyTypeIds.asStateFlow()

    fun isGlobalEnabled(): Boolean = _globalEnabled.value

    fun setGlobalEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GLOBAL_ENABLED, enabled).apply()
        _globalEnabled.value = enabled
    }

    fun isTypeNotifyEnabled(publicId: String): Boolean = publicId in _notifyTypeIds.value

    fun setTypeNotifyEnabled(publicId: String, enabled: Boolean) {
        val next = _notifyTypeIds.value.toMutableSet().apply { if (enabled) add(publicId) else remove(publicId) }
        writeSet(KEY_NOTIFY_TYPE_IDS, next)
        _notifyTypeIds.value = next
    }

    fun notifiedTypeIds(): Set<String> = readSet(KEY_NOTIFIED_TYPE_IDS)

    fun markNotified(publicId: String) {
        writeSet(KEY_NOTIFIED_TYPE_IDS, notifiedTypeIds() + publicId)
    }

    fun clearNotified(publicId: String) {
        writeSet(KEY_NOTIFIED_TYPE_IDS, notifiedTypeIds() - publicId)
    }

    private fun readSet(key: String): Set<String> = prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()

    private fun writeSet(key: String, value: Set<String>) {
        prefs.edit().putStringSet(key, value).apply()
    }
}
