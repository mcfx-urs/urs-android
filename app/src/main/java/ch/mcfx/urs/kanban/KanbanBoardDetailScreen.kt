package ch.mcfx.urs.kanban

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.chores.parseChoreColor
import ch.mcfx.urs.data.KanbanBoardDetail
import ch.mcfx.urs.data.KanbanCardWithDetails
import ch.mcfx.urs.data.KanbanColumnWithCards
import ch.mcfx.urs.data.local.KanbanCardEntity
import ch.mcfx.urs.data.local.KanbanChecklistItemEntity
import ch.mcfx.urs.data.local.NoteEntity
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.notes.TagFilterRow
import ch.mcfx.urs.notes.richtext.RichTextField
import ch.mcfx.urs.notes.richtext.RichTextFieldState
import ch.mcfx.urs.notes.richtext.RichTextLinkSheetHost
import ch.mcfx.urs.notes.richtext.rememberRichTextFieldState
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsCheckbox
import ch.mcfx.urs.ui.components.UrsConfirmBar
import ch.mcfx.urs.ui.components.UrsDateField
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsGlassCard
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import kotlin.math.abs
import kotlin.math.roundToInt

private val FormErrorColor = Color(0xFFD64545)
private val KanbanColumnWidth = 280.dp
private val PriorityDotSize = 10.dp
private val PriorityLowColor = Color(0xFF4CAF50)
private val PriorityMediumColor = Color(0xFFFFA726)
private val PriorityHighColor = FormErrorColor

@Composable
fun KanbanBoardDetailScreen(
    boardId: String,
    viewModel: KanbanBoardDetailViewModel = viewModel(factory = KanbanBoardDetailViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val saveConfirmation by viewModel.saveConfirmation.collectAsStateWithLifecycle()
    val cardEditor by viewModel.cardEditor.collectAsStateWithLifecycle()
    // Hoisted to this screen-level function (not KanbanBoardDetailOverlays,
    // where the card editor sheet itself lives) so RichTextLinkSheetHost
    // below can be placed as a sibling of the sheet, not nested inside it —
    // see that function's own doc comment for why a nested UrsBottomSheet
    // would get squeezed into the outer sheet's already-narrow constraints.
    val richTextState = rememberRichTextFieldState(cardEditor?.description ?: "")
    LaunchedEffect(boardId) { viewModel.loadBoard(boardId) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            KanbanBoardDetailUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))
            KanbanBoardDetailUiState.NotFound -> UrsText(
                stringResource(R.string.kanban_board_not_found),
                modifier = Modifier.align(Alignment.Center),
                color = UrsTheme.colors.onSurfaceMuted,
            )
            is KanbanBoardDetailUiState.Data -> BoardDetailContent(detail = state.detail, viewModel = viewModel)
        }

        if (saveConfirmation) {
            UrsConfirmBar(
                message = stringResource(R.string.kanban_card_saved),
                modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.l),
            )
        }
    }

    KanbanBoardDetailOverlays(viewModel = viewModel, cardEditor = cardEditor, richTextState = richTextState)

    RichTextLinkSheetHost(state = richTextState, onValueChange = viewModel::setCardDescription)
}

@Composable
private fun BoardDetailContent(detail: KanbanBoardDetail, viewModel: KanbanBoardDetailViewModel) {
    val selectedTags by viewModel.tagFilter.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val tagColors = remember(detail) { detail.columns.flatMap { it.cards }.flatMap { it.tags }.associate { it.tagName to it.color } }
    val allTags = remember(tagColors) { tagColors.keys.sorted() }
    // A filter whose tag no longer sits on any card (retagged/deleted since it was picked) is ignored.
    val tagFilter = selectedTags.filterTo(mutableSetOf()) { it in tagColors }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrsText(detail.board.name, style = UrsTheme.typography.screenTitle, modifier = Modifier.weight(1f))
            UrsOutlinedButton(
                text = stringResource(if (viewMode == KanbanViewMode.Board) R.string.kanban_view_list else R.string.kanban_view_board),
                onClick = viewModel::toggleViewMode,
            )
        }
        if (allTags.isNotEmpty()) {
            TagFilterRow(
                tags = allTags,
                tagColors = tagColors,
                selected = tagFilter,
                onToggle = viewModel::toggleTagFilter,
                onClear = viewModel::clearTagFilter,
            )
        }
        when (viewMode) {
            KanbanViewMode.Board -> BoardColumnsRow(detail = detail, tagFilter = tagFilter, viewModel = viewModel, modifier = Modifier.weight(1f))
            KanbanViewMode.List -> BoardListView(detail = detail, tagFilter = tagFilter, viewModel = viewModel, modifier = Modifier.weight(1f))
        }
    }
}

/** With a non-empty [tagFilter], only cards carrying every one of its tags. */
private fun KanbanColumnWithCards.visibleCards(tagFilter: Set<String>): List<KanbanCardWithDetails> =
    if (tagFilter.isEmpty()) cards else cards.filter { card -> tagFilter.all { selected -> card.tags.any { it.tagName == selected } } }

