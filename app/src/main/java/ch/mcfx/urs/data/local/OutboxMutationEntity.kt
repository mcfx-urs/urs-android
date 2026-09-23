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
 * [OutboxLocationHistoryPayload] for [TYPE_CREATE_LOCATION_HISTORY],
 * [OutboxBakePlanPayload] for [TYPE_CREATE_BAKE_PLAN],
 * [OutboxBakePlanStepUpdatePayload] for [TYPE_UPDATE_BAKE_PLAN_STEP],
 * [OutboxBakePlanCancelPayload] for [TYPE_CANCEL_BAKE_PLAN],
 * [OutboxNoteCreatePayload] for [TYPE_CREATE_NOTE], [OutboxNoteUpdatePayload]
 * for [TYPE_UPDATE_NOTE], [OutboxNoteStatusPayload] for
 * [TYPE_UPDATE_NOTE_STATUS], [OutboxNoteDeletePayload] for [TYPE_DELETE_NOTE],
 * [OutboxBeerLogCreatePayload] for [TYPE_CREATE_BEER_LOG],
 * [OutboxKanbanBoardPayload]/[OutboxKanbanBoardUpdatePayload]/
 * [OutboxKanbanBoardDeletePayload] for [TYPE_CREATE_KANBAN_BOARD]/
 * [TYPE_UPDATE_KANBAN_BOARD]/[TYPE_DELETE_KANBAN_BOARD],
 * [OutboxKanbanColumnPayload]/[OutboxKanbanColumnUpdatePayload]/
 * [OutboxKanbanColumnMovePayload]/[OutboxKanbanColumnDeletePayload] for
 * [TYPE_CREATE_KANBAN_COLUMN]/[TYPE_UPDATE_KANBAN_COLUMN]/
 * [TYPE_MOVE_KANBAN_COLUMN]/[TYPE_DELETE_KANBAN_COLUMN],
 * [OutboxKanbanCardPayload]/[OutboxKanbanCardUpdatePayload]/
 * [OutboxKanbanCardMovePayload]/[OutboxKanbanCardDeletePayload] for
 * [TYPE_CREATE_KANBAN_CARD]/[TYPE_UPDATE_KANBAN_CARD]/
 * [TYPE_MOVE_KANBAN_CARD]/[TYPE_DELETE_KANBAN_CARD],
 * [OutboxKanbanChecklistItemPayload]/[OutboxKanbanChecklistItemUpdatePayload]/
 * [OutboxKanbanChecklistItemDeletePayload] for
 * [TYPE_CREATE_KANBAN_CHECKLIST_ITEM]/[TYPE_UPDATE_KANBAN_CHECKLIST_ITEM]/
 * [TYPE_DELETE_KANBAN_CHECKLIST_ITEM],
 * [OutboxTrackerDomainCreatePayload]/[OutboxTrackerDomainUpdatePayload]/
 * [OutboxTrackerDomainDeletePayload] for [TYPE_CREATE_TRACKER_DOMAIN]/
 * [TYPE_UPDATE_TRACKER_DOMAIN]/[TYPE_DELETE_TRACKER_DOMAIN],
 * [OutboxAssetPayload]/[OutboxAssetUpdatePayload]/[OutboxAssetDeletePayload]
 * for [TYPE_CREATE_ASSET]/[TYPE_UPDATE_ASSET]/[TYPE_DELETE_ASSET],
 * [OutboxAssetComponentPayload]/[OutboxAssetComponentUpdatePayload]/
 * [OutboxAssetComponentDeletePayload] for [TYPE_CREATE_ASSET_COMPONENT]/
 * [TYPE_UPDATE_ASSET_COMPONENT]/[TYPE_DELETE_ASSET_COMPONENT],
 * [OutboxAssetCommentPayload]/[OutboxAssetCommentUpdatePayload]/
 * [OutboxAssetCommentDeletePayload] for [TYPE_CREATE_ASSET_COMMENT]/
 * [TYPE_UPDATE_ASSET_COMMENT]/[TYPE_DELETE_ASSET_COMMENT].
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
        const val TYPE_CREATE_BAKE_PLAN = "CREATE_BAKE_PLAN"
        const val TYPE_UPDATE_BAKE_PLAN_STEP = "UPDATE_BAKE_PLAN_STEP"
        const val TYPE_CANCEL_BAKE_PLAN = "CANCEL_BAKE_PLAN"
        const val TYPE_CREATE_NOTE = "CREATE_NOTE"
        const val TYPE_UPDATE_NOTE = "UPDATE_NOTE"
        const val TYPE_UPDATE_NOTE_STATUS = "UPDATE_NOTE_STATUS"
        const val TYPE_DELETE_NOTE = "DELETE_NOTE"
        const val TYPE_CREATE_TRACKER_TYPE = "CREATE_TRACKER_TYPE"
        const val TYPE_UPDATE_TRACKER_TYPE = "UPDATE_TRACKER_TYPE"
        const val TYPE_ARCHIVE_TRACKER_TYPE = "ARCHIVE_TRACKER_TYPE"
        const val TYPE_REACTIVATE_TRACKER_TYPE = "REACTIVATE_TRACKER_TYPE"
        const val TYPE_CREATE_TRACKER_EVENT = "CREATE_TRACKER_EVENT"
        const val TYPE_UPDATE_TRACKER_EVENT = "UPDATE_TRACKER_EVENT"
        const val TYPE_DELETE_TRACKER_EVENT = "DELETE_TRACKER_EVENT"
        const val TYPE_CREATE_BEER_LOG = "CREATE_BEER_LOG"
        const val TYPE_CREATE_KANBAN_BOARD = "CREATE_KANBAN_BOARD"
        const val TYPE_UPDATE_KANBAN_BOARD = "UPDATE_KANBAN_BOARD"
        const val TYPE_DELETE_KANBAN_BOARD = "DELETE_KANBAN_BOARD"
        const val TYPE_CREATE_KANBAN_COLUMN = "CREATE_KANBAN_COLUMN"
        const val TYPE_UPDATE_KANBAN_COLUMN = "UPDATE_KANBAN_COLUMN"
        const val TYPE_MOVE_KANBAN_COLUMN = "MOVE_KANBAN_COLUMN"
        const val TYPE_DELETE_KANBAN_COLUMN = "DELETE_KANBAN_COLUMN"
        const val TYPE_CREATE_KANBAN_CARD = "CREATE_KANBAN_CARD"
        const val TYPE_UPDATE_KANBAN_CARD = "UPDATE_KANBAN_CARD"
        const val TYPE_MOVE_KANBAN_CARD = "MOVE_KANBAN_CARD"
        const val TYPE_DELETE_KANBAN_CARD = "DELETE_KANBAN_CARD"
        const val TYPE_CREATE_KANBAN_CHECKLIST_ITEM = "CREATE_KANBAN_CHECKLIST_ITEM"
        const val TYPE_UPDATE_KANBAN_CHECKLIST_ITEM = "UPDATE_KANBAN_CHECKLIST_ITEM"
        const val TYPE_DELETE_KANBAN_CHECKLIST_ITEM = "DELETE_KANBAN_CHECKLIST_ITEM"
        const val TYPE_CREATE_TRACKER_DOMAIN = "CREATE_TRACKER_DOMAIN"
        const val TYPE_UPDATE_TRACKER_DOMAIN = "UPDATE_TRACKER_DOMAIN"
        const val TYPE_DELETE_TRACKER_DOMAIN = "DELETE_TRACKER_DOMAIN"
        const val TYPE_CREATE_ASSET = "CREATE_ASSET"
        const val TYPE_UPDATE_ASSET = "UPDATE_ASSET"
        const val TYPE_DELETE_ASSET = "DELETE_ASSET"
        const val TYPE_CREATE_ASSET_COMPONENT = "CREATE_ASSET_COMPONENT"
        const val TYPE_UPDATE_ASSET_COMPONENT = "UPDATE_ASSET_COMPONENT"
        const val TYPE_DELETE_ASSET_COMPONENT = "DELETE_ASSET_COMPONENT"
        const val TYPE_CREATE_ASSET_COMMENT = "CREATE_ASSET_COMMENT"
        const val TYPE_UPDATE_ASSET_COMMENT = "UPDATE_ASSET_COMMENT"
        const val TYPE_DELETE_ASSET_COMMENT = "DELETE_ASSET_COMMENT"
    }
}
