package ch.mcfx.urs.data

import ch.mcfx.urs.data.local.CatalogCategoryDao
import ch.mcfx.urs.data.local.CatalogProductDao
import ch.mcfx.urs.data.local.CatalogProductEntity
import ch.mcfx.urs.data.local.ListDao
import ch.mcfx.urs.data.local.ListEntity
import ch.mcfx.urs.data.local.ListItemDao
import ch.mcfx.urs.data.local.ListItemEntity
import ch.mcfx.urs.data.local.OutboxDao
import ch.mcfx.urs.data.local.OutboxListDeletePayload
import ch.mcfx.urs.data.local.OutboxListItemDeletePayload
import ch.mcfx.urs.data.local.OutboxListItemPayload
import ch.mcfx.urs.data.local.OutboxListItemUpdatePayload
import ch.mcfx.urs.data.local.OutboxListPayload
import ch.mcfx.urs.data.local.OutboxListUpdatePayload
import ch.mcfx.urs.data.local.OutboxMutationEntity
import ch.mcfx.urs.data.local.RecentlyUsedProductDao
import ch.mcfx.urs.data.local.RecentlyUsedProductEntity
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.data.remote.ListDto
import ch.mcfx.urs.data.remote.ListItemDto
import ch.mcfx.urs.data.remote.ListSharePayload
import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.data.sync.SyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One list item joined with the product/category names it's displayed with — see [ShoppingListRepository.observeItems]. */
data class ShoppingListItemDetail(
    val item: ListItemEntity,
    val productName: String,
    val categoryName: String,
    val catalogImageId: Int?,
)

/**
 * Offline-first write path for shopping lists and their items — same shape
 * as [InventoryRepository], with full create/update/delete outbox coverage
 * (see [ListEntity]'s doc comment). Unlike before , this repository
 * has no dependency on [InventoryRepository]/inventory data at all: a list
 * item now points directly at a shared `catalog_product` (see
 * [CatalogRepository]), and adding something to a list never creates or
 * touches an inventory product — core rule ("Inventar-Management
 * ist ein manueller Prozess").
 */
