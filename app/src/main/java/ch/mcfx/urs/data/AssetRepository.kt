package ch.mcfx.urs.data

import ch.mcfx.urs.data.local.AssetCommentDao
import ch.mcfx.urs.data.local.AssetCommentEntity
import ch.mcfx.urs.data.local.AssetComponentDao
import ch.mcfx.urs.data.local.AssetComponentEntity
import ch.mcfx.urs.data.local.AssetDao
import ch.mcfx.urs.data.local.AssetEntity
import ch.mcfx.urs.data.local.OutboxAssetCommentDeletePayload
import ch.mcfx.urs.data.local.OutboxAssetCommentPayload
import ch.mcfx.urs.data.local.OutboxAssetCommentUpdatePayload
import ch.mcfx.urs.data.local.OutboxAssetComponentDeletePayload
import ch.mcfx.urs.data.local.OutboxAssetComponentPayload
import ch.mcfx.urs.data.local.OutboxAssetComponentUpdatePayload
import ch.mcfx.urs.data.local.OutboxAssetDeletePayload
import ch.mcfx.urs.data.local.OutboxAssetInitialComponent
import ch.mcfx.urs.data.local.OutboxAssetPayload
import ch.mcfx.urs.data.local.OutboxAssetUpdatePayload
import ch.mcfx.urs.data.local.OutboxDao
import ch.mcfx.urs.data.local.OutboxMutationEntity
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.data.local.localIdStandIn
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.data.remote.AssetComponentDto
import ch.mcfx.urs.data.remote.AssetDto
import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.data.sync.SyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One asset joined with its components/comments — what the detail screen renders. */
data class AssetWithDetails(
    val asset: AssetEntity,
    val components: List<AssetComponentEntity>,
    val comments: List<AssetCommentEntity>,
)

/**
 * Assets (mcfx-urs/urs-android#89) — same offline-first Room+Outbox shape as
 * [KanbanRepository], one level shallower (asset → component/comment,
 * instead of board → column → card → checklist-item). Unlike Kanban cards,
 * an asset's initial components are queued *inside* the asset's own create
 * mutation (see [OutboxAssetPayload]) rather than as separate mutations —
 * matches the backend's `POST /api/v1/asset` accepting them inline.
 */
