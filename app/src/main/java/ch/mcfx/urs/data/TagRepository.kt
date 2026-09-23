package ch.mcfx.urs.data

import ch.mcfx.urs.data.remote.TagDto
import ch.mcfx.urs.data.remote.UrsApi
import kotlinx.coroutines.CancellationException

/**
 * Read-only cache of the caller's full shared tag pool (mcfx-urs/urs-backend#11:
 * Notes and Kanban tags are the same per-user pool now) — [suggest] answers
 * tag-input autocomplete for both features from this in-memory snapshot
 * instead of a network call per keystroke, replacing the old per-feature
 * local-DB-derived suggestion queries (which only ever knew about tags on
 * already-fetched notes/cards). [refreshFromBackend] is wired into
 * [ch.mcfx.urs.data.sync.PullCoordinator] like every other domain. A tag
 * created moments ago (before the next pull) simply won't suggest yet —
 * the same eventually-consistent tradeoff already accepted elsewhere in
 * this offline-first app.
 */
class TagRepository(private val api: UrsApi) {

    @Volatile
    private var cachedTags: List<TagDto> = emptyList()

    fun suggest(query: String): List<String> =
        cachedTags.map { it.name }
            .filter { it.contains(query, ignoreCase = true) }
            .sorted()
            .take(10)

    suspend fun refreshFromBackend(): Boolean = try {
        cachedTags = api.getTags()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Best-effort only, same shape as every other refreshFromBackend in this app.
        android.util.Log.w("TagRepository", "refreshFromBackend failed", e)
        false
    }
}