/**
 * Translates an insert position among a column's *visible* (tag-filtered)
 * siblings into the matching position among all its siblings, so a drop
 * onto a filtered column lands next to the cards actually on screen instead
 * of at the wrong offset among the hidden ones.
 */
private fun fullInsertIndex(fullSiblings: List<Long>, visibleSiblings: List<Long>, visibleIndex: Int): Int = when {
    visibleIndex < visibleSiblings.size -> fullSiblings.indexOf(visibleSiblings[visibleIndex])
    visibleSiblings.isEmpty() -> fullSiblings.size
    else -> fullSiblings.indexOf(visibleSiblings.last()) + 1
}

/** State of the Kanban card being dragged, shared by the board and the list layout. */
@Stable
private class CardDragState {
    var heldId by mutableStateOf<Long?>(null)
    var offset by mutableStateOf(Offset.Zero)
    var startBounds by mutableStateOf(Rect.Zero)

    /** Drop slot: the column and the position among its visible cards without the held one. */
    var dropColumnId by mutableStateOf<Long?>(null)
    var dropIndex by mutableStateOf(0)

    /**
     * Layout coordinates written by layout callbacks, only read by the drag handling. The bounds are
     * computed when read: a scroll moves the content without calling the callbacks again.
     */
    private val cardCoordinates = mutableMapOf<Long, LayoutCoordinates>()
    private val columnCoordinates = mutableMapOf<Long, LayoutCoordinates>()

    fun cardPositioned(cardId: Long, coordinates: LayoutCoordinates) {
        if (cardId != heldId) cardCoordinates[cardId] = coordinates
    }

    fun columnPositioned(columnId: Long, coordinates: LayoutCoordinates) {
        columnCoordinates[columnId] = coordinates
    }

    fun cardBounds(cardId: Long): Rect? = cardCoordinates[cardId]?.takeIf { it.isAttached }?.unclippedBoundsInRoot()

    fun columnBounds(columnId: Long): Rect? = columnCoordinates[columnId]?.takeIf { it.isAttached }?.unclippedBoundsInRoot()

    /** Where the card was touched, in root coordinates. */
    private var pointerStart = Offset.Zero

    val heldCenter: Offset get() = Offset(startBounds.center.x + offset.x, startBounds.center.y + offset.y)

    /** The finger: edge scrolling follows it, not the card centre, so a card grabbed off-centre can still reach either edge. */
    val pointer: Offset get() = Offset(pointerStart.x + offset.x, pointerStart.y + offset.y)

    fun start(cardId: Long, sourceColumnId: Long?, sourceIndex: Int, touch: Offset) {
        startBounds = cardBounds(cardId) ?: Rect.Zero
        pointerStart = Offset(startBounds.left + touch.x, startBounds.top + touch.y)
        offset = Offset.Zero
        dropColumnId = sourceColumnId
        dropIndex = sourceIndex
        heldId = cardId
    }

    fun reset() {
        heldId = null
        dropColumnId = null
        offset = Offset.Zero
    }

    /**
     * Points the drop slot at [columnId]. A card counts as above the slot once its centre lies above
     * the held card's centre; the cards shifted by the slot move back over the same line, so it cannot flap.
     */
    fun aim(columnId: Long, visibleIds: List<Long>) {
        dropColumnId = columnId
        val centerY = heldCenter.y
        dropIndex = visibleIds.count { id -> id != heldId && cardBounds(id)?.let { centerY > it.center.y } == true }
    }
}

private fun cardSource(visibleByColumn: Map<Long, List<Long>>, cardId: Long): Pair<Long, Int>? =
    visibleByColumn.entries.firstOrNull { cardId in it.value }?.let { it.key to it.value.indexOf(cardId) }

private fun KanbanBoardDetailViewModel.dropCard(detail: KanbanBoardDetail, visibleByColumn: Map<Long, List<Long>>, cardId: Long, columnId: Long, visibleIndex: Int) {
    val column = detail.columns.firstOrNull { it.column.id == columnId } ?: return
    val visibleSiblings = visibleByColumn[columnId].orEmpty().filter { it != cardId }
    val targetIndex = fullInsertIndex(
        fullSiblings = column.cards.map { it.card.id }.filter { it != cardId },
        visibleSiblings = visibleSiblings,
        visibleIndex = visibleIndex.coerceIn(0, visibleSiblings.size),
    )
    moveCard(cardId, column.column.publicId, targetIndex)
}

/** A shorter hold than the system default starts the drag of a card or column. */
@Composable
private fun rememberDragViewConfiguration(): ViewConfiguration {
    val viewConfiguration = LocalViewConfiguration.current
    return remember(viewConfiguration) {
        object : ViewConfiguration by viewConfiguration {
            override val longPressTimeoutMillis: Long = DragLongPressTimeoutMillis
        }
    }
}

/**
 * A card tile that can be dragged. While it is held the tile stays composed (it owns the running
 * gesture) but takes no space and draws nothing; [CardDragOverlay] draws the card instead.
 */
