package ch.mcfx.urs.kanban

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.KanbanBoardDetail
import ch.mcfx.urs.data.KanbanCardWithDetails
import ch.mcfx.urs.data.KanbanColumnWithCards
import ch.mcfx.urs.data.local.KanbanCardEntity
import ch.mcfx.urs.data.local.KanbanChecklistItemEntity
import ch.mcfx.urs.data.local.NoteEntity
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsCheckbox
import ch.mcfx.urs.ui.components.UrsDateField
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
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
    }

    KanbanBoardDetailOverlays(viewModel = viewModel)
}

@Composable
private fun BoardDetailContent(detail: KanbanBoardDetail, viewModel: KanbanBoardDetailViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(top = Spacing.l)) {
        UrsText(
            detail.board.name,
            style = UrsTheme.typography.screenTitle,
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
        )
        BoardColumnsRow(detail = detail, viewModel = viewModel, modifier = Modifier.weight(1f))
    }
}

/**
 * The horizontally-scrollable row of columns, each a vertically-arranged
 * list of cards — the drag-and-drop core of the feature. Mirrors
 * `HomeScreen`'s `detectDragGesturesAfterLongPress` +
 * `graphicsLayer`-translation pattern for both column reordering and card
 * reordering, and `ChoresScreen`'s `StatsStrip` for the "accumulate a
 * pixel delta, convert to a discrete step shift, subtract the consumed
 * portion back out" reorder math — extended to two axes for cards, which
 * can cross into a different column mid-drag, not just reorder within one
 * list.
 *
 * Column/card composables below are local functions (not separate
 * top-level `@Composable`s) so they can read/write this function's own
 * drag-state `var`s directly as closures, rather than threading a dozen
 * callbacks down through parameters.
 */
