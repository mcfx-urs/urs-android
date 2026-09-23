package ch.mcfx.urs.data

import android.content.Context
import ch.mcfx.urs.data.local.KanbanBoardDao
import ch.mcfx.urs.data.local.KanbanBoardEntity
import ch.mcfx.urs.data.local.KanbanCardDao
import ch.mcfx.urs.data.local.KanbanCardEntity
import ch.mcfx.urs.data.local.KanbanChecklistItemDao
import ch.mcfx.urs.data.local.KanbanChecklistItemEntity
import ch.mcfx.urs.data.local.KanbanColumnDao
import ch.mcfx.urs.data.local.KanbanColumnEntity
import ch.mcfx.urs.data.local.OutboxDao
import ch.mcfx.urs.data.local.OutboxKanbanBoardDeletePayload
import ch.mcfx.urs.data.local.OutboxKanbanBoardPayload
import ch.mcfx.urs.data.local.OutboxKanbanBoardUpdatePayload
import ch.mcfx.urs.data.local.OutboxKanbanCardDeletePayload
import ch.mcfx.urs.data.local.OutboxKanbanCardMovePayload
import ch.mcfx.urs.data.local.OutboxKanbanCardPayload
import ch.mcfx.urs.data.local.OutboxKanbanCardUpdatePayload
import ch.mcfx.urs.data.local.OutboxKanbanChecklistItemDeletePayload
import ch.mcfx.urs.data.local.OutboxKanbanChecklistItemPayload
import ch.mcfx.urs.data.local.OutboxKanbanChecklistItemUpdatePayload
import ch.mcfx.urs.data.local.OutboxKanbanColumnDeletePayload
import ch.mcfx.urs.data.local.OutboxKanbanColumnMovePayload
import ch.mcfx.urs.data.local.OutboxKanbanColumnPayload
import ch.mcfx.urs.data.local.OutboxKanbanColumnUpdatePayload
import ch.mcfx.urs.data.local.OutboxMutationEntity
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.data.local.TagDao
import ch.mcfx.urs.data.local.TagEntity
import ch.mcfx.urs.data.local.localIdStandIn
import ch.mcfx.urs.data.local.localKanbanBoardId
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.data.remote.KanbanBoardDetailDto
import ch.mcfx.urs.data.remote.KanbanBoardDto
import ch.mcfx.urs.data.remote.KanbanCardDto
import ch.mcfx.urs.data.remote.KanbanChecklistItemDto
import ch.mcfx.urs.data.remote.KanbanColumnDto
import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.data.sync.SyncManager
import ch.mcfx.urs.notifications.KanbanCardAlarmScheduler
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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

/** One card joined with its tags/checklist — what the board detail UI actually renders. */
data class KanbanCardWithDetails(
    val card: KanbanCardEntity,
    val tags: List<TagEntity>,
    val checklist: List<KanbanChecklistItemEntity>,
)

/** One column joined with its cards, already ordered by [KanbanCardEntity.position]. */
data class KanbanColumnWithCards(
    val column: KanbanColumnEntity,
    val cards: List<KanbanCardWithDetails>,
)

/** A board's full tree — board, columns (ordered), each with its cards (ordered). */
data class KanbanBoardDetail(
    val board: KanbanBoardEntity,
    val columns: List<KanbanColumnWithCards>,
)

/**
 * Offline-first write path for Kanban boards/columns/cards/checklist items
 * (GitHub issue #62) — mirrors [ShoppingListRepository]'s two-tier
 * create/update/delete-per-entity structure, with [NoteRepository]'s
 * per-entity device-alarm wiring for card due dates. Nested board/column/
 * card reads are assembled in Kotlin from a handful of flat, cheap
 * `observeAll()` Room flows via [combine] — same "combine + filter/join in
 * Kotlin rather than a Room `@Relation`" shape [ShoppingListRepository
 * .observeItems] already uses, needed here since a column/card's own parent
 * reference is a plain `publicId` string (real id, or a not-yet-synced
 * stand-in), not something Room can join on directly.
 */