@Composable
private fun DraggableCardTile(
    cardWithDetails: KanbanCardWithDetails,
    drag: CardDragState,
    tagFilter: Set<String>,
    sourceOf: (cardId: Long) -> Pair<Long, Int>?,
    onDrop: (cardId: Long, columnId: Long, visibleIndex: Int) -> Unit,
    onOpen: () -> Unit,
) {
    val cardId = cardWithDetails.card.id
    val isDragging = cardId == drag.heldId
    val latestSourceOf by rememberUpdatedState(sourceOf)
    val latestOnDrop by rememberUpdatedState(onDrop)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .collapsed(isDragging)
            .graphicsLayer { alpha = if (isDragging) 0f else 1f }
            .padding(bottom = Spacing.s),
    ) {
        UrsCard(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { drag.cardPositioned(cardId, it) }
                .pointerInput(cardId, tagFilter) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { touch ->
                            val source = latestSourceOf(cardId)
                            drag.start(cardId, source?.first, source?.second ?: 0, touch)
                        },
                        // The only place a move is actually decided/saved: from the slot shown at that moment.
                        onDragEnd = {
                            val columnId = drag.dropColumnId
                            val visibleIndex = drag.dropIndex
                            drag.reset()
                            if (columnId != null) latestOnDrop(cardId, columnId, visibleIndex)
                        },
                        onDragCancel = { drag.reset() },
                        onDrag = { change, delta -> change.consume(); drag.offset += delta },
                    )
                }
                .clickable(onClick = onOpen),
            contentPadding = PaddingValues(Spacing.m),
        ) {
            CardTileContent(cardWithDetails)
        }
    }
}

/**
 * The cards of one column with the faded drop slot of the held card inserted where it would land.
 * [cardTile] has a single call site on purpose: a second one would recreate the held tile when it
 * starts to be dragged and end its running gesture.
 */
@Composable
private fun ColumnCardList(
    columnId: Long,
    visibleCards: List<KanbanCardWithDetails>,
    heldCard: KanbanCardWithDetails?,
    drag: CardDragState,
    cardTile: @Composable (KanbanCardWithDetails) -> Unit,
) {
    val showSlot = heldCard != null && drag.dropColumnId == columnId
    var slotIndex = 0
    visibleCards.forEach { cardWithDetails ->
        val isHeld = cardWithDetails.card.id == drag.heldId
        if (!isHeld && showSlot && slotIndex == drag.dropIndex) key(DropSlotKey) { CardDropSlot(heldCard!!) }
        // A card keeps its own identity (and its live drag pointerInput) rather than
        // having some other card's composable slot reused for it.
        key(cardWithDetails.card.id) { cardTile(cardWithDetails) }
        if (!isHeld) slotIndex++
    }
    if (showSlot && slotIndex == drag.dropIndex) key(DropSlotKey) { CardDropSlot(heldCard!!) }
}

/** The held card, drawn above its container so no scroll container clips it while it moves. */
@Composable
private fun CardDragOverlay(drag: CardDragState, heldCard: KanbanCardWithDetails?, containerBounds: Rect) {
    if (heldCard == null) return
    val density = LocalDensity.current
    UrsCard(
        modifier = Modifier
            .width(with(density) { drag.startBounds.width.toDp() })
            .offset {
                IntOffset(
                    (drag.startBounds.left - containerBounds.left + drag.offset.x).roundToInt(),
                    (drag.startBounds.top - containerBounds.top + drag.offset.y).roundToInt(),
                )
            },
        contentPadding = PaddingValues(Spacing.m),
    ) {
        CardTileContent(heldCard)
    }
}

/**
 * The list layout: each column a labeled section with its cards underneath, one vertical scroll.
 * Cards are dragged like on the board; the slot is picked by the vertical position.
 */
