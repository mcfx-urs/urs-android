package ch.mcfx.urs.data.sync

import android.content.Context

private const val PREFS_NAME = "pull_cursor_prefs"

/**
 * Per-domain delta-pull cursors (the opaque `cursor` string the backend
 * returned with the last applied delta). Kept in plain SharedPreferences like
 * [SyncStatusStore]. A cursor is only trusted while the domain's local rows
 * still exist, so a repository must ignore it and pull in full when its table
 * is empty (fresh install, destructive schema migration, logout).
 */
class PullCursorStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(domain: String): String? = prefs.getString(domain, null)

    fun set(domain: String, cursor: String) {
        prefs.edit().putString(domain, cursor).apply()
    }
}