class AssetRepository(
    private val api: UrsApi,
    private val assetDao: AssetDao,
    private val componentDao: AssetComponentDao,
    private val commentDao: AssetCommentDao,
    private val outboxDao: OutboxDao,
    private val syncManager: SyncManager,
    private val applicationScope: CoroutineScope,
    private val json: Json,
) {
    fun observeAssets(): Flow<List<AssetEntity>> = assetDao.observeAll()

    suspend fun getAssetWithDetails(localId: Long): AssetWithDetails? {
        val asset = assetDao.getById(localId) ?: return null
        return AssetWithDetails(
            asset = asset,
            components = componentDao.getByAssetId(asset.publicId),
            comments = commentDao.getByAssetId(asset.publicId),
        )
    }

    /**
     * Offline-first create — the *only* way an asset gets created, online or
     * offline, same shape as [KanbanRepository.createCard]. [initialComponents]
     * rides inside this same outbox mutation (see [OutboxAssetPayload]),
     * never as separate per-component mutations.
     */
    suspend fun createAsset(
        name: String,
        category: AssetCategory,
        location: String,
        tags: List<String>,
        initialComponents: List<OutboxAssetInitialComponent>,
    ) {
        val payload = OutboxAssetPayload(
            name = name, category = category.toRaw(), location = location, tags = tags, components = initialComponents,
        )
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_ASSET,
                payloadJson = json.encodeToString(payload),
                createdAt = System.currentTimeMillis(),
            ),
        )
        val localId = assetDao.upsert(
            AssetEntity(
                outboxId = outboxId, name = name, category = category, location = location,
                tags = tags, syncStatus = SyncStatus.PENDING,
            ),
        )
        val assetPublicId = localIdStandIn(localId)
        val total = initialComponents.sumOf { it.price.toDoubleOrNull() ?: 0.0 }
        initialComponents.forEach { c ->
            componentDao.upsert(
                AssetComponentEntity(
                    assetId = assetPublicId, description = c.description, manufacturer = c.manufacturer,
                    price = c.price, purchaseDate = c.purchaseDate, dealer = c.dealer, syncStatus = SyncStatus.PENDING,
                ),
            )
        }
        if (total > 0.0) assetDao.updateTotalValue(localId, "%.2f".format(total))
        applicationScope.launch { syncManager.syncNow() }
    }

    /** Offline-first update of the asset's own fields — never components/comments. */
    suspend fun updateAsset(localId: Long, name: String, category: AssetCategory, location: String, status: String, tags: List<String>) {
        assetDao.updateFields(localId, name, category, location, status, tags, SyncStatus.PENDING)
        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_UPDATE_ASSET,
                payloadJson = json.encodeToString(
                    OutboxAssetUpdatePayload(localAssetId = localId, name = name, category = category.toRaw(), location = location, status = status, tags = tags),
                ),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    suspend fun deleteAsset(localId: Long) {
        val current = assetDao.getById(localId) ?: return
        val publicId = current.publicId
        componentDao.getByAssetId(publicId).forEach { it.outboxId?.let { id -> outboxDao.delete(id) } }
        commentDao.getByAssetId(publicId).forEach { it.outboxId?.let { id -> outboxDao.delete(id) } }
        componentDao.deleteByAssetId(publicId)
        commentDao.deleteByAssetId(publicId)
        current.outboxId?.let { outboxDao.delete(it) }
        assetDao.delete(localId)

        val serverId = current.serverId ?: return
        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_DELETE_ASSET,
                payloadJson = json.encodeToString(OutboxAssetDeletePayload(serverId = serverId)),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    suspend fun addComponent(assetId: String, description: String, manufacturer: String, price: String, purchaseDate: String, dealer: String) {
        val payload = OutboxAssetComponentPayload(assetId = assetId, description = description, manufacturer = manufacturer, price = price, purchaseDate = purchaseDate, dealer = dealer)
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_ASSET_COMPONENT,
                payloadJson = json.encodeToString(payload),
                createdAt = System.currentTimeMillis(),
            ),
        )
        componentDao.upsert(
            AssetComponentEntity(outboxId = outboxId, assetId = assetId, description = description, manufacturer = manufacturer, price = price, purchaseDate = purchaseDate, dealer = dealer, syncStatus = SyncStatus.PENDING),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    suspend fun updateComponent(localId: Long, description: String, manufacturer: String, price: String, purchaseDate: String, dealer: String) {
        componentDao.updateFields(localId, description, manufacturer, price, purchaseDate, dealer, SyncStatus.PENDING)
        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_UPDATE_ASSET_COMPONENT,
                payloadJson = json.encodeToString(OutboxAssetComponentUpdatePayload(localComponentId = localId, description = description, manufacturer = manufacturer, price = price, purchaseDate = purchaseDate, dealer = dealer)),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    suspend fun deleteComponent(localId: Long) {
        val current = componentDao.getById(localId) ?: return
        current.outboxId?.let { outboxDao.delete(it) }
        componentDao.delete(localId)

        val serverId = current.serverId ?: return
        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_DELETE_ASSET_COMPONENT,
                payloadJson = json.encodeToString(OutboxAssetComponentDeletePayload(serverId = serverId)),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    suspend fun addComment(assetId: String, text: String, date: String) {
        val payload = OutboxAssetCommentPayload(assetId = assetId, text = text, date = date)
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_ASSET_COMMENT,
                payloadJson = json.encodeToString(payload),
                createdAt = System.currentTimeMillis(),
            ),
        )
        commentDao.upsert(AssetCommentEntity(outboxId = outboxId, assetId = assetId, text = text, date = date, syncStatus = SyncStatus.PENDING))
        applicationScope.launch { syncManager.syncNow() }
    }

    suspend fun updateComment(localId: Long, text: String, date: String) {
        commentDao.updateFields(localId, text, date, SyncStatus.PENDING)
        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_UPDATE_ASSET_COMMENT,
                payloadJson = json.encodeToString(OutboxAssetCommentUpdatePayload(localCommentId = localId, text = text, date = date)),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    suspend fun deleteComment(localId: Long) {
        val current = commentDao.getById(localId) ?: return
        current.outboxId?.let { outboxDao.delete(it) }
        commentDao.delete(localId)

        val serverId = current.serverId ?: return
        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_DELETE_ASSET_COMMENT,
                payloadJson = json.encodeToString(OutboxAssetCommentDeletePayload(serverId = serverId)),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    /** Pulls the full asset list, including each asset's nested components/comments/tags. */
    suspend fun refreshFromBackend(): Boolean {
        syncManager.syncNow()
        return try {
            val assets = emptyAsNull { api.getAssets() }
            assetDao.upsertFromServer(assets.map { it.toEntity() })
            assets.forEach { dto ->
                componentDao.upsertFromServer(dto.id, dto.components.map { it.toEntity(dto.id) })
                commentDao.upsertFromServer(dto.id, dto.comments.map { AssetCommentEntity(serverId = it.id, outboxId = null, assetId = dto.id, text = it.text, date = it.date, syncStatus = SyncStatus.SYNCED) })
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("AssetRepository", "refreshFromBackend failed", e)
            false
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

private fun AssetDto.toEntity() = AssetEntity(
    serverId = id, outboxId = null, name = name, category = assetCategoryFromRaw(category), location = location,
    status = status, tags = tags.map { it.name }, totalValue = totalValue, syncStatus = SyncStatus.SYNCED,
)

private fun AssetComponentDto.toEntity(assetId: String) = AssetComponentEntity(
    serverId = id, outboxId = null, assetId = assetId, description = description, manufacturer = manufacturer,
    price = price, purchaseDate = purchaseDate, dealer = dealer, syncStatus = SyncStatus.SYNCED,
)