@Composable
private fun BoardListView(detail: KanbanBoardDetail, tagFilter: Set<String>, viewModel: KanbanBoardDetailViewModel, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val edgeScrollZonePx = with(density) { EdgeScrollZone.toPx() }
    val edgeScrollMaxStepPx = with(density) { EdgeScrollMaxStep.toPx() }

    val visibleCardIdsByColumn = detail.columns.associate { it.column.id to it.visibleCards(tagFilter).map { c -> c.card.id } }
    val cardById = remember(detail) { detail.columns.flatMap { it.cards }.associateBy { it.card.id } }
    val latestDetail by rememberUpdatedState(detail)
    val latestVisibleCardIds by rememberUpdatedState(visibleCardIdsByColumn)

    val cardDrag = remember { CardDragState() }
    val scrollState = rememberScrollState()
    var viewBounds by remember { mutableStateOf(Rect.Zero) }

    LaunchedEffect(cardDrag.heldId) {
        if (cardDrag.heldId == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val center = cardDrag.heldCenter
            val targetColumnId = latestDetail.columns.map { it.column.id }.minByOrNull { id ->
                cardDrag.columnBounds(id)?.let { abs(center.y - center.y.coerceIn(it.top, it.bottom)) } ?: Float.MAX_VALUE
            } ?: continue
            cardDrag.aim(targetColumnId, latestVisibleCardIds[targetColumnId].orEmpty())

            val delta = edgeScrollDelta(cardDrag.pointer.y, viewBounds.top, viewBounds.bottom, edgeScrollZonePx, edgeScrollMaxStepPx)
            if (delta != 0f) scrollState.scrollBy(delta)
        }
    }

    CompositionLocalProvider(LocalViewConfiguration provides rememberDragViewConfiguration()) {
        Box(modifier = modifier.fillMaxWidth().onGloballyPositioned { viewBounds = it.boundsInRoot() }) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = Spacing.l, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                detail.columns.forEach { columnWithCards ->
                    val columnId = columnWithCards.column.id
                    val cards = columnWithCards.visibleCards(tagFilter)
                    key(columnId) {
                        UrsGlassCard(
                            modifier = Modifier.fillMaxWidth().onGloballyPositioned { cardDrag.columnPositioned(columnId, it) },
                            contentPadding = PaddingValues(horizontal = Spacing.s, vertical = Spacing.xs),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.openColumnActionSheet(columnWithCards.column) }
                                        .padding(vertical = Spacing.s),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    UrsText(columnWithCards.column.name, style = UrsTheme.typography.cardTitle, modifier = Modifier.weight(1f))
                                    UrsText(cards.size.toString(), style = UrsTheme.typography.caption, color = UrsTheme.colors.onSurfaceMuted)
                                }
                                Column {
                                    ColumnCardList(columnId, cards, cardDrag.heldId?.let { cardById[it] }, cardDrag) { cardWithDetails ->
                                        DraggableCardTile(
                                            cardWithDetails = cardWithDetails,
                                            drag = cardDrag,
                                            tagFilter = tagFilter,
                                            sourceOf = { id -> cardSource(latestVisibleCardIds, id) },
                                            onDrop = { id, targetColumnId, visibleIndex ->
                                                viewModel.dropCard(latestDetail, latestVisibleCardIds, id, targetColumnId, visibleIndex)
                                            },
                                            onOpen = { viewModel.openCardEditor(cardWithDetails.card, cardWithDetails.tags) },
                                        )
                                    }
                                    AddCardRow(onClick = { viewModel.openCreateCardEditor(columnWithCards.column.publicId) })
                                }
                            }
                        }
                    }
                }
                AddColumnTile(onClick = viewModel::openCreateColumnForm)
            }
            CardDragOverlay(cardDrag, cardDrag.heldId?.let { cardById[it] }, viewBounds)
        }
    }
}

/**
 * The horizontally-scrollable row of columns, each a vertically-arranged
 * list of cards — the drag-and-drop core of the feature.
 *
 * A held card is drawn by an overlay above the board (neither scroll
 * container clips it), while its original tile collapses to zero size. A
 * faded copy of the card marks the drop slot: every frame the slot is derived
 * from the held card's position against the measured bounds of the columns
 * and the other cards, so the cards under it make room. The board and the
 * column under the card scroll when it is held near their edges.
 *
 * A held column moves with the finger while the other columns make room for
 * it: the slot is the column position nearest to where it is held, and the
 * board scrolls when it is held near the left or right edge.
 *
 * Column composables below are local functions (not separate
 * top-level `@Composable`s) so they can read/write this function's own
 * drag-state `var`s directly as closures, rather than threading a dozen
 * callbacks down through parameters.
 */
