package ch.mcfx.urs.home

import android.content.Context
import ch.mcfx.urs.navigation.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Launcher-style Home layout model + persistence (GitHub issue #12).
 *
 * The grid is a fixed 2 columns, scrolling vertically. Every Home item —
 * including the ones that are bespoke full-width rows today (Work Time,
 * New Fuel Fill, Life Map) — is one generic tile with an explicit
 * `(column, row, width, height)`, width and height each in {1, 2}. One
 * row-unit is today's `TileHeight`.
 *
 * This file is the data + conflict-resolution core only. The edit-mode UI
 * (long-press to enter, corner-dot resize, drag-move, the add picker,
 * "Done"/tap-outside to exit) is still to be built on top of it — see the
 * nightrun report.
 */

const val HOME_GRID_COLUMNS = 2

@Serializable
data class HomeTilePlacement(
    /** [Destination.name]; "NEW_FUEL_FILL" is the one non-Destination tile. */
    val destinationId: String,
    val column: Int,
    val row: Int,
    val width: Int,
    val height: Int,
) {
    val endColumnExclusive: Int get() = column + width
    val endRowExclusive: Int get() = row + height
}

/** Synthetic id for the "New Fuel Fill" shortcut, which has no [Destination]. */
const val NEW_FUEL_FILL_TILE_ID = "NEW_FUEL_FILL"

object HomeLayoutEngine {

    // Mirrors today's hardcoded FEATURE_TILES order and the current
    // full-width treatment of Work Time / New Fuel Fill / Life Map / Gokart.
    private val DEFAULT_ORDER: List<Pair<String, Int>> = listOf(
        Destination.WORK_TIME.name to 2,
        NEW_FUEL_FILL_TILE_ID to 2,
        Destination.LIFE_MAP.name to 2,
        Destination.SHOPPING_LIST.name to 1,
        Destination.VEHICLE.name to 1,
        Destination.INVENTORY.name to 1,
        Destination.BEER.name to 1,
        Destination.BAKING.name to 1,
        Destination.NOTES.name to 1,
        Destination.CHORES.name to 1,
        Destination.PRICE_MONITOR.name to 1,
        Destination.K.name to 1,
        Destination.GOKART.name to 2,
    )

    /** Today's order/sizes, laid out top-to-bottom, left-to-right. */
    fun defaultLayout(): List<HomeTilePlacement> {
        val placed = mutableListOf<HomeTilePlacement>()
        var col = 0
        var row = 0
        for ((id, width) in DEFAULT_ORDER) {
            if (col + width > HOME_GRID_COLUMNS) {
                col = 0
                row += 1
            }
            placed += HomeTilePlacement(id, col, row, width, height = 1)
            col += width
            if (col >= HOME_GRID_COLUMNS) {
                col = 0
                row += 1
            }
        }
        return placed
    }

    private fun overlaps(a: HomeTilePlacement, b: HomeTilePlacement): Boolean =
        a.column < b.endColumnExclusive && b.column < a.endColumnExclusive &&
            a.row < b.endRowExclusive && b.row < a.endRowExclusive

    /** Clamp a placement to the grid: valid sizes, in-bounds column. */
    private fun clamp(p: HomeTilePlacement): HomeTilePlacement {
        val w = p.width.coerceIn(1, 2)
        val h = p.height.coerceIn(1, 2)
        val c = p.column.coerceIn(0, HOME_GRID_COLUMNS - w)
        return p.copy(column = c, width = w, height = h, row = p.row.coerceAtLeast(0))
    }

    /**
     * Resolves overlaps by pushing conflicting tiles straight down by the
     * minimum number of rows needed to clear, recursively. [priorityId], if
     * given, is placed first and never moved — the tile the user just
     * dragged or resized.
     */
    fun resolveConflicts(input: List<HomeTilePlacement>, priorityId: String? = null): List<HomeTilePlacement> {
        val clamped = input.map(::clamp)
        val ordered = buildList {
            clamped.firstOrNull { it.destinationId == priorityId }?.let(::add)
            addAll(
                clamped
                    .filter { it.destinationId != priorityId }
                    .sortedWith(compareBy({ it.row }, { it.column })),
            )
        }

        val placed = mutableListOf<HomeTilePlacement>()
        for (tile in ordered) {
            var current = tile
            var guard = 0
            while (placed.any { overlaps(it, current) } && guard++ < 1000) {
                val clearRow = placed.filter { overlaps(it, current) }.maxOf { it.endRowExclusive }
                current = current.copy(row = clearRow)
            }
            placed += current
        }
        return placed.sortedWith(compareBy({ it.row }, { it.column }))
    }

    fun moveTile(layout: List<HomeTilePlacement>, id: String, column: Int, row: Int): List<HomeTilePlacement> {
        val next = layout.map { if (it.destinationId == id) it.copy(column = column, row = row) else it }
        return resolveConflicts(next, priorityId = id)
    }

    fun resizeTile(layout: List<HomeTilePlacement>, id: String, width: Int, height: Int): List<HomeTilePlacement> {
        val next = layout.map { if (it.destinationId == id) it.copy(width = width, height = height) else it }
        return resolveConflicts(next, priorityId = id)
    }

    fun removeTile(layout: List<HomeTilePlacement>, id: String): List<HomeTilePlacement> =
        resolveConflicts(layout.filterNot { it.destinationId == id })

    /** Adds [id] at 1x1 in the first free cell. */
    fun addTile(layout: List<HomeTilePlacement>, id: String): List<HomeTilePlacement> {
        if (layout.any { it.destinationId == id }) return layout
        val occupied = layout.flatMap { p ->
            (p.row until p.endRowExclusive).flatMap { r -> (p.column until p.endColumnExclusive).map { c -> r to c } }
        }.toSet()
        var row = 0
        while (true) {
            for (col in 0 until HOME_GRID_COLUMNS) {
                if ((row to col) !in occupied) {
                    return resolveConflicts(layout + HomeTilePlacement(id, col, row, 1, 1))
                }
            }
            row += 1
        }
    }
}

private const val PREFS_NAME = "home_layout_prefs"
private const val KEY_LAYOUT = "layout_json"

/**
 * Plain-SharedPreferences persistence, same pattern as
 * [ch.mcfx.urs.settings.ThemeSettingsStore]. Device-local only, no sync.
 */
class HomeLayoutStore(context: Context, private val json: Json) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val serializer = ListSerializer(HomeTilePlacement.serializer())

    private val _layout = MutableStateFlow(readPersisted() ?: HomeLayoutEngine.defaultLayout())
    val layout: StateFlow<List<HomeTilePlacement>> = _layout.asStateFlow()

    fun setLayout(placements: List<HomeTilePlacement>) {
        val resolved = HomeLayoutEngine.resolveConflicts(placements)
        prefs.edit().putString(KEY_LAYOUT, json.encodeToString(serializer, resolved)).apply()
        _layout.value = resolved
    }

    fun resetToDefault() = setLayout(HomeLayoutEngine.defaultLayout())

    private fun readPersisted(): List<HomeTilePlacement>? =
        prefs.getString(KEY_LAYOUT, null)?.let { stored ->
            runCatching { json.decodeFromString(serializer, stored) }.getOrNull()
        }
}
