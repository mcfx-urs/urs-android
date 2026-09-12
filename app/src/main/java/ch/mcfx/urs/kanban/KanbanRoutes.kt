package ch.mcfx.urs.kanban

import android.net.Uri

// Mirrors ShoppingListRoutes' shape: the hub screen (the list of boards) is
// Destination.KANBAN's own route, BOARD_DETAIL is the app's second
// parameterized route. Encoded since a board's stand-in id could, in
// principle, collide with path-reserved characters no differently than a
// list id could.
object KanbanRoutes {
    const val BOARDS = "kanban/boards"
    const val BOARD_DETAIL = "kanban/{boardId}"

    fun boardDetail(boardId: String): String = "kanban/${Uri.encode(boardId)}"
}
