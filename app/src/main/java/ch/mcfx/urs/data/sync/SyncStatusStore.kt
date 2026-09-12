package ch.mcfx.urs.data.sync

import android.content.Context

private const val PREFS_NAME = "sync_status_prefs"
private const val KEY_LAST_SUCCESS_AT = "last_success_at"
private const val KEY_LAST_PULL_AT = "last_pull_at"
private const val KEY_LAST_PULL_HAD_ERRORS = "last_pull_had_errors"

/**
 * Persists the wall-clock time of the last fully-successful outbox replay
 * (see [SyncManager]'s `replayOutbox`) — the outbox itself never records
 * this, since a synced row is simply deleted once it lands. Absent means the
 * app has never completed a clean sync pass since install (or since this
 * SharedPreferences file was cleared). Same plain-SharedPreferences pattern
 * as [ch.mcfx.urs.watchrelay.WatchRelaySettingsStore].
 *
 * Also tracks the last *pull* pass (see [PullCoordinator]) separately from
 * the outbox-replay ("push") tracking above — GitHub issue #53 found no
 * "last pulled at" signal existed anywhere before this.
 */
class SyncStatusStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastSuccessAt(): Long? = prefs.getLong(KEY_LAST_SUCCESS_AT, -1L).takeIf { it >= 0 }

    fun recordSuccess(atEpochMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_SUCCESS_AT, atEpochMillis).apply()
    }

    fun getLastPullAt(): Long? = prefs.getLong(KEY_LAST_PULL_AT, -1L).takeIf { it >= 0 }

    /** Whether at least one domain failed during the pull pass at [getLastPullAt]. */
    fun didLastPullHaveErrors(): Boolean = prefs.getBoolean(KEY_LAST_PULL_HAD_ERRORS, false)

    fun recordPullFinished(hadErrors: Boolean, atEpochMillis: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putLong(KEY_LAST_PULL_AT, atEpochMillis)
            .putBoolean(KEY_LAST_PULL_HAD_ERRORS, hadErrors)
            .apply()
    }
}
