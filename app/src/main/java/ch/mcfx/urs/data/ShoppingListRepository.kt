package ch.mcfx.urs.data

import ch.mcfx.urs.data.local.CatalogProductEntity
import ch.mcfx.urs.data.local.InventoryCategoryDao
import ch.mcfx.urs.data.local.InventoryProductDao
import ch.mcfx.urs.data.local.InventoryProductEntity
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
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.data.local.localIdStandIn
import ch.mcfx.urs.data.local.localInventoryProductId
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.data.remote.ListDto
import ch.mcfx.urs.data.remote.ListItemDto
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
)

/**
 * Offline-first write path for shopping lists and their items — same shape
 * as [InventoryRepository], but with full create/update/delete outbox
 * coverage (see [ListEntity]'s doc comment for why lists/items need that
 * where inventory categories/products didn't in that earlier phase).
 * Depends on [InventoryRepository]/[InventoryCategoryDao] directly rather
 * than through another layer of indirection — the only two repositories in
 * this app with a real cross-repository dependency, since adding a catalog
 * product to a list is genuinely also an inventory-product create (see
 * [addCatalogProduct]).
 */
class ShoppingListRepository(
    private val api: UrsApi,
    private val listDao: ListDao,
    private val listItemDao: ListItemDao,
    private val inventoryProductDao: InventoryProductDao,
    private val inventoryCategoryDao: InventoryCategoryDao,
    private val inventoryRepository: InventoryRepository,
    private val outboxDao: OutboxDao,
    private val syncManager: SyncManager,
    private val applicationScope: CoroutineScope,
    private val json: Json,
) {

    fun observeLists(): Flow<List<ListEntity>> = listDao.observeAll().map { it.sortedBy { l -> l.name.alphabeticSortKey() } }

    suspend fun getList(localId: Long): ListEntity? = listDao.getById(localId)

    /**
     * Joined item view for one list's detail screen: [ListItemEntity] plus
     * the product/category names it's rendered with. Built by combining
     * this app's existing per-entity Flows in Kotlin rather than a
     * cross-table SQL join — [ListItemEntity.productId] is a [publicId]
     * string (a real backend id, or a not-yet-synced local stand-in), the
     * same string-based indirection [InventoryProductEntity.categoryId]
     * already uses and resolves at the Kotlin/DAO level (not via SQL) — see
     * `SyncManager`'s `resolveProductId`/`localInventoryCategoryId`. This
     * follows that existing precedent instead of introducing a first
     * raw-SQL join for a relationship the rest of the app already resolves
     * the other way.
     *
     * Matching is keyed by [InventoryProductEntity.publicId] with a fallback
     * to the product's stable local row id, not [InventoryProductEntity
     * .publicId] alone: [ListItemEntity.productId] is only corrected to a
     * product's real server id once *this specific list item's own* create
     * mutation has replayed (see `SyncManager.replayCreateListItem`) — but
     * the referenced product can independently sync (and so have its own
     * `publicId` change from a local stand-in to a real server id) first,
     * e.g. while this item's create is still blocked on its *parent list*
     * not having synced yet. Without the local-id fallback, such an item
     * would silently disappear from this list the moment its product synced
     * ahead of it, even though nothing about the item itself changed.
     */
    fun observeItems(listId: String): Flow<List<ShoppingListItemDetail>> =
        combine(
            listItemDao.observeByList(listId),
            inventoryProductDao.observeAll(),
            inventoryCategoryDao.observeAll(),
        ) { items, products, categories ->
            val productsByPublicId = products.associateBy { it.publicId }
            val productsByLocalId = products.associateBy { it.id }
            val categoriesById = categories.associateBy { it.publicId }
            items.mapNotNull { item ->
                val product = productsByPublicId[item.productId]
                    ?: localInventoryProductId(item.productId)?.let { productsByLocalId[it] }
                    ?: return@mapNotNull null
                ShoppingListItemDetail(
                    item = item,
                    productName = product.name,
                    categoryName = categoriesById[product.categoryId]?.name.orEmpty(),
                )
            }
        }

    /**
     * Offline-first write path — same shape as [InventoryRepository
     * .createCategory]: both writes below are local-only and instant, a
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

    /**
     * Offline-first write path — queues a [OutboxMutationEntity
     * .TYPE_CREATE_LIST_ITEM] mutation plus the local mirror row, same shape
     * as [createList]. [listId]/[productId] are the parent rows' `publicId`s
     * (real backend ids, or not-yet-synced stand-ins — see `SyncManager`).
     * No dedup against an existing row for the same product on this list —
     * the backend itself allows the same product to appear multiple times,
     * distinguished only by note (see `urs-backend`'s `00014_add_list_tables
     * .sql`), so adding the same product twice with two different notes is
     * expected to create two separate rows, not merge them.
     */
    suspend fun addExistingProduct(listId: String, productId: String, note: String?) {
        val payload = OutboxListItemPayload(listId = listId, productId = productId, note = note)
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_LIST_ITEM,
                payloadJson = json.encodeToString(payload),
                createdAt = System.currentTimeMillis(),
            ),
        )
        listItemDao.upsert(
            ListItemEntity(outboxId = outboxId, listId = listId, productId = productId, note = note, syncStatus = SyncStatus.PENDING),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Adds a predefined catalog product to a list, reusing (rather than
     * duplicating) an existing household [InventoryProductEntity] for the
     * same [CatalogProductEntity.id] if one already exists — see
     * [InventoryProductDao.findByCatalogProductId]. Otherwise creates one
     * first (in the catalog product's own category, resolved-or-created via
     * [resolveOrCreateCategory]), then adds *that* to the list.
     */
    suspend fun addCatalogProduct(listId: String, catalogProduct: CatalogProductEntity, note: String?) {
        val existing = inventoryProductDao.findByCatalogProductId(catalogProduct.id)
        val productId = if (existing != null) {
            existing.publicId
        } else {
            val categoryId = resolveOrCreateCategory(catalogProduct.categoryName)
            val localProductId = inventoryRepository.createProduct(
                categoryId = categoryId,
                name = catalogProduct.name,
                quantity = null,
                catalogProductId = catalogProduct.id,
            )
            localIdStandIn(localProductId)
        }
        addExistingProduct(listId, productId, note)
    }

    /** @return the resolved category's `publicId` — an existing category matched by exact name, or a freshly created one. */
    private suspend fun resolveOrCreateCategory(categoryName: String): String {
        val existing = inventoryCategoryDao.observeAll().first().find { it.name == categoryName }
        if (existing != null) return existing.publicId
        return localIdStandIn(inventoryRepository.createCategory(categoryName))
    }

    /**
     * Offline-first update — same "rewrite the pending create in place if
     * not yet synced, otherwise cancel-and-requeue an update" shape as
     * [renameList]/[WorkTimeRepository.updateEntry]. [note]/[checked] are
     * always edited together, mirroring the backend's own combined
     * `PUT /list-item/{id}` route (see `urs-backend`'s `UpdateListItem`).
     */
    suspend fun updateItem(localId: Long, note: String?, checked: Boolean) {
        val current = listItemDao.getById(localId) ?: return

        val outboxId = if (current.serverId == null) {
            val payload = OutboxListItemPayload(listId = current.listId, productId = current.productId, note = note)
            current.outboxId?.let { outboxDao.updatePayload(it, json.encodeToString(payload)) }
            current.outboxId
        } else {
            current.outboxId?.let { outboxDao.delete(it) }
            val payload = OutboxListItemUpdatePayload(serverId = current.serverId, note = note, checked = checked)
            outboxDao.insert(
                OutboxMutationEntity(
                    type = OutboxMutationEntity.TYPE_UPDATE_LIST_ITEM,
                    payloadJson = json.encodeToString(payload),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }

        listItemDao.updateFields(localId, note, checked, SyncStatus.PENDING, outboxId)
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
    productId = productId,
    note = note.ifEmpty { null },
    checked = checked,
    syncStatus = SyncStatus.SYNCED,
)
