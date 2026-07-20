package ch.mcfx.urs.data

import ch.mcfx.urs.data.local.CatalogCategoryDao
import ch.mcfx.urs.data.local.CatalogCategoryEntity
import ch.mcfx.urs.data.local.CatalogProductDao
import ch.mcfx.urs.data.local.CatalogProductEntity
import ch.mcfx.urs.data.remote.CatalogCategoryDto
import ch.mcfx.urs.data.remote.CatalogCategoryUpdatePayload
import ch.mcfx.urs.data.remote.CatalogImageDto
import ch.mcfx.urs.data.remote.CatalogProductDto
import ch.mcfx.urs.data.remote.CatalogProductUpdatePayload
import ch.mcfx.urs.data.remote.NewCatalogCategoryPayload
import ch.mcfx.urs.data.remote.NewCatalogProductPayload
import ch.mcfx.urs.data.remote.UrsApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/**
 * Read-only Room cache of the shared/public product catalog —
 * separate from [InventoryRepository]/[ShoppingListRepository] since it's a
 * different data lifecycle: server-authoritative reference data neither of
 * those write to directly, so there's no outbox involvement for the bulk of
 * this class, just a plain read-through cache populated by
 * [refreshFromBackend]. [createCategory]/[createProduct] are the one
 * exception — direct, synchronous REST calls (not offline-first) for the
 * "type a new name" path either Inventory or Shopping List can trigger; see
 * their own doc comments for why that's a deliberate deviation from this
 * app's usual offline-first shape.
 */
