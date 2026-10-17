package ch.mcfx.urs.notes

import android.net.Uri

// Mirrors BakingRoutes' shape: the hub screen (active notes) is
// Destination.NOTES' own route, DETAIL is a parameterized second route (also
// the reminder notification's deep-link target), HISTORY a third
// top-level-reachable one for completed notes. Encoded since a note's
// stand-in id could, in principle, collide with path-reserved characters.
object NotesRoutes {
    const val LIST = "notes/list"
    const val DETAIL = "notes/list/{noteId}"
    const val HISTORY = "notes/history"
    const val NEW = "notes/new"

    /** Saved-state key the detail screen sets on the hub's back stack entry after completing a note. */
    const val NOTE_COMPLETED_RESULT = "noteCompleted"

    /** Same for reopening: set on the history's back stack entry. */
    const val NOTE_REOPENED_RESULT = "noteReopened"

    fun detail(noteId: String): String = "notes/list/${Uri.encode(noteId)}"
}
