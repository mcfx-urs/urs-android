package ch.mcfx.urs.data

import ch.mcfx.urs.data.local.InventoryCategoryDao
import ch.mcfx.urs.data.local.InventoryCategoryEntity
import ch.mcfx.urs.data.local.InventoryProductDao
import ch.mcfx.urs.data.local.InventoryProductEntity
import ch.mcfx.urs.data.local.OutboxDao
import ch.mcfx.urs.data.local.OutboxInventoryCategoryPayload
import ch.mcfx.urs.data.local.OutboxInventoryProductPayload
import ch.mcfx.urs.data.local.OutboxMutationEntity
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.data.remote.InventoryCategoryDto
import ch.mcfx.urs.data.remote.InventoryProductDto
import ch.mcfx.urs.data.remote.InventoryProductPayload
import ch.mcfx.urs.data.remote.InventoryProductSettingsPayload
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

class InventoryRepository(
    private val api: UrsApi,
    private val inventoryCategoryDao: InventoryCategoryDao,
    private val inventoryProductDao: InventoryProductDao,
    private val outboxDao: OutboxDao,
    private val syncManager: SyncManager,
    private val applicationScope: CoroutineScope,
    private val json: Json,
) {

    fun observeCategories(): Flow<List<InventoryCategoryEntity>> =
        inventoryCategoryDao.observeAll().map { it.sortedBy { c -> c.name.alphabeticSortKey() } }

    fun observeProducts(categoryId: String): Flow<List<InventoryProductEntity>> =
        inventoryProductDao.observeByCategory(categoryId).map { it.sortedBy { p -> p.name.alphabeticSortKey() } }

    /**
     * Offline-first write path — the *only* way a category gets created,
     * online or offline. Same shape as [FuelRepository.createFill]: both
     * writes below (the outbox row and the local [InventoryCategoryEntity],
     * status [SyncStatus.PENDING]) are local-only and instant, a background
     * sync attempt is fired immediately afterwards but never awaited here.
     */
    suspend fun createCategory(name: String) {
        val payload = OutboxInventoryCategoryPayload(name = name)
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_INVENTORY_CATEGORY,
                payloadJson = json.encodeToString(payload),
                createdAt = System.currentTimeMillis(),
            ),
        )
        inventoryCategoryDao.upsert(
            InventoryCategoryEntity(outboxId = outboxId, name = name, syncStatus = SyncStatus.PENDING),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Offline-first write path — same shape as [createCategory]. [categoryId]
     * is whatever [InventoryCategoryEntity.publicId] the caller currently
     * knows the parent category by (a real backend id, or a not-yet-synced
     * stand-in) — `SyncManager` resolves it to the real id at replay time.
     */
    suspend fun createProduct(categoryId: String, name: String, quantity: Int?) {
        val payload = OutboxInventoryProductPayload(categoryId = categoryId, name = name, quantity = quantity)
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
                categoryId = categoryId,
                name = name,
                quantity = quantity,
                syncStatus = SyncStatus.PENDING,
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    // Direct REST write, no outbox — deliberately not offline-first in this
    // phase (see WorkTimeRepository.setMonthOverride for the same shape).
    // The local mirror row is still removed on success, since observeCategories
    // now drives the category list from Room rather than a fresh network
    // fetch each time — without this the deleted row would otherwise linger
    // until the next refreshFromBackend.
    suspend fun deleteCategory(id: String) {
        api.deleteInventoryCategory(id)
        inventoryCategoryDao.deleteByServerId(id)
    }

    // Same shape as deleteCategory.
    suspend fun deleteProduct(id: String) {
        api.deleteInventoryProduct(id)
        inventoryProductDao.deleteByServerId(id)
    }

    // null means "not currently tracked" — sent as an empty string, the
    // same not-set convention the backend uses for the threshold fields.
    // Direct REST write, no outbox (see deleteCategory's doc comment); the
    // local mirror is updated on success so the stepper's list still
    // reflects it without waiting on the next refreshFromBackend.
    suspend fun updateProductQuantity(categoryId: String, productId: String, name: String, newQuantity: Int?) {
        api.updateInventoryProduct(
            productId,
            InventoryProductPayload(categoryId = categoryId, name = name, quantity = newQuantity?.toString().orEmpty()),
        )
        inventoryProductDao.updateQuantityByServerId(productId, newQuantity)
    }

    // Thresholds/reminder are deliberately a separate call from
    // updateProductQuantity (different backend route entirely) so neither
    // ever risks clobbering the other's fields. Same direct-REST-write shape
    // as updateProductQuantity, including the local-mirror write-through
    // (the product list's warning colors read these fields locally).
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
    // product's current quantity by re-fetching its category's product list
    // (no dedicated "get product by id" backend route exists, and adding one
    // just for this would be solving a problem the existing endpoint already
    // covers). Always a fresh network read, not the Room cache — a
    // conditional reminder needs the backend's current truth, not
    // potentially-stale local data.
    suspend fun getProductQuantity(categoryId: String, productId: String): Int? =
        emptyAsNull { api.getInventoryProducts(categoryId) }.find { it.id == productId }?.quantity?.toIntOrNull()

    /**
     * Opportunistic backend refresh for the Room-cached categories/products —
     * same best-effort shape as [FuelRepository.refreshFromBackend]: run
     * when reachable, never blocking the UI or surfacing an error on
     * failure. Products are only refreshed per category already known
     * locally (matching the backend's own per-category endpoint
     * granularity — no "all products" route exists), so a cold start still
     * shows something for whichever category screen the user opens next.
     * Never touches PENDING/FAILED rows, which exist solely via the outbox
     * replay path above.
     */
    suspend fun refreshFromBackend() {
        refreshQuietly {
            val categories = emptyAsNull { api.getInventoryCategories() }
            inventoryCategoryDao.upsertFromServer(categories.map { it.toEntity() })
        }
        inventoryCategoryDao.observeAll().first().mapNotNull { it.serverId }.forEach { categoryId ->
            refreshQuietly {
                val products = emptyAsNull { api.getInventoryProducts(categoryId) }
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

private fun InventoryCategoryDto.toEntity() = InventoryCategoryEntity(
    serverId = id,
    outboxId = null,
    name = name,
    syncStatus = SyncStatus.SYNCED,
)

private fun InventoryProductDto.toEntity() = InventoryProductEntity(
    serverId = id,
    outboxId = null,
    categoryId = categoryId,
    name = name,
    quantity = quantity.toIntOrNull(),
    firstThreshold = firstThreshold.toIntOrNull(),
    secondThreshold = secondThreshold.toIntOrNull(),
    reminderThreshold = reminderThreshold.toIntOrNull(),
    reminderHour = reminderHour.toIntOrNull(),
    reminderMinute = reminderMinute.toIntOrNull(),
    catalogProductId = null,
    syncStatus = SyncStatus.SYNCED,
)

// The backend sorts alphabetically on the raw name, which would clump any
// emoji-prefixed name together by codepoint instead of alphabetizing by the
// letter that follows it (e.g. "🍖Kitchen" next to other emoji, not next to
// other K's) — some categories are expected to have an emoji prefix, some
// not, so sorting needs to skip past any leading non-letter/non-digit
// codepoints (emoji or otherwise) before comparing.
private fun String.alphabeticSortKey(): String {
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