class KanbanRepository(
    private val context: Context,
    private val api: UrsApi,
    private val boardDao: KanbanBoardDao,
    private val columnDao: KanbanColumnDao,
    private val cardDao: KanbanCardDao,
    private val checklistDao: KanbanChecklistItemDao,
    private val tagDao: TagDao,
    private val tagRepository: TagRepository,
    private val outboxDao: OutboxDao,
    private val syncManager: SyncManager,
    private val applicationScope: CoroutineScope,
    private val json: Json,
) {

    fun observeBoards(): Flow<List<KanbanBoardEntity>> = boardDao.observeAll().map { it.sortedBy { b -> b.name.alphabeticSortKey() } }

    fun observeFavoriteBoards(): Flow<List<KanbanBoardEntity>> = boardDao.observeFavorites()

    suspend fun setBoardFavorite(localId: Long, isFavorite: Boolean) = boardDao.setFavorite(localId, isFavorite)

    /** Resolves a board's [publicId] (a real serverId, or a not-yet-synced stand-in) back to its stable local row id. */
    suspend fun resolveLocalBoardId(boardId: String): Long? = localKanbanBoardId(boardId) ?: boardDao.findLocalIdByServerId(boardId)

    /**
     * Reactive nested tree for the board detail screen — see this class's
     * own doc comment for why this isn't a Room `@Relation`.
     */
    fun observeBoard(localBoardId: Long): Flow<KanbanBoardDetail?> =
        combine(
            boardDao.observeById(localBoardId),
            columnDao.observeAll(),
            cardDao.observeAll(),
            checklistDao.observeAll(),
            tagDao.observeAllCardTags(),
        ) { board, allColumns, allCards, allChecklist, allTags ->
            if (board == null) return@combine null
            val columns = allColumns.filter { it.boardId == board.publicId }.sortedBy { it.position }
            val cardsByColumn = allCards.groupBy { it.columnId }
            val checklistByCard = allChecklist.groupBy { it.cardId }
            val tagsByCard = allTags.groupBy { it.cardId }
            KanbanBoardDetail(
                board = board,
                columns = columns.map { column ->
                    KanbanColumnWithCards(
                        column = column,
                        cards = cardsByColumn[column.publicId].orEmpty().sortedBy { it.position }.map { card ->
                            KanbanCardWithDetails(
                                card = card,
                                tags = tagsByCard[card.id].orEmpty().sortedBy { it.tagName },
                                checklist = checklistByCard[card.publicId].orEmpty().sortedBy { it.position },
                            )
                        },
                    )
                },
            )
        }

    suspend fun getBoard(localId: Long): KanbanBoardEntity? = boardDao.getById(localId)

    suspend fun createBoard(name: String) {
        val payload = OutboxKanbanBoardPayload(name = name)
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_KANBAN_BOARD,
                payloadJson = json.encodeToString(payload),
                createdAt = System.currentTimeMillis(),
            ),
        )
        boardDao.upsert(KanbanBoardEntity(outboxId = outboxId, name = name, syncStatus = SyncStatus.PENDING))
        applicationScope.launch { syncManager.syncNow() }
    }

    /** Offline-first rename — same "rewrite the pending create in place if not yet synced, otherwise queue an update" shape as [ShoppingListRepository.renameList]. */
    suspend fun renameBoard(localId: Long, name: String) {
        val current = boardDao.getById(localId) ?: return

        val outboxId = if (current.serverId == null) {
            val payload = OutboxKanbanBoardPayload(name = name)
            current.outboxId?.let { outboxDao.updatePayload(it, json.encodeToString(payload)) }
            current.outboxId
        } else {
            current.outboxId?.let { outboxDao.delete(it) }
            val payload = OutboxKanbanBoardUpdatePayload(serverId = current.serverId, name = name)
            outboxDao.insert(
                OutboxMutationEntity(
                    type = OutboxMutationEntity.TYPE_UPDATE_KANBAN_BOARD,
                    payloadJson = json.encodeToString(payload),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }

        boardDao.updateFields(localId, name, SyncStatus.PENDING, outboxId)
        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Offline-first delete — same shape as [ShoppingListRepository
     * .deleteList], cascaded one level further down: every column's cards
     * and every card's checklist/tags/reminder alarm are cleared locally
     * too (Room has no cross-entity cascade), and any of their own
     * still-pending mutations cancelled first.
     */
    suspend fun deleteBoard(localId: Long) {
        val current = boardDao.getById(localId) ?: return
        val boardPublicId = current.publicId

        columnDao.getByBoardId(boardPublicId).forEach { column ->
            deleteColumnCascadeLocal(column.publicId)
            column.outboxId?.let { outboxDao.delete(it) }
        }
        columnDao.deleteByBoardId(boardPublicId)

        current.outboxId?.let { outboxDao.delete(it) }
        boardDao.delete(localId)

        val serverId = current.serverId ?: return
        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_DELETE_KANBAN_BOARD,
                payloadJson = json.encodeToString(OutboxKanbanBoardDeletePayload(serverId = serverId)),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    suspend fun createColumn(boardId: String, name: String) {
        val position = (columnDao.getByBoardId(boardId).maxOfOrNull { it.position } ?: -1) + 1
        val payload = OutboxKanbanColumnPayload(boardId = boardId, name = name)
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_KANBAN_COLUMN,
                payloadJson = json.encodeToString(payload),
                createdAt = System.currentTimeMillis(),
            ),
        )
        columnDao.upsert(
            KanbanColumnEntity(outboxId = outboxId, boardId = boardId, name = name, position = position, syncStatus = SyncStatus.PENDING),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    /** Offline-first rename — same shape as [renameBoard]. */
    suspend fun renameColumn(localId: Long, name: String) {
        val current = columnDao.getById(localId) ?: return

        val outboxId = if (current.serverId == null) {
            val payload = OutboxKanbanColumnPayload(boardId = current.boardId, name = name)
            current.outboxId?.let { outboxDao.updatePayload(it, json.encodeToString(payload)) }
            current.outboxId
        } else {
            current.outboxId?.let { outboxDao.delete(it) }
            val payload = OutboxKanbanColumnUpdatePayload(serverId = current.serverId, name = name)
            outboxDao.insert(
                OutboxMutationEntity(
                    type = OutboxMutationEntity.TYPE_UPDATE_KANBAN_COLUMN,
                    payloadJson = json.encodeToString(payload),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }

        columnDao.updateFields(localId, name, SyncStatus.PENDING, outboxId)
        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Reorders [localId] to [targetIndex] among its own board's columns —
     * local reorder is instant (renumbers every sibling column to a
     * contiguous 0..N-1 range, same as the backend's own `MoveKanbanColumn`),
     * a single outbox mutation carries just the final index. Always queues a
     * fresh mutation regardless of the column's own sync state — position is
     * never part of the create payload (the backend always appends a new
     * column at the end), so a column moved before its own create has synced
     * still needs this once that create goes through.
     */
    suspend fun moveColumn(localId: Long, targetIndex: Int) {
        val current = columnDao.getById(localId) ?: return
        val siblings = columnDao.getByBoardId(current.boardId).sortedBy { it.position }.filterNot { it.id == localId }
        val clampedIndex = targetIndex.coerceIn(0, siblings.size)
        val reordered = siblings.toMutableList().apply { add(clampedIndex, current) }
        reordered.forEachIndexed { index, column -> columnDao.updatePosition(column.id, index) }

        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_MOVE_KANBAN_COLUMN,
                payloadJson = json.encodeToString(OutboxKanbanColumnMovePayload(localColumnId = localId, index = clampedIndex)),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Offline-first delete — refuses (returns `false`, no-op) when the
     * column still has cards, mirroring the backend's own
     * `ErrKanbanColumnNotEmpty` guard rather than letting a 409 surface
     * from a background sync pass long after the local delete already
     * happened.
     */
    suspend fun deleteColumn(localId: Long): Boolean {
        val current = columnDao.getById(localId) ?: return true
        val columnPublicId = current.publicId
        if (cardDao.getByColumnId(columnPublicId).isNotEmpty()) return false

        current.outboxId?.let { outboxDao.delete(it) }
        columnDao.delete(localId)

        val serverId = current.serverId ?: return true
        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_DELETE_KANBAN_COLUMN,
                payloadJson = json.encodeToString(OutboxKanbanColumnDeletePayload(serverId = serverId)),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
        return true
    }

    // Clears a column's cards (and their own checklist/tags/alarms/pending
    // mutations) locally — used by deleteBoard, which bypasses deleteColumn's
    // "must be empty" guard since the whole board is going away regardless.
    private suspend fun deleteColumnCascadeLocal(columnPublicId: String) {
        cardDao.getByColumnId(columnPublicId).forEach { card ->
            deleteCardCascadeLocal(card)
            card.outboxId?.let { outboxDao.delete(it) }
        }
        cardDao.deleteByColumnId(columnPublicId)
    }

    private suspend fun deleteCardCascadeLocal(card: KanbanCardEntity) {
        KanbanCardAlarmScheduler.cancel(context, alarmIdFor(card.id))
        checklistDao.getByCardId(card.publicId).forEach { it.outboxId?.let { id -> outboxDao.delete(id) } }
        checklistDao.deleteByCardId(card.publicId)
        tagDao.deleteByCardId(card.id)
    }

    suspend fun getTags(localCardId: Long): List<TagEntity> = tagDao.getByCardId(localCardId)

    suspend fun suggestTags(query: String): List<String> = tagRepository.suggest(query)

    suspend fun createCard(
        columnId: String,
        title: String,
        description: String,
        dueDate: String?,
        priority: String,
        linkedNoteId: String?,
        tags: List<String>,
    ) {
        val position = (cardDao.getByColumnId(columnId).maxOfOrNull { it.position } ?: -1) + 1
        val payload = OutboxKanbanCardPayload(
            columnId = columnId, title = title, description = description, dueDate = dueDate,
            priority = priority, linkedNoteId = linkedNoteId, tags = tags,
        )
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_KANBAN_CARD,
                payloadJson = json.encodeToString(payload),
                createdAt = System.currentTimeMillis(),
            ),
        )
        val localId = cardDao.upsert(
            KanbanCardEntity(
                outboxId = outboxId, columnId = columnId, position = position, title = title, description = description,
                dueDate = dueDate, priority = priority, linkedNoteId = linkedNoteId, syncStatus = SyncStatus.PENDING,
            ),
        )
        // Color unknown for a brand-new local-optimistic tag until the next
        // refreshFromBackend() pull resolves it - blank falls back to UrsPill's
        // own default styling, same as NoteRepository.createNote's own tags.
        tagDao.insertAll(tags.map { TagEntity(cardId = localId, tagName = it, color = "") })
        // Never yet synced — its own publicId is always the local stand-in
        // at this point, see KanbanCardEntity.publicId.
        dueDate?.let { scheduleCardReminder(localId, localIdStandIn(localId), it, title) }
        applicationScope.launch { syncManager.syncNow() }
    }

    /** Offline-first update — always queues a fresh, localId-addressed mutation, same shape as [NoteRepository.updateNote]. */
    suspend fun updateCard(
        localId: Long,
        title: String,
        description: String,
        dueDate: String?,
        priority: String,
        linkedNoteId: String?,
        tags: List<String>,
    ) {
        val current = cardDao.getById(localId) ?: return
        cardDao.updateFields(localId, title, description, dueDate, priority, linkedNoteId, SyncStatus.PENDING)
        tagDao.deleteByCardId(localId)
        // Same local-optimistic placeholder as createCard - corrected by the next refreshFromBackend() pull.
        tagDao.insertAll(tags.map { TagEntity(cardId = localId, tagName = it, color = "") })

        KanbanCardAlarmScheduler.cancel(context, alarmIdFor(localId))
        dueDate?.let { scheduleCardReminder(localId, current.publicId, it, title) }

        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_UPDATE_KANBAN_CARD,
                payloadJson = json.encodeToString(
                    OutboxKanbanCardUpdatePayload(
                        localCardId = localId, title = title, description = description, dueDate = dueDate,
                        priority = priority, linkedNoteId = linkedNoteId, tags = tags,
                    ),
                ),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Reorders [localId] to [targetIndex] within [targetColumnId] — which
     * may be its current column (pure reorder) or a different one on the
     * same board (cross-column move). Local reorder is instant, renumbering
     * every affected column's cards to a contiguous 0..N-1 range, same as
     * [moveColumn]. Always queues a fresh mutation — same "position is never
     * part of the create payload" reasoning as [moveColumn].
     */
    suspend fun moveCard(localId: Long, targetColumnId: String, targetIndex: Int) {
        val current = cardDao.getById(localId) ?: return
        val sourceColumnId = current.columnId

        val targetSiblings = cardDao.getByColumnId(targetColumnId).sortedBy { it.position }.filterNot { it.id == localId }
        val clampedIndex = targetIndex.coerceIn(0, targetSiblings.size)
        val reorderedTarget = targetSiblings.toMutableList().apply { add(clampedIndex, current.copy(columnId = targetColumnId)) }
        reorderedTarget.forEachIndexed { index, card ->
            cardDao.updatePosition(card.id, targetColumnId, index, if (card.id == localId) SyncStatus.PENDING else card.syncStatus)
        }

        if (sourceColumnId != targetColumnId) {
            val sourceSiblings = cardDao.getByColumnId(sourceColumnId).filterNot { it.id == localId }.sortedBy { it.position }
            sourceSiblings.forEachIndexed { index, card -> cardDao.updatePosition(card.id, sourceColumnId, index, card.syncStatus) }
        }

        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_MOVE_KANBAN_CARD,
                payloadJson = json.encodeToString(
                    OutboxKanbanCardMovePayload(localCardId = localId, targetColumnId = targetColumnId, index = clampedIndex),
                ),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    /** Offline-first delete — same shape as [ShoppingListRepository.deleteItem], cascaded to checklist/tags/alarm first. */
    suspend fun deleteCard(localId: Long) {
        val current = cardDao.getById(localId) ?: return
        deleteCardCascadeLocal(current)
        current.outboxId?.let { outboxDao.delete(it) }
        cardDao.delete(localId)

        val serverId = current.serverId ?: return
        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_DELETE_KANBAN_CARD,
                payloadJson = json.encodeToString(OutboxKanbanCardDeletePayload(serverId = serverId)),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    suspend fun addChecklistItem(cardId: String, text: String) {
        val position = (checklistDao.getByCardId(cardId).maxOfOrNull { it.position } ?: -1) + 1
        val payload = OutboxKanbanChecklistItemPayload(cardId = cardId, text = text)
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_KANBAN_CHECKLIST_ITEM,
                payloadJson = json.encodeToString(payload),
                createdAt = System.currentTimeMillis(),
            ),
        )
        checklistDao.upsert(
            KanbanChecklistItemEntity(outboxId = outboxId, cardId = cardId, text = text, position = position, syncStatus = SyncStatus.PENDING),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    /** Offline-first toggle — always queues a fresh, localId-addressed mutation, same shape as [updateCard]. */
    suspend fun toggleChecklistItem(localItemId: Long, done: Boolean) {
        val current = checklistDao.getById(localItemId) ?: return
        checklistDao.updateFields(localItemId, current.text, done, SyncStatus.PENDING)
        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_UPDATE_KANBAN_CHECKLIST_ITEM,
                payloadJson = json.encodeToString(
                    OutboxKanbanChecklistItemUpdatePayload(localItemId = localItemId, text = current.text, done = done),
                ),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    suspend fun deleteChecklistItem(localItemId: Long) {
        val current = checklistDao.getById(localItemId) ?: return
        current.outboxId?.let { outboxDao.delete(it) }
        checklistDao.delete(localItemId)

        val serverId = current.serverId ?: return
        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_DELETE_KANBAN_CHECKLIST_ITEM,
                payloadJson = json.encodeToString(OutboxKanbanChecklistItemDeletePayload(serverId = serverId)),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Re-arms every active card's due-date reminder — called after a reboot
     * (exact alarms don't survive it) from
     * [ch.mcfx.urs.notifications.BootCompletedReceiver], same shape as
     * [NoteRepository.rearmPendingReminders].
     */
    suspend fun rearmPendingReminders() {
        cardDao.observeAll().first().forEach { card ->
            card.dueDate?.let { scheduleCardReminder(card.id, card.publicId, it, card.title) }
        }
    }

    /**
     * Opportunistic backend refresh for the boards hub — pulls the (cheap,
     * name-only) board list, same best-effort shape as [ShoppingListRepository
     * .refreshFromBackend]. Board detail (columns/cards/checklist/tags) is
     * pulled separately per board, see [refreshBoard].
     */
    suspend fun refreshBoards(): Boolean = refreshQuietly {
        val boards = emptyAsNull { api.getKanbanBoards() }
        boardDao.upsertFromServer(boards.map { it.toEntity() })
    }

    /**
     * Opportunistic backend refresh for one board's full tree — the server
     * response is the whole nested board already, so this is a
     * straightforward cache-replace of that board's columns/cards/checklist/
     * tags, same shape as [ShoppingListRepository.refreshFromBackend]'s
     * per-list item pull. No-ops (returns `true`) for a board that hasn't
     * synced yet — there's no server id to pull with.
     */
    suspend fun refreshBoard(boardId: String): Boolean {
        val localId = localKanbanBoardId(boardId)
        val serverId = if (localId != null) boardDao.getById(localId)?.serverId else boardId
        if (serverId == null) return true

        return refreshQuietly {
            val detail = api.getKanbanBoard(serverId)
            boardDao.upsertOne(detail.toEntity())
            columnDao.upsertFromServer(serverId, detail.columns.map { it.toEntity() })
            detail.columns.forEach { column ->
                val cards = column.cards
                val resolvedLocalCardIds = cardDao.upsertFromServer(column.id, cards.map { it.toEntity() })
                cards.zip(resolvedLocalCardIds).forEach { (cardDto, localCardId) ->
                    checklistDao.upsertFromServer(cardDto.id, cardDto.checklist.map { it.toEntity() })
                    tagDao.deleteByCardId(localCardId)
                    tagDao.insertAll(cardDto.tags.map { TagEntity(cardId = localCardId, tagName = it.name, color = it.color) })
                    rescheduleReminderFromServer(localCardId, cardDto)
                }
            }
        }
    }

    /**
     * Pulls every board's own detail after the board list itself — the
     * PullCoordinator-facing entry point, same "list then per-item detail"
     * two-level shape as [ShoppingListRepository.refreshFromBackend].
     */
    suspend fun refreshFromBackend(): Boolean {
        syncManager.syncNow()
        val boardsOk = refreshBoards()
        val detailsOk = boardDao.observeAll().first().mapNotNull { it.serverId }.map { serverId ->
            refreshBoard(serverId)
        }.all { it }
        return boardsOk && detailsOk
    }

    // Re-syncs a pulled card's alarm against the server's own due date —
    // covers a due date changed on another device, which would otherwise
    // leave a stale local alarm armed (or a new one unarmed) until this
    // card is next edited locally.
    private fun rescheduleReminderFromServer(localCardId: Long, cardDto: KanbanCardDto) {
        KanbanCardAlarmScheduler.cancel(context, alarmIdFor(localCardId))
        cardDto.dueDate.takeIf { it.isNotBlank() }?.let { scheduleCardReminder(localCardId, cardDto.id, it, cardDto.title) }
    }

    private fun scheduleCardReminder(localCardId: Long, cardId: String, dueDate: String, title: String) {
        val date = runCatching { LocalDate.parse(dueDate) }.getOrNull() ?: return
        val triggerAtMillis = date.atTime(REMINDER_HOUR, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        KanbanCardAlarmScheduler.scheduleCardAlarm(context, alarmIdFor(localCardId), triggerAtMillis, cardId, title)
    }

    // Reserved id range — see BakingRepository.BAKING_STEP_ALARM_ID_BASE's
    // doc comment for the sibling ranges this must not collide with.
    private fun alarmIdFor(localId: Long) = KANBAN_CARD_REMINDER_ID_BASE + (localId.toInt() and 0xFFFF)

    private suspend fun refreshQuietly(block: suspend () -> Unit): Boolean {
        try {
            block()
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best-effort only — see ShoppingListRepository.refreshFromBackend's doc comment.
            android.util.Log.w("KanbanRepository", "refresh failed", e)
            return false
        }
    }

    // The backend encodes empty result sets as JSON `null` instead of `[]`.
    private suspend fun <T> emptyAsNull(call: suspend () -> List<T>): List<T> =
        try {
            call()
        } catch (_: SerializationException) {
            emptyList()
        }

    companion object {
        // Fixed local trigger time for every card's due-date reminder — no
        // per-card time-of-day picker, no global settings screen (owner's
        // own call, see the feature's tracking issue).
        private val REMINDER_HOUR = LocalTime.of(8, 0).hour
        private const val KANBAN_CARD_REMINDER_ID_BASE = 400_000
    }
}

private fun KanbanBoardDto.toEntity() = KanbanBoardEntity(serverId = id, outboxId = null, name = name, syncStatus = SyncStatus.SYNCED)

private fun KanbanBoardDetailDto.toEntity() = KanbanBoardEntity(serverId = id, outboxId = null, name = name, syncStatus = SyncStatus.SYNCED)

private fun KanbanColumnDto.toEntity() = KanbanColumnEntity(
    serverId = id, outboxId = null, boardId = boardId, name = name, position = index, syncStatus = SyncStatus.SYNCED,
)

private fun KanbanCardDto.toEntity() = KanbanCardEntity(
    serverId = id, outboxId = null, columnId = columnId, position = index, title = title, description = description,
    dueDate = dueDate.ifBlank { null }, priority = priority, linkedNoteId = noteId.ifBlank { null }, syncStatus = SyncStatus.SYNCED,
)

private fun KanbanChecklistItemDto.toEntity() = KanbanChecklistItemEntity(
    serverId = id, outboxId = null, cardId = cardId, text = text, done = done, position = index, syncStatus = SyncStatus.SYNCED,
)
