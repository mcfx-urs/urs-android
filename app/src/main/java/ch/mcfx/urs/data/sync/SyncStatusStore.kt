package ch.mcfx.urs.data.sync

import android.content.Context

private const val PREFS_NAME = "sync_status_prefs"
private const val KEY_LAST_SUCCESS_AT = "last_success_at"

/**
 * Persists the wall-clock time of the last fully-successful outbox replay
 * (see [SyncManager]'s `replayOutbox`) — the outbox itself never records
 * this, since a synced row is simply deleted once it lands. Absent means the
 * app has never completed a clean sync pass since install (or since this
 * SharedPreferences file was cleared). Same plain-SharedPreferences pattern
 * as [ch.mcfx.urs.watchrelay.WatchRelaySettingsStore].
 */
class SyncStatusStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastSuccessAt(): Long? = prefs.getLong(KEY_LAST_SUCCESS_AT, -1L).takeIf { it >= 0 }

    fun recordSuccess(atEpochMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_SUCCESS_AT, atEpochMillis).apply()
    }
}