@Composable
private fun BoardColumnsRow(detail: KanbanBoardDetail, tagFilter: Set<String>, viewModel: KanbanBoardDetailViewModel, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val columnWidthPx = with(density) { KanbanColumnWidth.toPx() }
    val columnStepPx = columnWidthPx + with(density) { Spacing.m.toPx() }
    val edgeScrollZonePx = with(density) { EdgeScrollZone.toPx() }
    val edgeScrollMaxStepPx = with(density) { EdgeScrollMaxStep.toPx() }

    // Column/card membership always mirrors the real, persisted board — a
    // held card only floats in the overlay and leaves a collapsed tile behind
    // in its own column, it is never re-parented into another column's list
    // while the gesture is held (that would drop the live pointerInput). The
    // move is saved exactly once, in onDragEnd, from the slot shown at that
    // moment. Columns are the one thing shown reordered during the hold: they
    // are keyed by id in the same row, which keeps their composition.
    val orderedColumnIds = detail.columns.map { it.column.id }
    // Cards actually on screen under the current tag filter. Drop slots are
    // computed against this, then mapped back onto the full list (fullInsertIndex).
    val visibleCardIdsByColumn = detail.columns.associate { it.column.id to it.visibleCards(tagFilter).map { c -> c.card.id } }
    // Pure display/lookup maps, deliberately not gated on the narrow id
    // shape above — these must always reflect the latest field values (a
    // card's title/tags/etc.) even while some other card is mid-drag.
    val columnById = remember(detail) { detail.columns.associateBy { it.column.id } }
    val cardById = remember(detail) { detail.columns.flatMap { it.cards }.associateBy { it.card.id } }

    // Read from the long-lived gesture and frame-loop coroutines below, which
    // would otherwise keep the values of the composition they were launched in.
    val latestDetail by rememberUpdatedState(detail)
    val latestColumnIds by rememberUpdatedState(orderedColumnIds)
    val latestVisibleCardIds by rememberUpdatedState(visibleCardIdsByColumn)

    val cardDrag = remember { CardDragState() }
    val columnScrollStates = remember { mutableMapOf<Long, ScrollState>() }
    val boardScrollState = rememberScrollState()
    var boardBounds by remember { mutableStateOf(Rect.Zero) }

    var draggingColumnId by remember { mutableStateOf<Long?>(null) }
    var columnDragOffsetX by remember { mutableStateOf(0f) }
    var columnStartIndex by remember { mutableStateOf(0) }
    var columnStartScroll by remember { mutableStateOf(0) }
    var columnPointerStartX by remember { mutableStateOf(0f) }
    var columnDropIndex by remember { mutableStateOf(0) }

    val displayedColumnIds = draggingColumnId?.let { heldId ->
        orderedColumnIds.filter { it != heldId }.toMutableList().apply { add(columnDropIndex.coerceIn(0, size), heldId) }
    } ?: orderedColumnIds

    LaunchedEffect(cardDrag.heldId) {
        if (cardDrag.heldId == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val center = cardDrag.heldCenter
            val targetColumnId = latestColumnIds.minByOrNull { id ->
                cardDrag.columnBounds(id)?.let { abs(center.x - center.x.coerceIn(it.left, it.right)) } ?: Float.MAX_VALUE
            } ?: continue
            cardDrag.aim(targetColumnId, latestVisibleCardIds[targetColumnId].orEmpty())

            val board = boardBounds
            val boardDelta = edgeScrollDelta(cardDrag.pointer.x, board.left, board.right, edgeScrollZonePx, edgeScrollMaxStepPx)
            if (boardDelta != 0f) boardScrollState.scrollBy(boardDelta)
            val list = cardDrag.columnBounds(targetColumnId) ?: continue
            val listDelta = edgeScrollDelta(cardDrag.pointer.y, list.top, list.bottom, edgeScrollZonePx, edgeScrollMaxStepPx)
            if (listDelta != 0f) columnScrollStates[targetColumnId]?.scrollBy(listDelta)
        }
    }

    LaunchedEffect(draggingColumnId) {
        if (draggingColumnId == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            // Left edge of the held column in the scrolled content, and the slot nearest to it.
            val contentX = columnStartIndex * columnStepPx + columnDragOffsetX + (boardScrollState.value - columnStartScroll)
            columnDropIndex = (contentX / columnStepPx).roundToInt().coerceIn(0, latestColumnIds.lastIndex)

            // The scroll follows the finger, not the column: the finger stays put on screen while the board scrolls.
            val board = boardBounds
            val delta = edgeScrollDelta(columnPointerStartX + columnDragOffsetX, board.left, board.right, edgeScrollZonePx, edgeScrollMaxStepPx)
            if (delta != 0f) boardScrollState.scrollBy(delta)
        }
    }

    @Composable
    fun ColumnHeader(columnWithCards: KanbanColumnWithCards, visibleCount: Int) {
        val columnId = columnWithCards.column.id
        val headerCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { headerCoordinates[0] = it }
                .pointerInput(columnId) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { touch ->
                            columnPointerStartX = (headerCoordinates[0]?.takeIf { it.isAttached }?.boundsInRoot()?.left ?: 0f) + touch.x
                            columnStartIndex = latestColumnIds.indexOf(columnId)
                            columnStartScroll = boardScrollState.value
                            columnDropIndex = columnStartIndex
                            columnDragOffsetX = 0f
                            draggingColumnId = columnId
                        },
                        // Decided only here, from the slot shown at that moment.
                        onDragEnd = {
                            val targetIndex = columnDropIndex
                            draggingColumnId = null
                            columnDragOffsetX = 0f
                            viewModel.moveColumn(columnId, targetIndex)
                        },
                        onDragCancel = { draggingColumnId = null; columnDragOffsetX = 0f },
                        onDrag = { change, delta -> change.consume(); columnDragOffsetX += delta.x },
                    )
                }
                .clickable { viewModel.openColumnActionSheet(columnWithCards.column) }
                .padding(vertical = Spacing.s),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrsText(columnWithCards.column.name, style = UrsTheme.typography.cardTitle, modifier = Modifier.weight(1f))
            UrsText(
                visibleCount.toString(),
                style = UrsTheme.typography.caption,
                color = UrsTheme.colors.onSurfaceMuted,
            )
        }
    }

    CompositionLocalProvider(LocalViewConfiguration provides rememberDragViewConfiguration()) {
        Box(modifier = modifier.fillMaxWidth().onGloballyPositioned { boardBounds = it.boundsInRoot() }) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(boardScrollState)
                    .padding(horizontal = Spacing.l, vertical = Spacing.s),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                displayedColumnIds.forEach { columnId ->
                    val columnWithCards = columnById[columnId] ?: return@forEach
                    val isColumnDragging = columnId == draggingColumnId
                    // Keyed by columnId (not just positional) — without this, reordering
                    // columns (or a card crossing into a column that shifts this one's
                    // slot) makes Compose reuse this composable's slot for a *different*
                    // column's data, which cancels and restarts the columnId-keyed
                    // pointerInput below mid-gesture instead of letting the drag continue.
                    key(columnId) {
                        UrsGlassCard(
                            modifier = Modifier
                                .width(KanbanColumnWidth)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    // The row lays the held column out at its slot; the offset moves it back under the finger.
                                    translationX = if (isColumnDragging) {
                                        (columnStartIndex - columnDropIndex) * columnStepPx + columnDragOffsetX + (boardScrollState.value - columnStartScroll)
                                    } else {
                                        0f
                                    }
                                }
                                .zIndex(if (isColumnDragging) 1f else 0f),
                            contentPadding = PaddingValues(horizontal = Spacing.s, vertical = Spacing.xs),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                ColumnHeader(columnWithCards, visibleCardIdsByColumn[columnId].orEmpty().size)
                                val columnScrollState = rememberScrollState()
                                columnScrollStates[columnId] = columnScrollState
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .onGloballyPositioned { cardDrag.columnPositioned(columnId, it) }
                                        .verticalScroll(columnScrollState),
                                ) {
                                    ColumnCardList(columnId, columnWithCards.visibleCards(tagFilter), cardDrag.heldId?.let { cardById[it] }, cardDrag) { cardWithDetails ->
                                        DraggableCardTile(
                                            cardWithDetails = cardWithDetails,
                                            drag = cardDrag,
                                            tagFilter = tagFilter,
                                            sourceOf = { id -> cardSource(latestVisibleCardIds, id) },
                                            onDrop = { id, targetColumnId, visibleIndex ->
                                                viewModel.dropCard(latestDetail, latestVisibleCardIds, id, targetColumnId, visibleIndex)
                                            },
                                            onOpen = { viewModel.openCardEditor(cardWithDetails.card, cardWithDetails.tags) },
                                        )
                                    }
                                    AddCardRow(onClick = { viewModel.openCreateCardEditor(columnWithCards.column.publicId) })
                                }
                            }
                        }
                    }
                }
                AddColumnTile(onClick = viewModel::openCreateColumnForm)
            }
            CardDragOverlay(cardDrag, cardDrag.heldId?.let { cardById[it] }, boardBounds)
        }
    }
}