class ShoppingListRepository(
    private val api: UrsApi,
    private val listDao: ListDao,
    private val listItemDao: ListItemDao,
    private val catalogProductDao: CatalogProductDao,
    private val catalogCategoryDao: CatalogCategoryDao,
    private val recentlyUsedProductDao: RecentlyUsedProductDao,
    private val outboxDao: OutboxDao,
    private val syncManager: SyncManager,
    private val applicationScope: CoroutineScope,
    private val json: Json,
) {

    fun observeLists(): Flow<List<ListEntity>> = listDao.observeAll().map { it.sortedBy { l -> l.name.alphabeticSortKey() } }

    suspend fun getList(localId: Long): ListEntity? = listDao.getById(localId)

    /**
     * Joined item view for one list's detail screen: [ListItemEntity] plus
     * the product/category names it's rendered with, resolved against the
     * cached [CatalogProductEntity]/[ch.mcfx.urs.data.local.CatalogCategoryEntity]
     * by [ListItemEntity.catalogProductId] — always a real id (see that
     * field's own doc comment), so unlike the pre- shape this
     * replaces, no local-id fallback/stand-in resolution is needed here: an
     * item whose catalog product isn't cached locally yet (a cold start
     * before [ch.mcfx.urs.data.CatalogRepository.refreshFromBackend] has run
     * once) simply doesn't show until that cache warms up, the same
     * "product must already be cached" precondition catalog search already
     * has everywhere else.
     */
    fun observeItems(listId: String): Flow<List<ShoppingListItemDetail>> =
        combine(
            listItemDao.observeByList(listId),
            catalogProductDao.observeAll(),
            catalogCategoryDao.observeAll(),
        ) { items, products, categories ->
            val productsById = products.associateBy { it.id }
            val categoriesById = categories.associateBy { it.id }
            items.mapNotNull { item ->
                val product = productsById[item.catalogProductId] ?: return@mapNotNull null
                ShoppingListItemDetail(
                    item = item,
                    productName = product.name,
                    categoryName = product.catalogCategoryId?.let { categoriesById[it]?.name }.orEmpty(),
                    catalogImageId = product.catalogImageId,
                )
            }
        }

    /** Same catalog-joined shape as [observeItems], for the "recently used" tail section / AddProductScreen's "Zuletzt" tab. */
    fun observeRecentlyUsed(): Flow<List<CatalogProductEntity>> =
        combine(recentlyUsedProductDao.observeAll(), catalogProductDao.observeAll()) { recents, products ->
            val productsById = products.associateBy { it.id }
            recents.mapNotNull { productsById[it.catalogProductId] }
        }

    /**
     * Offline-first write path — same shape as [InventoryRepository
     * .createInventory]: both writes below are local-only and instant, a
     * background sync attempt is fired immediately afterwards but never
     * awaited here.
     */
    suspend fun createList(name: String) {
        val payload = OutboxListPayload(name = name)
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_LIST,
                payloadJson = json.encodeToString(payload),
                createdAt = System.currentTimeMillis(),
            ),
        )
        listDao.upsert(ListEntity(outboxId = outboxId, name = name, syncStatus = SyncStatus.PENDING))
        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Offline-first rename — same "rewrite the pending create in place if
     * not yet synced, otherwise cancel-and-requeue an update" shape as
     * [WorkTimeRepository.updateEntry].
     */
    suspend fun renameList(localId: Long, name: String) {
        val current = listDao.getById(localId) ?: return

        val outboxId = if (current.serverId == null) {
            val payload = OutboxListPayload(name = name)
            current.outboxId?.let { outboxDao.updatePayload(it, json.encodeToString(payload)) }
            current.outboxId
        } else {
            current.outboxId?.let { outboxDao.delete(it) }
            val payload = OutboxListUpdatePayload(serverId = current.serverId, name = name)
            outboxDao.insert(
                OutboxMutationEntity(
                    type = OutboxMutationEntity.TYPE_UPDATE_LIST,
                    payloadJson = json.encodeToString(payload),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }

        listDao.updateFields(localId, name, SyncStatus.PENDING, outboxId)
        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Offline-first delete — same shape as [WorkTimeRepository.deleteEntry].
     * The list's own items are removed locally too (Room has no
     * cross-entity cascade — see [ch.mcfx.urs.data.local.ListItemDao
     * .deleteByListId]'s doc comment), and any of their own still-pending
     * mutations cancelled first, since they'd otherwise try to create/update
     * an item against a list that's about to stop existing server-side.
     */
    suspend fun deleteList(localId: Long) {
        val current = listDao.getById(localId) ?: return
        val publicId = current.publicId

        listItemDao.getByListId(publicId).forEach { item -> item.outboxId?.let { outboxDao.delete(it) } }
        listItemDao.deleteByListId(publicId)

        current.outboxId?.let { outboxDao.delete(it) }
        listDao.delete(localId)

        val serverId = current.serverId ?: return
        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_DELETE_LIST,
                payloadJson = json.encodeToString(OutboxListDeletePayload(serverId = serverId)),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    // Sharing management is a direct network call, no outbox — same shape
    // as InventoryRepository's share methods.
    suspend fun shareList(listId: String, userId: String) {
        api.shareList(listId, ListSharePayload(userId = userId))
    }

    suspend fun getListShares(listId: String): List<ShareEntry> =
        emptyAsNull { api.getListShares(listId) }.map { ShareEntry(userId = it.userId) }

    suspend fun removeListShare(listId: String, userId: String) {
        api.deleteListShare(listId, userId)
    }

    /**
     * Offline-first write path — queues a [OutboxMutationEntity
     * .TYPE_CREATE_LIST_ITEM] mutation plus the local mirror row, same shape
     * as [createList]. [listId] is the parent list's `publicId` (a real
     * backend id, or a not-yet-synced stand-in — see `SyncManager`).
     * [catalogProductId] is always a real `catalog_product` id ( —
     * see [ListItemEntity]'s doc comment). No dedup against an existing row
     * for the same product on this list — the backend itself allows the
     * same product to appear multiple times, distinguished only by note
     * (see `urs-backend`'s `00014_add_list_tables.sql`), so adding the same
     * product twice with two different notes is expected to create two
     * separate rows, not merge them.
     */
    suspend fun addExistingProduct(listId: String, catalogProductId: String, note: String?) {
        val payload = OutboxListItemPayload(listId = listId, catalogProductId = catalogProductId, note = note)
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_LIST_ITEM,
                payloadJson = json.encodeToString(payload),
                createdAt = System.currentTimeMillis(),
            ),
        )
        listItemDao.upsert(
            ListItemEntity(
                outboxId = outboxId, listId = listId, catalogProductId = catalogProductId, note = note,
                syncStatus = SyncStatus.PENDING,
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Adds a predefined catalog product to a list — just forwards
     * to [addExistingProduct], nothing more: unlike the pre- shape
     * this replaces, this never creates or reuses an inventory product.
     * "Recently used" bumps itself server-side (the backend's
     * `InsertListItem` logs `list_item_usage` in the same transaction as
     * creating the list item) — no separate client-side call needed beyond
     * re-fetching [refreshRecentlyUsed] on next load.
     */
    suspend fun addCatalogProduct(listId: String, catalogProduct: CatalogProductEntity, note: String?) {
        addExistingProduct(listId, catalogProduct.id, note)
    }

    /**
     * Offline-first update — same "rewrite the pending create in place if
     * not yet synced, otherwise cancel-and-requeue an update" shape as
     * [renameList]/[WorkTimeRepository.updateEntry]. `checked` is gone
     * entirely — this now only ever edits the note.
     */
    suspend fun updateItem(localId: Long, note: String?) {
        val current = listItemDao.getById(localId) ?: return

        val outboxId = if (current.serverId == null) {
            val payload = OutboxListItemPayload(listId = current.listId, catalogProductId = current.catalogProductId, note = note)
            current.outboxId?.let { outboxDao.updatePayload(it, json.encodeToString(payload)) }
            current.outboxId
        } else {
            current.outboxId?.let { outboxDao.delete(it) }
            val payload = OutboxListItemUpdatePayload(serverId = current.serverId, note = note)
            outboxDao.insert(
                OutboxMutationEntity(
                    type = OutboxMutationEntity.TYPE_UPDATE_LIST_ITEM,
                    payloadJson = json.encodeToString(payload),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }

        listItemDao.updateFields(localId, note, SyncStatus.PENDING, outboxId)
        applicationScope.launch { syncManager.syncNow() }
    }

    /** Offline-first delete — same shape as [deleteList]. */
    suspend fun deleteItem(localId: Long) {
        val current = listItemDao.getById(localId) ?: return
        current.outboxId?.let { outboxDao.delete(it) }
        listItemDao.delete(localId)

        val serverId = current.serverId ?: return
        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_DELETE_LIST_ITEM,
                payloadJson = json.encodeToString(OutboxListItemDeletePayload(serverId = serverId)),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Opportunistic backend refresh, same best-effort shape as
     * [InventoryRepository.refreshFromBackend]: run when reachable, never
     * blocking the caller or surfacing an error on failure. Items are only
     * refreshed per list already known locally, matching the backend's own
     * per-list endpoint granularity. Never touches PENDING/FAILED rows,
     * which exist solely via the outbox replay path above.
     */
    suspend fun refreshFromBackend() {
        refreshQuietly {
            val lists = emptyAsNull { api.getLists() }
            listDao.upsertFromServer(lists.map { it.toEntity() })
        }
        listDao.observeAll().first().mapNotNull { it.serverId }.forEach { listId ->
            refreshQuietly {
                val items = emptyAsNull { api.getListItems(listId) }
                listItemDao.upsertFromServer(items.map { it.toEntity() })
            }
        }
    }

    /**
     * Opportunistic backend refresh for the "recently used" cache — full
     * replace on every call (see [RecentlyUsedProductEntity]'s doc comment),
     * same best-effort shape as [refreshFromBackend].
     */
    suspend fun refreshRecentlyUsed() {
        refreshQuietly {
            val products = emptyAsNull { api.getRecentlyUsedProducts() }
            recentlyUsedProductDao.replaceAll(
                products.mapIndexed { index, dto ->
                    RecentlyUsedProductEntity(catalogProductId = dto.catalogProductId, lastUsedAt = dto.lastUsedAt, rank = index)
                },
            )
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

private fun ListDto.toEntity() = ListEntity(
    serverId = id,
    outboxId = null,
    name = name,
    syncStatus = SyncStatus.SYNCED,
)

private fun ListItemDto.toEntity() = ListItemEntity(
    serverId = id,
    outboxId = null,
    listId = listId,
    catalogProductId = catalogProductId,
    note = note.ifEmpty { null },
    syncStatus = SyncStatus.SYNCED,
)