@Composable
private fun BoardColumnsRow(detail: KanbanBoardDetail, viewModel: KanbanBoardDetailViewModel, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val columnSpacingPx = with(density) { Spacing.m.toPx() }
    val columnStepPx = with(density) { KanbanColumnWidth.toPx() } + columnSpacingPx
    val cardSpacingPx = with(density) { Spacing.s.toPx() }

    // Column/card membership always mirrors the real, persisted board — a
    // held card or column is only ever a *visual* float (graphicsLayer
    // translation) on top of this, never a live re-parent into a different
    // column's list. Which column/index a drag actually lands on is decided
    // exactly once, in onDragEnd, from the final accumulated offset — never
    // while the gesture is still held. Anything else (re-parenting mid-drag,
    // as an earlier version of this screen did) means Compose treats the
    // drag as crossing into a different composable subtree, which can drop
    // the live pointerInput gesture entirely and/or flap back and forth
    // around the crossing threshold on ordinary finger jitter.
    val columnIdShape = detail.columns.map { it.column.id }
    val cardIdShape = detail.columns.map { it.column.id to it.cards.map { c -> c.card.id } }
    val orderedColumnIds = columnIdShape
    val cardIdsByColumn = cardIdShape.toMap()
    // Pure display/lookup maps, deliberately not gated on the narrow id
    // shape above — these must always reflect the latest field values (a
    // card's title/tags/etc.) even while some other card is mid-drag.
    val columnById = remember(detail) { detail.columns.associateBy { it.column.id } }
    val cardById = remember(detail) { detail.columns.flatMap { it.cards }.associateBy { it.card.id } }
    val cardHeightsPx = remember { mutableStateMapOf<Long, Int>() }

    var draggingColumnId by remember { mutableStateOf<Long?>(null) }
    var columnDragOffsetX by remember { mutableStateOf(0f) }

    var draggingCardId by remember { mutableStateOf<Long?>(null) }
    var cardDragOffset by remember { mutableStateOf(Offset.Zero) }

    @Composable
    fun CardTile(cardWithDetails: KanbanCardWithDetails) {
        val cardId = cardWithDetails.card.id
        val isDragging = cardId == draggingCardId
        UrsCard(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { cardHeightsPx[cardId] = it.size.height }
                .graphicsLayer {
                    translationX = if (isDragging) cardDragOffset.x else 0f
                    translationY = if (isDragging) cardDragOffset.y else 0f
                }
                .zIndex(if (isDragging) 1f else 0f)
                .pointerInput(cardId) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { draggingCardId = cardId; cardDragOffset = Offset.Zero },
                        // The only place a move is actually decided/saved — see the
                        // comment on cardIdsByColumn's declaration above for why.
                        onDragEnd = {
                            draggingCardId = null
                            val offset = cardDragOffset
                            cardDragOffset = Offset.Zero
                            val sourceColumnId = cardIdsByColumn.entries.firstOrNull { cardId in it.value }?.key
                                ?: return@detectDragGesturesAfterLongPress
                            val sourceIndex = cardIdsByColumn.getValue(sourceColumnId).indexOf(cardId)
                            val sourceColumnIndex = orderedColumnIds.indexOf(sourceColumnId)
                            val columnShift = if (columnStepPx > 0f) (offset.x / columnStepPx).roundToInt() else 0
                            val targetColumnIndex = (sourceColumnIndex + columnShift).coerceIn(0, orderedColumnIds.lastIndex)
                            val targetColumnId = orderedColumnIds[targetColumnIndex]
                            val targetColumnPublicId = columnById[targetColumnId]?.column?.publicId ?: return@detectDragGesturesAfterLongPress
                            val stepY = (cardHeightsPx[cardId] ?: 0) + cardSpacingPx
                            val indexShift = if (stepY > 0f) (offset.y / stepY).roundToInt() else 0
                            val targetSiblingCount = cardIdsByColumn[targetColumnId].orEmpty().count { it != cardId }
                            val targetIndex = (sourceIndex + indexShift).coerceIn(0, targetSiblingCount)
                            viewModel.moveCard(cardId, targetColumnPublicId, targetIndex)
                        },
                        onDragCancel = { draggingCardId = null; cardDragOffset = Offset.Zero },
                        onDrag = { change, delta -> change.consume(); cardDragOffset += delta },
                    )
                }
                .clickable { viewModel.openCardEditor(cardWithDetails.card, cardWithDetails.tags) },
            contentPadding = PaddingValues(Spacing.m),
        ) {
            CardTileContent(cardWithDetails)
        }
    }

    @Composable
    fun ColumnHeader(columnWithCards: KanbanColumnWithCards) {
        val columnId = columnWithCards.column.id
        val isDragging = columnId == draggingColumnId
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = if (isDragging) columnDragOffsetX else 0f }
                .zIndex(if (isDragging) 1f else 0f)
                .pointerInput(columnId) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { draggingColumnId = columnId; columnDragOffsetX = 0f },
                        // Decided only here, from the final offset — see cardIdsByColumn's
                        // doc comment above for why not live during the hold.
                        onDragEnd = {
                            draggingColumnId = null
                            val offsetX = columnDragOffsetX
                            columnDragOffsetX = 0f
                            val currentIndex = orderedColumnIds.indexOf(columnId)
                            if (currentIndex < 0 || columnStepPx <= 0f) return@detectDragGesturesAfterLongPress
                            val targetIndex = (currentIndex + (offsetX / columnStepPx).roundToInt()).coerceIn(0, orderedColumnIds.lastIndex)
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
                columnWithCards.cards.size.toString(),
                style = UrsTheme.typography.caption,
                color = UrsTheme.colors.onSurfaceMuted,
            )
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        orderedColumnIds.forEach { columnId ->
            val columnWithCards = columnById[columnId] ?: return@forEach
            // Keyed by columnId (not just positional) — without this, reordering
            // columns (or a card crossing into a column that shifts this one's
            // slot) makes Compose reuse this composable's slot for a *different*
            // column's data, which cancels and restarts the columnId-keyed
            // pointerInput below mid-gesture instead of letting the drag continue.
            key(columnId) {
                Column(
                    modifier = Modifier.width(KanbanColumnWidth).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    ColumnHeader(columnWithCards)
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        cardIdsByColumn[columnId].orEmpty().forEach { cardId ->
                            val cardWithDetails = cardById[cardId] ?: return@forEach
                            // Same reasoning as the columnId key above — a card that
                            // crosses into a different column's list must keep its own
                            // identity (and its live drag pointerInput) rather than
                            // having some other card's composable slot reused for it.
                            key(cardId) { CardTile(cardWithDetails) }
                        }
                        AddCardRow(onClick = { viewModel.openCreateCardEditor(columnWithCards.column.publicId) })
                    }
                }
            }
        }
        AddColumnTile(onClick = viewModel::openCreateColumnForm)
    }
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
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    cardWithDetails.tags.forEach { UrsPill(text = it) }
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
private fun KanbanBoardDetailOverlays(viewModel: KanbanBoardDetailViewModel) {
    val showColumnForm by viewModel.showColumnForm.collectAsStateWithLifecycle()
    val columnFormState by viewModel.columnFormState.collectAsStateWithLifecycle()
    val actionSheetColumn by viewModel.actionSheetColumn.collectAsStateWithLifecycle()
    val columnNotEmptyError by viewModel.columnNotEmptyError.collectAsStateWithLifecycle()
    val cardEditor by viewModel.cardEditor.collectAsStateWithLifecycle()
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
                viewModel = viewModel,
                onRequestDelete = viewModel::deleteCurrentCard,
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
    viewModel: KanbanBoardDetailViewModel,
    onRequestDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = Spacing.xl)
            .padding(bottom = Spacing.xxl)
            .verticalScroll(rememberScrollState()),
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

        UrsTextField(
            value = form.description,
            onValueChange = viewModel::setCardDescription,
            label = stringResource(R.string.kanban_card_field_description),
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
                        UrsPill(text = tag)
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