private const val DragLongPressTimeoutMillis = 250L
private val EdgeScrollZone = 56.dp
private val EdgeScrollMaxStep = 16.dp
private const val DropSlotKey = "drop-slot"

/** Faded copy of the held card, shown where it would land and pushing the cards below it down. */
@Composable
private fun CardDropSlot(cardWithDetails: KanbanCardWithDetails) {
    Box(Modifier.fillMaxWidth().graphicsLayer { alpha = 0.3f }.padding(bottom = Spacing.s)) {
        UrsCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.m)) {
            CardTileContent(cardWithDetails)
        }
    }
}

/** Bounds in root coordinates without the clipping of scroll containers: [boundsInRoot] collapses a scrolled-out layout. */
private fun LayoutCoordinates.unclippedBoundsInRoot(): Rect = Rect(positionInRoot(), size.toSize())

/** Measures like a zero-size layout when [collapsed], keeping the node (and its running gesture) in place. */
private fun Modifier.collapsed(collapsed: Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    if (collapsed) layout(0, 0) { placeable.place(0, 0) } else layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/** Scroll step per frame, growing from 0 to [maxStep] as [position] moves from [zone] inside the edge to the edge itself. */
private fun edgeScrollDelta(position: Float, start: Float, end: Float, zone: Float, maxStep: Float): Float = when {
    position < start + zone -> -((start + zone - position) / zone).coerceIn(0f, 1f) * maxStep
    position > end - zone -> ((position - (end - zone)) / zone).coerceIn(0f, 1f) * maxStep
    else -> 0f
}

/** Priority dot top-end, then title, due date, tags (own row), checklist count (own row) — same order as the web `CardView`. */
@Composable
private fun CardTileContent(cardWithDetails: KanbanCardWithDetails) {
    val card = cardWithDetails.card
    Box(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(end = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            UrsText(card.title, style = UrsTheme.typography.cardTitle)
            card.dueDate?.let {
                UrsText(it, style = UrsTheme.typography.caption, color = UrsTheme.colors.onSurfaceMuted)
            }
            if (cardWithDetails.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    cardWithDetails.tags.forEach { tag ->
                        val color = tag.color.takeIf { it.isNotBlank() }?.let(::parseChoreColor)
                        if (color != null) {
                            UrsPill(text = tag.tagName, containerColor = color.copy(alpha = 0.15f), contentColor = color)
                        } else {
                            UrsPill(text = tag.tagName)
                        }
                    }
                }
            }
            if (cardWithDetails.checklist.isNotEmpty()) {
                val done = cardWithDetails.checklist.count { it.done }
                UrsText(
                    "$done/${cardWithDetails.checklist.size}",
                    style = UrsTheme.typography.caption,
                    color = UrsTheme.colors.onSurfaceMuted,
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(PriorityDotSize)
                .clip(CircleShape)
                .background(priorityColor(card.priority)),
        )
    }
}

private fun priorityColor(priority: String): Color = when (priority) {
    KanbanCardEntity.PRIORITY_LOW -> PriorityLowColor
    KanbanCardEntity.PRIORITY_HIGH -> PriorityHighColor
    else -> PriorityMediumColor
}

@Composable
private fun AddCardRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrsIcon(imageVector = Icons.Filled.Add, contentDescription = null, tint = UrsTheme.colors.onSurfaceMuted)
        UrsText(stringResource(R.string.kanban_card_add), style = UrsTheme.typography.body, color = UrsTheme.colors.onSurfaceMuted)
    }
}