class CatalogRepository(
    private val api: UrsApi,
    private val catalogProductDao: CatalogProductDao,
    private val catalogCategoryDao: CatalogCategoryDao,
) {

    fun observeAll(): Flow<List<CatalogProductEntity>> = catalogProductDao.observeAll()

    fun search(query: String): Flow<List<CatalogProductEntity>> = catalogProductDao.search(query)

    fun observeByCategory(categoryId: String): Flow<List<CatalogProductEntity>> = catalogProductDao.observeByCategory(categoryId)

    fun observeMostPopular(limit: Int = 50): Flow<List<CatalogProductEntity>> = catalogProductDao.observeMostPopular(limit)

    fun observeCategories(): Flow<List<CatalogCategoryEntity>> =
        catalogCategoryDao.observeAll()

    /**
     * Opportunistic backend refresh for the Room-cached catalog — same
     * best-effort shape as [InventoryRepository.refreshFromBackend]: run
     * when reachable, never blocking the caller or surfacing an error on
     * failure. A failed refresh just leaves the cache stale until the next
     * successful call. Refreshes both products and categories together —
     * either screen that needs one of the two (Inventory, Shopping List)
     * ends up warming both, so neither ever waits on a second trigger.
     */
    suspend fun refreshFromBackend() {
        refreshQuietly {
            val products = emptyAsNull { api.getCatalogProducts() }
            catalogProductDao.upsertAll(products.map { it.toEntity() })
        }
        refreshQuietly {
            val categories = emptyAsNull { api.getCatalogCategories() }
            catalogCategoryDao.upsertAll(categories.map { it.toEntity() })
        }
    }

    /**
     * Manual "type a new category name" path — a direct,
     * synchronous REST call, not offline-first: unlike inventory/list
     * containers, `catalog_category` is shared/public reference data, so
     * creating one requires being online (idempotent by name server-side,
     * see `urs-backend`'s `GetOrCreateCatalogCategory`). The freshly
     * created-or-matched row is upserted into the local cache immediately so
     * it's visible without waiting on the next [refreshFromBackend].
     */
    suspend fun createCategory(name: String): CatalogCategoryEntity {
        val response = api.createCatalogCategory(NewCatalogCategoryPayload(name = name))
        // Always "manual" — postCatalogCategory only ever inserts manual rows
        // (urs-backend's GetOrCreateCatalogCategory), so no need to round-trip
        // through the DTO's source field just to learn what we already know.
        val entity = CatalogCategoryEntity(id = response.id, name = response.name, source = "manual")
        catalogCategoryDao.upsertOne(entity)
        return entity
    }

    /** Same "direct, synchronous, shared-pool" shape as [createCategory]. */
    suspend fun createProduct(name: String, catalogCategoryId: String?): CatalogProductEntity {
        val response = api.createCatalogProduct(
            NewCatalogProductPayload(name = name, catalogCategoryId = catalogCategoryId.orEmpty()),
        )
        val entity = CatalogProductEntity(
            id = response.id,
            categoryName = "",
            catalogCategoryId = response.catalogCategoryId.ifEmpty { null },
            name = response.name,
            source = "manual",
        )
        catalogProductDao.upsertOne(entity)
        return entity
    }

    /**
     * rename/re-link a manually-created product (name/category/
     * image) — Product Management's edit form. Same direct-REST shape as
     * [createProduct]; the local cache is updated from the request's own
     * inputs rather than re-fetching, mirroring how create already works.
     */
    suspend fun updateProduct(id: String, name: String, catalogCategoryId: String?, catalogImageId: String?) {
        api.updateCatalogProduct(
            id,
            CatalogProductUpdatePayload(
                name = name,
                catalogCategoryId = catalogCategoryId.orEmpty(),
                catalogImageId = catalogImageId.orEmpty(),
            ),
        )
        catalogProductDao.updateFields(id, name, catalogCategoryId, catalogImageId?.toIntOrNull())
    }

    /** delete a manually-created product — Product Management's delete action. */
    suspend fun deleteProduct(id: String) {
        api.deleteCatalogProduct(id)
        catalogProductDao.deleteById(id)
    }

    /** rename/re-link a manually-created category — Product Management's edit form. */
    suspend fun updateCategory(id: String, name: String, catalogImageId: String?) {
        api.updateCatalogCategory(id, CatalogCategoryUpdatePayload(name = name, catalogImageId = catalogImageId.orEmpty()))
        catalogCategoryDao.updateFields(id, name, catalogImageId?.toIntOrNull())
    }

    /** delete a manually-created category — Product Management's delete action. */
    suspend fun deleteCategory(id: String) {
        api.deleteCatalogCategory(id)
        catalogCategoryDao.deleteById(id)
    }

    /**
     * every catalog image available for reuse — no local cache (a
     * fresh network read every time the picker opens), same reasoning as
     * [quantityOnHand]: this list only matters while the picker is open, not
     * worth an offline-first shape.
     */
    suspend fun getImages(): List<CatalogImageDto> = emptyAsNull { api.getCatalogImages() }

    /**
     * Read-only display hint — the caller's total on-hand quantity
     * of [catalogProductId] across every inventory they can access, or
     * `null` if untracked anywhere accessible. Always a fresh network read,
     * same "no meaningful local cache for this" reasoning as
     * [InventoryRepository.getProductQuantity] — never write-coupled to the
     * shopping list.
     */
    suspend fun quantityOnHand(catalogProductId: String): Int? {
        val response = try {
            api.getQuantityOnHand(catalogProductId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: HttpException) {
            return null
        } catch (_: Exception) {
            return null
        }
        if (!response.isSuccessful) return null
        return response.body()?.quantity?.toIntOrNull()
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
    catalogCategoryId = catalogCategoryId.ifEmpty { null },
    name = name,
    searchTerms = searchTerms.ifEmpty { null },
    brands = brands.ifEmpty { null },
    popularityIndex = popularityIndex.toIntOrNull(),
    catalogImageId = catalogImageId.toIntOrNull(),
    recentNote1 = recentNote1.ifEmpty { null },
    recentNote2 = recentNote2.ifEmpty { null },
    recentNote3 = recentNote3.ifEmpty { null },
    source = source,
)

private fun CatalogCategoryDto.toEntity() = CatalogCategoryEntity(
    id = id,
    name = name,
    source = source,
    catalogImageId = catalogImageId.toIntOrNull(),
)
