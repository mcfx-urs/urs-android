package ch.mcfx.urs.notifications

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PREFS_NAME = "reminder_prefs"
private const val KEY_REMINDERS = "reminders_json"

@Serializable
data class ReminderConfig(
    val id: Int,
    val channelId: String,
    val hour: Int,
    val minute: Int,
    val title: String,
    val body: String,
    val deepLinkRoute: String? = null,
    // "yyyy-MM-dd" of the last day this actually fired, null if never — lets
    // rearmAndCheckMissed() tell "already fired today" apart from "missed".
    val lastFiredDate: String? = null,
)

// Persists the set of currently-scheduled daily reminders so
// ReminderScheduler can re-arm them after a reboot (exact alarms don't
// survive one) and detect ones that were missed while the phone was
// off/Doze-restricted. Nothing here is sensitive, unlike
// VpnConfigRepository's WireGuard key — plain JSON is fine, no Keystore
// encryption needed.
class ReminderStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun getAll(): List<ReminderConfig> {
        val raw = prefs.getString(KEY_REMINDERS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ReminderConfig>>(raw) }.getOrDefault(emptyList())
    }

    fun save(reminder: ReminderConfig) {
        val updated = getAll().filterNot { it.id == reminder.id } + reminder
        persist(updated)
    }

    fun remove(id: Int) {
        persist(getAll().filterNot { it.id == id })
    }

    private fun persist(reminders: List<ReminderConfig>) {
        prefs.edit().putString(KEY_REMINDERS, json.encodeToString(reminders)).apply()
    }
}
