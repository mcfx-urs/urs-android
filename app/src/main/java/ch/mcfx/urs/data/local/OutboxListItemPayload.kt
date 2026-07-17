package ch.mcfx.urs.data.local

import kotlinx.serialization.Serializable

/**
 * What gets JSON-encoded into [OutboxMutationEntity.payloadJson] for a
 * queued list-item creation. [listId] is the parent list's [ListEntity
 * .publicId] as it stood when this was queued — a real backend id, or (if
 * the list was still offline) a not-yet-synced stand-in that `SyncManager`
 * resolves to the real id at replay time, once the list's own create has
 * gone through. [catalogProductId] is always a real `catalog_product` id
 * (`catalog_product` rows are server-authoritative reference data
 * this app never creates offline-first in this flow, only via the
 * synchronous, direct-REST `POST /catalog-product` manual-creation call —
 * see [ch.mcfx.urs.data.ShoppingListRepository.addCatalogProduct]), so
 * unlike [listId] it never needs its own stand-in-resolution step.
 */
@Serializable
data class OutboxListItemPayload(
    val listId: String,
    val catalogProductId: String,
    val note: String? = null,
)

/**
 * What gets JSON-encoded for a queued update to an item that's already
 * confirmed by the backend — [serverId] identifies it directly, same shape
 * as [OutboxWorkTimeEntryUpdatePayload]. `list_item_checked` is gone
 * entirely — adding/removing an item from a list is the only state
 * transition now, so there's nothing left to carry here besides the note.
 */
@Serializable
data class OutboxListItemUpdatePayload(
    val serverId: String,
    val note: String? = null,
)

/** Same shape as [OutboxWorkTimeEntryDeletePayload]. */
@Serializable
data class OutboxListItemDeletePayload(val serverId: String)
