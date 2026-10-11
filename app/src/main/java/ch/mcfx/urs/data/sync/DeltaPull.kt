package ch.mcfx.urs.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Delta-pull response of a list endpoint called with `since` (see urs-backend `SyncDelta`). */
@Serializable
data class DeltaDto<T>(
    @SerialName("items") val items: List<T> = emptyList(),
    @SerialName("deleted_ids") val deletedIds: List<String> = emptyList(),
    @SerialName("cursor") val cursor: String,
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("full") val full: Boolean = false,
)

const val INITIAL_DELTA_CURSOR = "0"

/**
 * One delta pull of a domain: asks the backend for everything changed after the stored cursor and
 * applies it. A full pull happens without a stored cursor, when the local table is empty
 * ([hasLocalData] false) or when the server answers `full`. The cursor is stored only after the
 * apply succeeded, so a failed pull is retried from the same cursor.
 *
 * @param applyFull receives the complete list; reconcile the local rows against it (rows missing
 *   from it are gone on the server).
 * @param applyDelta receives only the changed items and the ids that no longer exist; it must
 *   neither delete other rows nor touch rows that are not yet pushed.
 */
suspend fun <T> PullCursorStore.pullDelta(
    domain: String,
    hasLocalData: suspend () -> Boolean,
    fetch: suspend (since: String) -> DeltaDto<T>,
    applyFull: suspend (List<T>) -> Unit,
    applyDelta: suspend (items: List<T>, deletedIds: List<String>) -> Unit,
) {
    val stored = get(domain)
    val since = if (stored != null && hasLocalData()) stored else INITIAL_DELTA_CURSOR
    val page = fetch(since)
    if (page.full) applyFull(page.items) else applyDelta(page.items, page.deletedIds)
    set(domain, page.cursor)
}
