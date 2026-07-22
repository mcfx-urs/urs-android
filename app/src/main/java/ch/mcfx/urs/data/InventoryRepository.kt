package ch.mcfx.urs.data

import ch.mcfx.urs.data.local.InventoryDao
import ch.mcfx.urs.data.local.InventoryEntity
import ch.mcfx.urs.data.local.InventoryProductDao
import ch.mcfx.urs.data.local.InventoryProductEntity
import ch.mcfx.urs.data.local.OutboxDao
import ch.mcfx.urs.data.local.OutboxInventoryDeletePayload
import ch.mcfx.urs.data.local.OutboxInventoryPayload
import ch.mcfx.urs.data.local.OutboxInventoryProductPayload
import ch.mcfx.urs.data.local.OutboxInventoryUpdatePayload
import ch.mcfx.urs.data.local.OutboxMutationEntity
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.data.remote.InventoryDto
import ch.mcfx.urs.data.remote.InventoryPayload
import ch.mcfx.urs.data.remote.InventoryProductDto
import ch.mcfx.urs.data.remote.InventoryProductQuantityPayload
import ch.mcfx.urs.data.remote.InventoryProductSettingsPayload
import ch.mcfx.urs.data.remote.InventorySharePayload
import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.data.sync.SyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Offline-first write path for inventories and their tracked products —
 * same shape as [ShoppingListRepository], with full create/rename/delete
 * outbox coverage for the inventory itself (mirrors
 * [ch.mcfx.urs.data.local.ListEntity]'s doc comment: an inventory is now a
 * named, ownable, shareable, multi-instance container just like a shopping
 * list always was, replacing the old one-inventory-per-user model built on
 * `inventory_category`). Product identity/grouping is resolved entirely
 * through `catalog_product` (see [CatalogRepository]) — this class never
 * touches the shopping list, and vice versa (adding something to a list
 * never touches inventory).
 */
