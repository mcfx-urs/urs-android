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
 * (see [ListEntity]'s doc comment). This repository has no dependency on
 * [InventoryRepository]/inventory data at all: a list item points directly
 * at a shared `catalog_product` (see [CatalogRepository]), and adding
 * something to a list never creates or touches an inventory product
 * ("Inventar-Management ist ein manueller Prozess").
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

    /** Home-tile quick-jump badges — local-only preference, never synced to the backend. */
    fun observeFavoriteLists(): Flow<List<ListEntity>> = listDao.observeFavorites()

    suspend fun setListFavorite(localId: Long, isFavorite: Boolean) = listDao.setFavorite(localId, isFavorite)

    /** Chosen icon id from IconCatalog, or null to clear — local-only, never synced. */
    suspend fun setListIconId(localId: Long, iconId: String?) = listDao.setIconId(localId, iconId)

    suspend fun getList(localId: Long): ListEntity? = listDao.getById(localId)

    /**
     * Joined item view for one list's detail screen: [ListItemEntity] plus
     * the product/category names it's rendered with, resolved against the
     * cached [CatalogProductEntity]/[ch.mcfx.urs.data.local.CatalogCategoryEntity]
     * by [ListItemEntity.catalogProductId] — always a real id (see that
     * field's own doc comment), so no local-id fallback/stand-in resolution
     * is needed here: an item whose catalog product isn't cached locally
     * yet (a cold start
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
    // Excludes products already on this list — once a recently-used product
    // gets added (back) onto the list, it should disappear from this feed
    // rather than staying listed as if it still needed picking again; this
    // combine reacts to observeItems' own list membership Flow, so it
    // updates immediately, no manual "hide it" bookkeeping needed.
    fun observeRecentlyUsed(listId: String): Flow<List<CatalogProductEntity>> =
        combine(
            recentlyUsedProductDao.observeForList(listId),
            catalogProductDao.observeAll(),
            listItemDao.observeByList(listId),
        ) { recents, products, currentItems ->
            val productsById = products.associateBy { it.id }
            val currentProductIds = currentItems.map { it.catalogProductId }.toSet()
            recents.mapNotNull { productsById[it.catalogProductId] }.filterNot { it.id in currentProductIds }
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
     * [catalogProductId] is always a real `catalog_product` id (see
     * [ListItemEntity]'s doc comment). No dedup against an existing row
     * for the same product on this list — the backend itself allows the
     * same product to appear multiple times, distinguished only by note
     * (see `urs-backend`'s `00014_add_list_tables.sql`), so adding the same
     * product twice with two different notes is expected to create two
     * separate rows, not merge them.
     */
    /** Returns the new item's local row id — lets a caller undo the add via [deleteItem]. */
    suspend fun addExistingProduct(
        listId: String,
        catalogProductId: String,
        note: String?,
        quantity: Int? = null,
        onSale: Boolean = false,
    ): Long {
        val payload = OutboxListItemPayload(
            listId = listId, catalogProductId = catalogProductId, note = note,
            quantity = quantity, onSale = onSale,
        )
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_LIST_ITEM,
                payloadJson = json.encodeToString(payload),
                createdAt = System.currentTimeMillis(),
            ),
        )
        val localId = listItemDao.upsert(
            ListItemEntity(
                outboxId = outboxId, listId = listId, catalogProductId = catalogProductId, note = note,
                quantity = quantity, onSale = onSale,
                syncStatus = SyncStatus.PENDING,
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
        return localId
    }

    /**
     * Adds a predefined catalog product to a list — just forwards
     * to [addExistingProduct], nothing more: this never creates or reuses
     * an inventory product.
     * "Recently used" bumps itself server-side (the backend's
     * `InsertListItem` logs `list_item_usage` in the same transaction as
     * creating the list item) — no separate client-side call needed beyond
     * re-fetching [refreshRecentlyUsed] on next load.
     */
    suspend fun addCatalogProduct(
        listId: String,
        catalogProduct: CatalogProductEntity,
        note: String?,
        quantity: Int? = null,
        onSale: Boolean = false,
    ): Long = addExistingProduct(listId, catalogProduct.id, note, quantity, onSale)

    /**
     * Offline-first update — same "rewrite the pending create in place if
     * not yet synced, otherwise cancel-and-requeue an update" shape as
     * [renameList]/[WorkTimeRepository.updateEntry]. `checked` is gone
     * entirely — this now only ever edits the note.
     */
    suspend fun updateItem(localId: Long, note: String?, quantity: Int? = null, onSale: Boolean = false) {
        val current = listItemDao.getById(localId) ?: return

        val outboxId = if (current.serverId == null) {
            val payload = OutboxListItemPayload(
                listId = current.listId, catalogProductId = current.catalogProductId, note = note,
                quantity = quantity, onSale = onSale,
            )
            current.outboxId?.let { outboxDao.updatePayload(it, json.encodeToString(payload)) }
            current.outboxId
        } else {
            current.outboxId?.let { outboxDao.delete(it) }
            val payload = OutboxListItemUpdatePayload(
                serverId = current.serverId, note = note, quantity = quantity, onSale = onSale,
            )
            outboxDao.insert(
                OutboxMutationEntity(
                    type = OutboxMutationEntity.TYPE_UPDATE_LIST_ITEM,
                    payloadJson = json.encodeToString(payload),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }

        listItemDao.updateFields(localId, note, quantity, onSale, SyncStatus.PENDING, outboxId)
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
     * per-list endpoint granularity.
     *
     * Drains the outbox first ([SyncManager.syncNow]) so any still-queued
     * delete has actually landed server-side before the pull runs —
     * otherwise the pull would return, and [ListItemDao.upsertFromServer]
     * would re-insert, the very row that delete is about to remove (the
     * item/list "reappears after offline deletion" bug). `syncNow()` is
     * `mutex.withLock { replayOutbox() }` — idempotent on an empty outbox
     * and never throwing — so calling it unconditionally here is safe.
     * The pull then also removes local SYNCED rows the server no longer
     * returns, reconciling away a delete made on another device (or one
     * that slipped through before this ordering existed) rather than
     * letting it linger forever. PENDING/FAILED rows are never touched —
     * they exist solely via the outbox replay path above.
     */
    suspend fun refreshFromBackend() {
        syncManager.syncNow()
        refreshQuietly {
            val lists = emptyAsNull { api.getLists() }
            listDao.upsertFromServer(lists.map { it.toEntity() })
        }
        listDao.observeAll().first().mapNotNull { it.serverId }.forEach { listId ->
            refreshQuietly {
                val items = emptyAsNull { api.getListItems(listId) }
                listItemDao.upsertFromServer(listId, items.map { it.toEntity() })
            }
        }
    }

    /**
     * Opportunistic backend refresh for the "recently used" cache, scoped to
     * one list — full replace of that list's own rows on every call (see
     * [RecentlyUsedProductEntity]'s doc comment), same best-effort shape as
     * [refreshFromBackend].
     */
    suspend fun refreshRecentlyUsed(listId: String) {
        refreshQuietly {
            val products = emptyAsNull { api.getRecentlyUsedProducts(listId) }
            recentlyUsedProductDao.replaceForList(
                listId,
                products.mapIndexed { index, dto ->
                    RecentlyUsedProductEntity(
                        catalogProductId = dto.catalogProductId,
                        listId = listId,
                        lastUsedAt = dto.lastUsedAt,
                        rank = index,
                    )
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
    quantity = quantity,
    onSale = onSale,
    syncStatus = SyncStatus.SYNCED,
)
