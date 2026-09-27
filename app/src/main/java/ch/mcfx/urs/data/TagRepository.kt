package ch.mcfx.urs.data

import ch.mcfx.urs.data.remote.TagColorUpdatePayload
import ch.mcfx.urs.data.remote.TagDto
import ch.mcfx.urs.data.remote.UrsApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
 * this offline-first app. [tags] additionally exposes the full pool
 * reactively for the Settings → Tags screen (mcfx-urs/urs-android#103).
 */
class TagRepository(private val api: UrsApi) {

    private val _tags = MutableStateFlow<List<TagDto>>(emptyList())
    val tags: StateFlow<List<TagDto>> = _tags.asStateFlow()

    fun suggest(query: String): List<String> =
        _tags.value.map { it.name }
            .filter { it.contains(query, ignoreCase = true) }
            .sorted()
            .take(10)

    suspend fun refreshFromBackend(): Boolean = try {
        _tags.value = api.getTags()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Best-effort only, same shape as every other refreshFromBackend in this app.
        android.util.Log.w("TagRepository", "refreshFromBackend failed", e)
        false
    }

    /**
     * Recolor an existing tag (mcfx-urs/urs-android#103) — a direct,
     * synchronous REST call, same shape as [CatalogRepository]'s manual
     * create/update calls, updating the in-memory pool from the request's
     * own input rather than re-fetching.
     */
    suspend fun updateColor(name: String, color: String) {
        api.updateTagColor(name, TagColorUpdatePayload(color = color))
        _tags.update { list -> list.map { if (it.name == name) it.copy(color = color) else it } }
    }
}