@Composable
private fun AddColumnTile(onClick: () -> Unit) {
    Column(modifier = Modifier.width(KanbanColumnWidth)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrsIcon(imageVector = Icons.Filled.Add, contentDescription = null, tint = UrsTheme.colors.onSurfaceMuted)
            UrsText(stringResource(R.string.kanban_column_add), style = UrsTheme.typography.body, color = UrsTheme.colors.onSurfaceMuted)
        }
    }
}

/** Every bottom sheet / dialog overlay this screen can show, driven entirely by [viewModel] state. */
@Composable
private fun KanbanBoardDetailOverlays(
    viewModel: KanbanBoardDetailViewModel,
    cardEditor: KanbanCardFormState?,
    richTextState: RichTextFieldState,
) {
    val showColumnForm by viewModel.showColumnForm.collectAsStateWithLifecycle()
    val columnFormState by viewModel.columnFormState.collectAsStateWithLifecycle()
    val actionSheetColumn by viewModel.actionSheetColumn.collectAsStateWithLifecycle()
    val columnNotEmptyError by viewModel.columnNotEmptyError.collectAsStateWithLifecycle()
    val cardEditorChecklist by viewModel.cardEditorChecklist.collectAsStateWithLifecycle()
    val availableNotes by viewModel.availableNotes.collectAsStateWithLifecycle()

    if (showColumnForm) {
        UrsBottomSheet(onDismissRequest = viewModel::closeColumnForm) {
            ColumnForm(form = columnFormState, viewModel = viewModel)
        }
    }

    actionSheetColumn?.let { column ->
        UrsBottomSheet(onDismissRequest = viewModel::closeColumnActionSheet) {
            ColumnActionSheet(
                onRename = { viewModel.openRenameColumnForm(column) },
                onDelete = { viewModel.deleteColumn(column.id) },
            )
        }
    }

    if (columnNotEmptyError) {
        UrsBottomSheet(onDismissRequest = viewModel::dismissColumnNotEmptyError) {
            Column(modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l)) {
                UrsText(stringResource(R.string.kanban_column_not_empty), style = UrsTheme.typography.body)
            }
        }
    }

    cardEditor?.let { form ->
        UrsBottomSheet(onDismissRequest = viewModel::closeCardEditor) {
            CardEditorSheet(
                form = form,
                checklist = cardEditorChecklist,
                availableNotes = availableNotes,
                richTextState = richTextState,
                viewModel = viewModel,
                onRequestDelete = viewModel::deleteCurrentCard,
                onRequestMarkDone = viewModel::markCurrentCardDone,
            )
        }
    }
}

@Composable
private fun ColumnForm(form: KanbanColumnFormState, viewModel: KanbanBoardDetailViewModel) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(
            stringResource(if (form.editingColumnId != null) R.string.kanban_column_rename else R.string.kanban_column_add),
            style = UrsTheme.typography.screenTitle,
        )
        UrsTextField(
            value = form.name,
            onValueChange = viewModel::setColumnName,
            label = stringResource(R.string.kanban_column_name),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsTextField(
            value = form.defaultTagName,
            onValueChange = viewModel::setColumnDefaultTagName,
            label = stringResource(R.string.kanban_column_default_tag),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsButton(
            text = stringResource(if (form.submitting) R.string.saving else R.string.save),
            onClick = viewModel::submitColumnForm,
            enabled = form.isValid && !form.submitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ColumnActionSheet(onRename: () -> Unit, onDelete: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l)) {
        ActionSheetRow(label = stringResource(R.string.kanban_column_rename), icon = Icons.Filled.Edit, onClick = onRename)
        ActionSheetRow(
            label = stringResource(R.string.kanban_column_delete),
            icon = Icons.Filled.Delete,
            onClick = onDelete,
            tint = FormErrorColor,
        )
    }
}

@Composable
private fun ActionSheetRow(label: String, icon: ImageVector, onClick: () -> Unit, tint: Color = UrsTheme.colors.onSurface) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrsIcon(imageVector = icon, contentDescription = null, tint = tint)
        UrsText(label, style = UrsTheme.typography.cardTitle, color = tint)
    }
}

