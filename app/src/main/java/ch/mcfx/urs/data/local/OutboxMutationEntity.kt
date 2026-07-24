package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class OutboxStatus { PENDING, SYNCING, FAILED }

/**
 * One queued, not-yet-confirmed write. [payloadJson] is a
 * kotlinx.serialization-encoded payload matching [type] — [OutboxFillPayload]
 * for [TYPE_CREATE_FILL], [OutboxFillUpdatePayload]/[OutboxFillDeletePayload]
 * for [TYPE_UPDATE_FILL]/[TYPE_DELETE_FILL], [OutboxWorkTimeEntryPayload] for
 * [TYPE_CREATE_WORK_TIME_ENTRY] (the backend resolves ad-hoc station
 * creation / daily-total computation server-side as part of that same
 * request in both cases, so one row per submission is enough),
 * [OutboxInventoryPayload]/[OutboxInventoryUpdatePayload]/
 * [OutboxInventoryDeletePayload] for [TYPE_CREATE_INVENTORY]/
 * [TYPE_UPDATE_INVENTORY]/[TYPE_DELETE_INVENTORY],
 * [OutboxInventoryProductPayload] for [TYPE_CREATE_INVENTORY_PRODUCT],
 * [OutboxListPayload]/[OutboxListUpdatePayload]/[OutboxListDeletePayload]
 * for [TYPE_CREATE_LIST]/[TYPE_UPDATE_LIST]/[TYPE_DELETE_LIST],
 * [OutboxListItemPayload]/[OutboxListItemUpdatePayload]/
 * [OutboxListItemDeletePayload] for [TYPE_CREATE_LIST_ITEM]/
 * [TYPE_UPDATE_LIST_ITEM]/[TYPE_DELETE_LIST_ITEM],
 * [OutboxLocationHistoryPayload] for [TYPE_CREATE_LOCATION_HISTORY].
 * [createdAt] drives strict FIFO replay order (see `SyncManager`), not
 * wall-clock display.
 */
@Entity(tableName = "outbox_mutation")
data class OutboxMutationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val payloadJson: String,
    val createdAt: Long,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val status: OutboxStatus = OutboxStatus.PENDING,
) {
    companion object {
        const val TYPE_CREATE_FILL = "CREATE_FILL"
        const val TYPE_UPDATE_FILL = "UPDATE_FILL"
        const val TYPE_DELETE_FILL = "DELETE_FILL"
        const val TYPE_CREATE_WORK_TIME_ENTRY = "CREATE_WORK_TIME_ENTRY"
        const val TYPE_UPDATE_WORK_TIME_ENTRY = "UPDATE_WORK_TIME_ENTRY"
        const val TYPE_DELETE_WORK_TIME_ENTRY = "DELETE_WORK_TIME_ENTRY"
        const val TYPE_CREATE_INVENTORY = "CREATE_INVENTORY"
        const val TYPE_UPDATE_INVENTORY = "UPDATE_INVENTORY"
        const val TYPE_DELETE_INVENTORY = "DELETE_INVENTORY"
        const val TYPE_CREATE_INVENTORY_PRODUCT = "CREATE_INVENTORY_PRODUCT"
        const val TYPE_CREATE_LIST = "CREATE_LIST"
        const val TYPE_UPDATE_LIST = "UPDATE_LIST"
        const val TYPE_DELETE_LIST = "DELETE_LIST"
        const val TYPE_CREATE_LIST_ITEM = "CREATE_LIST_ITEM"
        const val TYPE_UPDATE_LIST_ITEM = "UPDATE_LIST_ITEM"
        const val TYPE_DELETE_LIST_ITEM = "DELETE_LIST_ITEM"
        const val TYPE_CREATE_LOCATION_HISTORY = "CREATE_LOCATION_HISTORY"
        const val TYPE_CREATE_VEHICLE_SERVICE = "CREATE_VEHICLE_SERVICE"
        const val TYPE_UPDATE_VEHICLE_SERVICE = "UPDATE_VEHICLE_SERVICE"
        const val TYPE_DELETE_VEHICLE_SERVICE = "DELETE_VEHICLE_SERVICE"
    }
}
