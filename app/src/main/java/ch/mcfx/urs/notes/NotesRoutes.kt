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

    fun detail(noteId: String): String = "notes/list/${Uri.encode(noteId)}"
}
