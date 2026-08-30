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
 * The grid is a fixed 2 columns, scrolling vertically. The persisted, and
 * only authoritative, state is an ORDERED LIST of tiles ([HomeTileSpec]):
 * moving a tile means moving it to a different position in that order,
 * exactly like reordering items in a list. A tile's on-screen
 * `(column, row)` is never stored — [HomeLayoutEngine.pack] derives it
 * fresh every time by laying the list out strictly left-to-right/
 * top-to-bottom, so it can never drift out of sync with the order.
 *
 * This replaces an earlier design where each tile stored its own
 * `(column, row)` directly and gaps were closed by reactively nudging
 * neighbors — that model couldn't express "shift everything between the
 * old and new spot by one", which is what reordering by dragging one tile
 * onto another actually needs.
 */

const val HOME_GRID_COLUMNS = 2

/** One entry in the persisted tile order. Width/height each in {1, 2}. */
@Serializable
data class HomeTileSpec(
    /** [Destination.name]; "NEW_FUEL_FILL" is the one non-Destination tile. */
    val destinationId: String,
    val width: Int,
    val height: Int,
)

/** A tile's computed grid position — rendering-only, derived by [HomeLayoutEngine.pack], never persisted. */
data class HomeTilePlacement(
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
    // Life Map gets height 2 by default (unlike every other seed tile) — its
    // live map preview reads as too cramped at a single 112dp row, closer to
    // its previous bespoke 190dp card height at two stacked rows.
    private val DEFAULT_ORDER: List<HomeTileSpec> = listOf(
        HomeTileSpec(Destination.WORK_TIME.name, 2, 1),
        HomeTileSpec(NEW_FUEL_FILL_TILE_ID, 2, 1),
        HomeTileSpec(Destination.LIFE_MAP.name, 2, 2),
        HomeTileSpec(Destination.SHOPPING_LIST.name, 1, 1),
        HomeTileSpec(Destination.VEHICLE.name, 1, 1),
        HomeTileSpec(Destination.INVENTORY.name, 1, 1),
        HomeTileSpec(Destination.BEER.name, 1, 1),
        HomeTileSpec(Destination.BAKING.name, 1, 1),
        HomeTileSpec(Destination.NOTES.name, 1, 1),
        HomeTileSpec(Destination.CHORES.name, 1, 1),
        HomeTileSpec(Destination.PRICE_MONITOR.name, 1, 1),
        HomeTileSpec(Destination.K.name, 1, 1),
        HomeTileSpec(Destination.GOKART.name, 2, 1),
    )

    fun defaultOrder(): List<HomeTileSpec> = DEFAULT_ORDER

    private fun overlaps(a: HomeTilePlacement, b: HomeTilePlacement): Boolean =
        a.column < b.endColumnExclusive && b.column < a.endColumnExclusive &&
            a.row < b.endRowExclusive && b.row < a.endRowExclusive

    /**
     * Lays [specs] out strictly in order: each tile takes the earliest
     * (topmost, then leftmost) position that fits its size without
     * overlapping an already-placed tile. The scan cursor only ever moves
     * *forward* — it never re-checks a row an earlier tile has already
     * moved past. That's the difference between the two kinds of gaps a
     * tile's shape can leave: a spot beside a tall tile that's still ahead
     * of the cursor gets picked up by whatever comes next (the cursor
     * simply steps over the blocked cell within the same row-by-row
     * sweep), but a spot a wide tile skipped past *earlier* — because nothing
     * yet placed there fit — is never revisited, so a later, better-fitting
     * tile can't jump back into it. That's what keeps a manual drag from
     * fighting an automatic backfill: the only way to fill an
     * already-passed cell is to explicitly reorder something into it.
     */
    fun pack(specs: List<HomeTileSpec>): List<HomeTilePlacement> {
        val placed = mutableListOf<HomeTilePlacement>()
        var cursorCol = 0
        var cursorRow = 0
        for (spec in specs) {
            val width = spec.width.coerceIn(1, 2)
            val height = spec.height.coerceIn(1, 2)
            var placement: HomeTilePlacement? = null
            while (placement == null) {
                if (cursorCol + width > HOME_GRID_COLUMNS) {
                    cursorCol = 0
                    cursorRow++
                    continue
                }
                val candidate = HomeTilePlacement(spec.destinationId, cursorCol, cursorRow, width, height)
                if (placed.none { overlaps(it, candidate) }) {
                    placement = candidate
                } else {
                    cursorCol++
                }
            }
            placed += placement
            cursorCol += width
            if (cursorCol >= HOME_GRID_COLUMNS) {
                cursorCol = 0
                cursorRow++
            }
        }
        return placed
    }

    /** Moves [id] to [targetId]'s current position in the order; everything between shifts by one. */
    fun moveTile(specs: List<HomeTileSpec>, id: String, targetId: String): List<HomeTileSpec> {
        if (id == targetId) return specs
        val from = specs.indexOfFirst { it.destinationId == id }
        val to = specs.indexOfFirst { it.destinationId == targetId }
        if (from < 0 || to < 0) return specs
        val mutable = specs.toMutableList()
        val tile = mutable.removeAt(from)
        mutable.add(to, tile)
        return mutable
    }

    fun resizeTile(specs: List<HomeTileSpec>, id: String, width: Int, height: Int): List<HomeTileSpec> =
        specs.map { if (it.destinationId == id) it.copy(width = width, height = height) else it }

    fun removeTile(specs: List<HomeTileSpec>, id: String): List<HomeTileSpec> =
        specs.filterNot { it.destinationId == id }

    /** Adds [id] at 1x1, appended to the end of the order — [pack] places it in the next free slot. */
    fun addTile(specs: List<HomeTileSpec>, id: String): List<HomeTileSpec> {
        if (specs.any { it.destinationId == id }) return specs
        return specs + HomeTileSpec(id, 1, 1)
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
    private val serializer = ListSerializer(HomeTileSpec.serializer())

    private val initialOrder = readPersisted() ?: HomeLayoutEngine.defaultOrder()

    private val _order = MutableStateFlow(initialOrder)
    val order: StateFlow<List<HomeTileSpec>> = _order.asStateFlow()

    private val _layout = MutableStateFlow(HomeLayoutEngine.pack(initialOrder))
    val layout: StateFlow<List<HomeTilePlacement>> = _layout.asStateFlow()

    fun setOrder(next: List<HomeTileSpec>) {
        prefs.edit().putString(KEY_LAYOUT, json.encodeToString(serializer, next)).apply()
        _order.value = next
        _layout.value = HomeLayoutEngine.pack(next)
    }

    fun resetToDefault() = setOrder(HomeLayoutEngine.defaultOrder())

    private fun readPersisted(): List<HomeTileSpec>? =
        prefs.getString(KEY_LAYOUT, null)?.let { stored ->
            runCatching { json.decodeFromString(serializer, stored) }.getOrNull()
        }
}
