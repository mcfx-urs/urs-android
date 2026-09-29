package ch.mcfx.urs.kanban

import android.content.Context

private const val PREFS_NAME = "kanban_view_mode_prefs"

enum class KanbanViewMode { Board, List }

/**
 * Local, device-only choice between the multi-column board layout and the
 * stacked list layout, remembered per board (keyed by the board's public
 * id). Same plain-SharedPreferences pattern as
 * [ch.mcfx.urs.chores.ChoreOrderStore]; not synced server-side.
 */
class KanbanViewModeStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(boardId: String): KanbanViewMode =
        if (prefs.getString(boardId, null) == KanbanViewMode.List.name) KanbanViewMode.List else KanbanViewMode.Board

    fun set(boardId: String, mode: KanbanViewMode) {
        prefs.edit().putString(boardId, mode.name).apply()
    }

    /** Called from [ch.mcfx.urs.auth.AuthRepository.logout], like every other per-user local cache. */
    fun clear() {
        prefs.edit().clear().apply()
    }
}