class InventoryRepository(
    private val api: UrsApi,
    private val inventoryDao: InventoryDao,
    private val inventoryProductDao: InventoryProductDao,
    private val outboxDao: OutboxDao,
    private val syncManager: SyncManager,
    private val applicationScope: CoroutineScope,
    private val json: Json,
) {

    fun observeInventories(): Flow<List<InventoryEntity>> =
        inventoryDao.observeAll().map { it.sortedBy { i -> i.name.alphabeticSortKey() } }

    suspend fun getInventory(localId: Long): InventoryEntity? = inventoryDao.getById(localId)

    fun observeProducts(inventoryId: String): Flow<List<InventoryProductEntity>> =
        inventoryProductDao.observeByInventory(inventoryId)

    /**
     * Offline-first write path — the *only* way an inventory gets created,
     * online or offline. Same shape as [ShoppingListRepository.createList]:
     * both writes below (the outbox row and the local [InventoryEntity],
     * status [SyncStatus.PENDING]) are local-only and instant, a background
     * sync attempt is fired immediately afterwards but never awaited here.
     */
    suspend fun createInventory(name: String) {
        val payload = OutboxInventoryPayload(name = name)
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_INVENTORY,
                payloadJson = json.encodeToString(payload),
                createdAt = System.currentTimeMillis(),
            ),
        )
        inventoryDao.upsert(InventoryEntity(outboxId = outboxId, name = name, syncStatus = SyncStatus.PENDING))
        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Offline-first rename — same "rewrite the pending create in place if
     * not yet synced, otherwise cancel-and-requeue an update" shape as
     * [ShoppingListRepository.renameList].
     */
    suspend fun renameInventory(localId: Long, name: String) {
        val current = inventoryDao.getById(localId) ?: return

        val outboxId = if (current.serverId == null) {
            val payload = OutboxInventoryPayload(name = name)
            current.outboxId?.let { outboxDao.updatePayload(it, json.encodeToString(payload)) }
            current.outboxId
        } else {
            current.outboxId?.let { outboxDao.delete(it) }
            val payload = OutboxInventoryUpdatePayload(serverId = current.serverId, name = name)
            outboxDao.insert(
                OutboxMutationEntity(
                    type = OutboxMutationEntity.TYPE_UPDATE_INVENTORY,
                    payloadJson = json.encodeToString(payload),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }

        inventoryDao.updateFields(localId, name, SyncStatus.PENDING, outboxId)
        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Offline-first delete — same shape as [ShoppingListRepository
     * .deleteList]. The inventory's own tracked products are removed
     * locally too (Room has no cross-entity cascade), and any of their own
     * still-pending mutations cancelled first, since they'd otherwise try to
     * create/update a product against an inventory that's about to stop
     * existing server-side.
     */
    suspend fun deleteInventory(localId: Long) {
        val current = inventoryDao.getById(localId) ?: return
        val publicId = current.publicId

        inventoryProductDao.getByInventoryId(publicId).forEach { product -> product.outboxId?.let { outboxDao.delete(it) } }
        inventoryProductDao.deleteByInventoryId(publicId)

        current.outboxId?.let { outboxDao.delete(it) }
        inventoryDao.delete(localId)

        val serverId = current.serverId ?: return
        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_DELETE_INVENTORY,
                payloadJson = json.encodeToString(OutboxInventoryDeletePayload(serverId = serverId)),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    // Sharing management is a direct network call, no outbox — simple,
    // infrequent management actions rather than core offline-first data
    // (same shape as deleteProduct/deleteCategory used to have).
    suspend fun shareInventory(inventoryId: String, userId: String) {
        api.shareInventory(inventoryId, InventorySharePayload(userId = userId))
    }

    suspend fun getInventoryShares(inventoryId: String): List<ShareEntry> =
        emptyAsNull { api.getInventoryShares(inventoryId) }.map { ShareEntry(userId = it.userId) }

    suspend fun removeInventoryShare(inventoryId: String, userId: String) {
        api.deleteInventoryShare(inventoryId, userId)
    }

    /**
     * Offline-first write path — same shape as [createInventory]. [inventoryId]
     * is whatever [InventoryEntity.publicId] the caller currently knows the
     * parent inventory by (a real backend id, or a not-yet-synced stand-in)
     * — `SyncManager` resolves it to the real id at replay time.
     * [catalogProductId] is always a real `catalog_product` id — no
     * manually-typed name, the product identity comes entirely from the
     * catalog. Deduplicates against an existing tracked row for the same
     * catalog product *within this inventory* — a product can be tracked in
     * some inventories and not others, so this dedup is per-inventory, not
     * household-wide like the old shape this replaces.
     */
    suspend fun createProduct(inventoryId: String, catalogProductId: String, quantity: Int?) {
        val existing = inventoryProductDao.findByCatalogProductId(catalogProductId, inventoryId)
        if (existing != null) return

        val payload = OutboxInventoryProductPayload(inventoryId = inventoryId, catalogProductId = catalogProductId, quantity = quantity)
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_INVENTORY_PRODUCT,
                payloadJson = json.encodeToString(payload),
                createdAt = System.currentTimeMillis(),
            ),
        )
        inventoryProductDao.upsert(
            InventoryProductEntity(
                outboxId = outboxId,
                inventoryId = inventoryId,
                catalogProductId = catalogProductId,
                quantity = quantity,
                syncStatus = SyncStatus.PENDING,
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    // Direct REST write, no outbox — deliberately not offline-first in this
    // phase (see WorkTimeRepository.setMonthOverride for the same shape).
    // The local mirror row is still removed on success, since observeProducts
    // now drives the product list from Room rather than a fresh network
    // fetch each time — without this the deleted row would otherwise linger
    // until the next refreshFromBackend.
    suspend fun deleteProduct(id: String) {
        api.deleteInventoryProduct(id)
        inventoryProductDao.deleteByServerId(id)
    }

    // null means "not currently tracked" — sent as an empty string, the
    // same not-set convention the backend uses for the threshold fields.
    // Direct REST write, no outbox (see deleteProduct's doc comment); the
    // local mirror is updated on success so the tile grid still reflects it
    // without waiting on the next refreshFromBackend. Quantity is the only
    // field this route can still change post-creation — no more name/category
    // to carry along.
    suspend fun updateProductQuantity(productId: String, newQuantity: Int?) {
        api.updateInventoryProduct(productId, InventoryProductQuantityPayload(quantity = newQuantity?.toString().orEmpty()))
        inventoryProductDao.updateQuantityByServerId(productId, newQuantity)
    }

    // Thresholds/reminder are deliberately a separate call from
    // updateProductQuantity (different backend route entirely) so neither
    // ever risks clobbering the other's fields. Same direct-REST-write shape
    // as updateProductQuantity, including the local-mirror write-through
    // (the product tile grid's warning colors read these fields locally).
    suspend fun updateProductSettings(
        productId: String,
        firstThreshold: Int?,
        secondThreshold: Int?,
        reminderThreshold: Int?,
        reminderHour: Int?,
        reminderMinute: Int?,
    ) {
        api.updateInventoryProductSettings(
            productId,
            InventoryProductSettingsPayload(
                firstThreshold = firstThreshold?.toString().orEmpty(),
                secondThreshold = secondThreshold?.toString().orEmpty(),
                reminderThreshold = reminderThreshold?.toString().orEmpty(),
                reminderHour = reminderHour?.toString().orEmpty(),
                reminderMinute = reminderMinute?.toString().orEmpty(),
            ),
        )
        inventoryProductDao.updateSettingsByServerId(
            productId, firstThreshold, secondThreshold, reminderThreshold, reminderHour, reminderMinute,
        )
    }

    // Used by ReminderScheduler's conditional-fire check: looks up a single
    // product's current quantity by re-fetching its inventory's product list
    // (no dedicated "get product by id" backend route exists, and adding one
    // just for this would be solving a problem the existing endpoint already
    // covers). Always a fresh network read, not the Room cache — a
    // conditional reminder needs the backend's current truth, not
    // potentially-stale local data.
    suspend fun getProductQuantity(inventoryId: String, productId: String): Int? =
        emptyAsNull { api.getInventoryProducts(inventoryId) }.find { it.id == productId }?.quantity?.toIntOrNull()

    /**
     * Opportunistic backend refresh for the Room-cached inventories/products —
     * same best-effort shape as [ShoppingListRepository.refreshFromBackend]:
     * run when reachable, never blocking the UI or surfacing an error on
     * failure. Products are only refreshed per inventory already known
     * locally (matching the backend's own per-inventory endpoint
     * granularity — no "all products" route exists), so a cold start still
     * shows something for whichever inventory screen the user opens next.
     * Never touches PENDING/FAILED rows, which exist solely via the outbox
     * replay path above.
     */
    suspend fun refreshFromBackend() {
        refreshQuietly {
            val inventories = emptyAsNull { api.getInventories() }
            inventoryDao.upsertFromServer(inventories.map { it.toEntity() })
        }
        inventoryDao.observeAll().first().mapNotNull { it.serverId }.forEach { inventoryId ->
            refreshQuietly {
                val products = emptyAsNull { api.getInventoryProducts(inventoryId) }
                inventoryProductDao.upsertFromServer(products.map { it.toEntity() })
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

private fun InventoryDto.toEntity() = InventoryEntity(
    serverId = id,
    outboxId = null,
    name = name,
    syncStatus = SyncStatus.SYNCED,
)

private fun InventoryProductDto.toEntity() = InventoryProductEntity(
    serverId = id,
    outboxId = null,
    inventoryId = inventoryId,
    catalogProductId = catalogProductId,
    quantity = quantity.toIntOrNull(),
    firstThreshold = firstThreshold.toIntOrNull(),
    secondThreshold = secondThreshold.toIntOrNull(),
    reminderThreshold = reminderThreshold.toIntOrNull(),
    reminderHour = reminderHour.toIntOrNull(),
    reminderMinute = reminderMinute.toIntOrNull(),
    syncStatus = SyncStatus.SYNCED,
)

// The backend sorts alphabetically on the raw name, which would clump any
// emoji-prefixed name together by codepoint instead of alphabetizing by the
// letter that follows it (e.g. "🍖Kitchen" next to other emoji, not next to
// other K's) — some names are expected to have an emoji prefix, some not, so
// sorting needs to skip past any leading non-letter/non-digit codepoints
// (emoji or otherwise) before comparing. Internal (not private):
// ch.mcfx.urs.shoppinglist reuses this for the same name sort order, so
// shopping-list/inventory lists never disagree on ordering.
internal fun String.alphabeticSortKey(): String {
    var charIndex = 0
    val codePoints = codePoints().toArray()
    for (codePoint in codePoints) {
        if (Character.isLetterOrDigit(codePoint)) {
            return substring(charIndex).lowercase()
        }
        charIndex += Character.charCount(codePoint)
    }
    return lowercase()
}
