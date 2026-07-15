package ch.mcfx.urs.data

import ch.mcfx.urs.data.local.CatalogProductDao
import ch.mcfx.urs.data.local.CatalogProductEntity
import ch.mcfx.urs.data.remote.CatalogProductDto
import ch.mcfx.urs.data.remote.UrsApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException

/**
 * Read-only Room cache of the predefined product catalog — separate from
 * [InventoryRepository] since it's a different data lifecycle: server-
 * authoritative reference data the client never writes to, so there's no
 * outbox involvement at all, just a plain read-through cache populated by
 * [refreshFromBackend].
 */
class CatalogRepository(
    private val api: UrsApi,
    private val catalogProductDao: CatalogProductDao,
) {

    fun observeAll(): Flow<List<CatalogProductEntity>> = catalogProductDao.observeAll()

    fun search(query: String): Flow<List<CatalogProductEntity>> = catalogProductDao.search(query)

    /**
     * Opportunistic backend refresh for the Room-cached catalog — same
     * best-effort shape as [InventoryRepository.refreshFromBackend]: run
     * when reachable, never blocking the caller or surfacing an error on
     * failure. A failed refresh just leaves the cache stale until the next
     * successful call.
     */
    suspend fun refreshFromBackend() {
        refreshQuietly {
            val products = emptyAsNull { api.getCatalogProducts() }
            catalogProductDao.upsertAll(products.map { it.toEntity() })
        }
    }

    private suspend fun refreshQuietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort only — see refreshFromBackend's doc comment.
        }
    }

    // The backend encodes empty result sets as JSON `null` instead of `[]`.
    private suspend fun <T> emptyAsNull(call: suspend () -> List<T>): List<T> =
        try {
            call()
        } catch (_: SerializationException) {
            emptyList()
        }
}

private fun CatalogProductDto.toEntity() = CatalogProductEntity(
    id = id,
    categoryName = categoryName,
    name = name,
    searchTerms = searchTerms.ifEmpty { null },
    brands = brands.ifEmpty { null },
    popularityIndex = popularityIndex.toIntOrNull(),
    catalogImageId = catalogImageId.toIntOrNull(),
)