@Composable
private fun CardEditorSheet(
    form: KanbanCardFormState,
    checklist: List<KanbanChecklistItemEntity>,
    availableNotes: List<NoteEntity>,
    richTextState: RichTextFieldState,
    viewModel: KanbanBoardDetailViewModel,
    onRequestDelete: () -> Unit,
    onRequestMarkDone: () -> Unit,
) {
    val formScrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .padding(horizontal = Spacing.xl)
            .padding(bottom = Spacing.xxl)
            .verticalScroll(formScrollState),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(
            stringResource(if (form.isEditing) R.string.kanban_card_edit_title else R.string.kanban_card_new_title),
            style = UrsTheme.typography.screenTitle,
        )

        UrsTextField(
            value = form.title,
            onValueChange = viewModel::setCardTitle,
            label = stringResource(R.string.kanban_card_field_title),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        RichTextField(
            state = richTextState,
            onValueChange = viewModel::setCardDescription,
            label = stringResource(R.string.kanban_card_field_description),
            formScrollState = formScrollState,
            modifier = Modifier.fillMaxWidth(),
        )

        PrioritySelector(selected = form.priority, onSelect = viewModel::setCardPriority)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrsText(
                text = stringResource(R.string.kanban_card_due_date_enable),
                style = UrsTheme.typography.body,
                modifier = Modifier.weight(1f).padding(end = Spacing.m),
            )
            UrsCheckbox(checked = form.dueDateEnabled, onCheckedChange = viewModel::setCardDueDateEnabled)
        }
        if (form.dueDateEnabled) {
            UrsDateField(
                value = form.dueDate,
                onValueChange = viewModel::setCardDueDate,
                label = stringResource(R.string.kanban_card_due_date),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val noneLabel = stringResource(R.string.kanban_card_linked_note_none)
        val noteOptions = remember(availableNotes) { listOf<NoteEntity?>(null) + availableNotes }
        UrsDropdownField(
            label = stringResource(R.string.kanban_card_linked_note),
            options = noteOptions,
            selectedLabel = availableNotes.firstOrNull { it.publicId == form.linkedNoteId }?.title,
            optionLabel = { note: NoteEntity? -> note?.title ?: noneLabel },
            onSelect = { note: NoteEntity? -> viewModel.setCardLinkedNoteId(note?.publicId) },
            modifier = Modifier.fillMaxWidth(),
        )

        CardTagsEditor(form = form, viewModel = viewModel)

        if (form.isEditing) {
            ChecklistEditor(items = checklist, input = form.checklistInput, viewModel = viewModel)
        }

        if (form.submitFailed) {
            UrsText(stringResource(R.string.error_save), color = FormErrorColor, style = UrsTheme.typography.body)
        }

        UrsButton(
            text = stringResource(if (form.submitting) R.string.saving else R.string.save),
            onClick = viewModel::submitCardEditor,
            enabled = form.isValid && !form.submitting,
            modifier = Modifier.fillMaxWidth(),
        )

        if (form.isEditing) {
            UrsOutlinedButton(
                text = stringResource(R.string.kanban_card_mark_done),
                onClick = onRequestMarkDone,
                enabled = !form.submitting,
                modifier = Modifier.fillMaxWidth(),
            )
            UrsOutlinedButton(
                text = stringResource(R.string.kanban_card_delete),
                onClick = onRequestDelete,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PrioritySelector(selected: String, onSelect: (String) -> Unit) {
    val labels = mapOf(
        KanbanCardEntity.PRIORITY_LOW to stringResource(R.string.kanban_priority_low),
        KanbanCardEntity.PRIORITY_MEDIUM to stringResource(R.string.kanban_priority_medium),
        KanbanCardEntity.PRIORITY_HIGH to stringResource(R.string.kanban_priority_high),
    )
    UrsDropdownField(
        label = stringResource(R.string.kanban_card_priority),
        options = labels.keys.toList(),
        selectedLabel = labels[selected],
        optionLabel = { labels.getValue(it) },
        onSelect = onSelect,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CardTagsEditor(form: KanbanCardFormState, viewModel: KanbanBoardDetailViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (form.tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                form.tags.forEach { tag ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val color = form.tagColors[tag]?.takeIf { it.isNotBlank() }?.let(::parseChoreColor)
                        if (color != null) {
                            UrsPill(text = tag, containerColor = color.copy(alpha = 0.15f), contentColor = color)
                        } else {
                            UrsPill(text = tag)
                        }
                        UrsIconButton(
                            onClick = { viewModel.removeCardTag(tag) },
                            contentDescription = stringResource(R.string.kanban_card_remove_tag, tag),
                            imageVector = Icons.Filled.Close,
                        )
                    }
                }
            }
        }
        UrsTextField(
            value = form.tagInput,
            onValueChange = viewModel::setCardTagInput,
            label = stringResource(R.string.kanban_card_field_tags),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.addCardTag(form.tagInput) }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (form.tagSuggestions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                form.tagSuggestions.forEach { suggestion ->
                    UrsCard(modifier = Modifier.fillMaxWidth().clickable { viewModel.addCardTag(suggestion) }) {
                        UrsText(suggestion, style = UrsTheme.typography.body)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistEditor(items: List<KanbanChecklistItemEntity>, input: String, viewModel: KanbanBoardDetailViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        UrsText(stringResource(R.string.kanban_card_checklist), style = UrsTheme.typography.caption, color = UrsTheme.colors.onSurfaceMuted)
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                UrsCheckbox(checked = item.done, onCheckedChange = { viewModel.toggleChecklistItem(item) })
                UrsText(item.text, style = UrsTheme.typography.body, modifier = Modifier.weight(1f))
                UrsIconButton(
                    onClick = { viewModel.deleteChecklistItem(item) },
                    contentDescription = stringResource(R.string.kanban_checklist_item_delete),
                    imageVector = Icons.Filled.Close,
                )
            }
        }
        UrsTextField(
            value = input,
            onValueChange = viewModel::setChecklistInput,
            label = stringResource(R.string.kanban_checklist_item_add),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.addChecklistItem() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

