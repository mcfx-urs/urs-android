package ch.mcfx.urs.data.sync

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PREFS_NAME = "sync_status_prefs"
private const val KEY_LAST_SUCCESS_AT = "last_success_at"
private const val KEY_LAST_PULL_AT = "last_pull_at"
private const val KEY_LAST_PULL_HAD_ERRORS = "last_pull_had_errors"
private const val KEY_LAST_PULL_RECORD = "last_pull_record"
private const val KEY_LEGACY_PULL_HISTORY = "pull_history"
private const val PULL_HISTORY_FILE = "pull-history.jsonl"
private val PULL_HISTORY_RETENTION_MILLIS = TimeUnit.DAYS.toMillis(30)
private val PULL_HISTORY_COMPACT_SLACK_MILLIS = TimeUnit.DAYS.toMillis(1)

/** One domain's result in a pull pass: [cause] is set on failure, [durationMs] is the wall-clock time of the step, [bytes] the response body bytes received on the wire. */
@Serializable
data class PullDomainResult(
    val domain: String,
    val ok: Boolean,
    val cause: String? = null,
    val durationMs: Long,
    val bytes: Long = 0,
)

/** What started a pull pass. */
@Serializable
enum class PullTrigger { MANUAL, PERIODIC, CONNECTIVITY }

/** Network the pass started on: [network] is wifi, cellular, ethernet, other or none; [vpn] is whether a VPN was active. */
@Serializable
data class PullNetwork(val network: String = "", val vpn: Boolean = false)

/** One finished pull pass: when it ended, what started it, the network it ran on, and every domain's result. */
@Serializable
data class PullRecord(
    val atMillis: Long,
    val results: List<PullDomainResult>,
    val trigger: PullTrigger? = null,
    val network: PullNetwork = PullNetwork(),
)

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
    private val historyFile = File(context.filesDir, PULL_HISTORY_FILE)
    private val historyLock = Any()

    fun getLastSuccessAt(): Long? = prefs.getLong(KEY_LAST_SUCCESS_AT, -1L).takeIf { it >= 0 }

    fun recordSuccess(atEpochMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_SUCCESS_AT, atEpochMillis).apply()
    }

    fun getLastPullAt(): Long? = prefs.getLong(KEY_LAST_PULL_AT, -1L).takeIf { it >= 0 }

    /** Whether at least one domain failed during the pull pass at [getLastPullAt]. */
    fun didLastPullHaveErrors(): Boolean = prefs.getBoolean(KEY_LAST_PULL_HAD_ERRORS, false)

    /** Pull passes of the last 30 days, oldest first, read from the history file (one JSON record per line). */
    fun getPullHistory(): List<PullRecord> = synchronized(historyLock) {
        migrateLegacyHistory()
        readHistory()
    }

    /** Per-domain results of the pull pass at [getLastPullAt]; empty if none was recorded. */
    fun getLastPullResults(): List<PullDomainResult> =
        prefs.getString(KEY_LAST_PULL_RECORD, null)
            ?.let { runCatching { Json.decodeFromString<PullRecord>(it) }.getOrNull() }
            ?.results
            .orEmpty()

    fun recordPullFinished(
        hadErrors: Boolean,
        results: List<PullDomainResult>,
        trigger: PullTrigger? = null,
        network: PullNetwork = PullNetwork(),
        atEpochMillis: Long = System.currentTimeMillis(),
    ) {
        val record = PullRecord(atEpochMillis, results, trigger, network)
        prefs.edit()
            .putLong(KEY_LAST_PULL_AT, atEpochMillis)
            .putBoolean(KEY_LAST_PULL_HAD_ERRORS, hadErrors)
            .putString(KEY_LAST_PULL_RECORD, Json.encodeToString(record))
            .apply()
        runCatching { synchronized(historyLock) { appendToHistory(record) } }
    }

    private fun readHistory(): List<PullRecord> {
        if (!historyFile.exists()) return emptyList()
        return historyFile.readLines().mapNotNull { line ->
            runCatching { Json.decodeFromString<PullRecord>(line) }.getOrNull()
        }
    }

    // Appends one line; the file is only rewritten once its oldest record is more
    // than a day past the retention window, so most passes cost a single append.
    private fun appendToHistory(record: PullRecord) {
        historyFile.appendText(Json.encodeToString(record) + "\n")
        val cutoff = record.atMillis - PULL_HISTORY_RETENTION_MILLIS
        val oldest = historyFile.useLines { lines ->
            lines.firstOrNull()?.let { runCatching { Json.decodeFromString<PullRecord>(it).atMillis }.getOrNull() }
        }
        if (oldest != null && oldest < cutoff - PULL_HISTORY_COMPACT_SLACK_MILLIS) {
            val kept = readHistory().filter { it.atMillis >= cutoff }
            val tmp = File(historyFile.parentFile, "$PULL_HISTORY_FILE.tmp")
            tmp.writeText(kept.joinToString("") { Json.encodeToString(it) + "\n" })
            tmp.renameTo(historyFile)
        }
    }

    // Records kept in SharedPreferences by an earlier build move into the history file once.
    private fun migrateLegacyHistory() {
        val legacy = prefs.getString(KEY_LEGACY_PULL_HISTORY, null) ?: return
        runCatching {
            val records = Json.decodeFromString<List<PullRecord>>(legacy)
            if (!historyFile.exists()) historyFile.writeText(records.joinToString("") { Json.encodeToString(it) + "\n" })
        }
        prefs.edit().remove(KEY_LEGACY_PULL_HISTORY).apply()
    }
}
