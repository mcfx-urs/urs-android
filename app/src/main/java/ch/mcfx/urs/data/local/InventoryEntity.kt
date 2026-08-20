package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local, always-available mirror of an inventory — a named, ownable,
 * shareable container tracking on-hand quantities of catalog products,
 * replacing the old one-inventory-per-user model built on
 * `inventory_category`. Same offline-first shape as [ListEntity] (stable
 * local [id], nullable [serverId]/[outboxId] until confirmed, [syncStatus]),
 * including full create/rename/delete outbox coverage — unlike the old
 * category shape this replaces, an inventory can be renamed/deleted
 * offline-first too, mirroring [ListEntity] exactly rather than the
 * create-only shape `InventoryCategoryEntity` used to have.
 *
 * [userId] is the locally logged-in user this row was written under (backend
 * `user_id`, not necessarily the inventory's owner — a shared inventory is
 * stamped with whichever user's session pulled it). The backend already
 * scopes `GET /api/v1/inventory` to inventories visible to the caller, but
 * the local Room cache previously had no user concept at all and simply
 * accumulated every synced inventory forever, regardless of who was
 * currently logged in — [InventoryDao.observeAll] filters on this column so
 * switching the logged-in user on one device can't show a previous user's
 * inventory.
 */
@Entity(tableName = "inventory")
data class InventoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Null until this row has been confirmed by the backend — the real inventory_id.
    val serverId: String? = null,
    // Links back to the OutboxMutationEntity row still driving this
    // inventory's sync, null once synced.
    val outboxId: Long? = null,
    val name: String,
    val userId: String,
    val syncStatus: SyncStatus,
)

/**
 * The id the UI/outbox payloads address this inventory by: the real backend
 * id once known, otherwise a stand-in derived from the stable local [id] —
 * see [localInventoryId] for the reverse lookup a queued inventory-product
 * create uses to resolve its still-pending parent inventory at replay time.
 */
val InventoryEntity.publicId: String
    get() = serverId ?: localIdStandIn(id)

/** Reverses [publicId] — same shape as [localListId]. */
fun localInventoryId(value: String): Long? = parseLocalIdStandIn(value)
