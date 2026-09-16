package ch.mcfx.urs.kanban

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.local.KanbanBoardEntity
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsFab
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.components.ursScreenContentPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

// The type scale has no "big FAB glyph" size, and no "error" role in the
// palette yet — same local constants as ShoppingListsScreen.
private val FabIconStyle = TextStyle(fontSize = 28.sp)
private val FormErrorColor = Color(0xFFD64545)
private val BoardRowTitleStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)

@Composable
fun KanbanBoardsScreen(
    onOpenBoard: (KanbanBoardEntity) -> Unit,
    viewModel: KanbanBoardsViewModel = viewModel(factory = KanbanBoardsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val showForm by viewModel.showForm.collectAsStateWithLifecycle()
    val actionSheetBoard by viewModel.actionSheetBoard.collectAsStateWithLifecycle()
    val pendingDeleteBoard by viewModel.pendingDeleteBoard.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            KanbanBoardsUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))

            is KanbanBoardsUiState.Data -> BoardsList(
                boards = state.boards,
                onOpenBoard = onOpenBoard,
                onLongPress = viewModel::openActionSheet,
            )
        }

        if (uiState is KanbanBoardsUiState.Data) {
            UrsFab(
                onClick = viewModel::openCreateForm,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
            ) {
                UrsText(text = "+", style = FabIconStyle, color = UrsTheme.colors.onAccent)
            }
        }
    }

    if (showForm) {
        UrsBottomSheet(onDismissRequest = viewModel::closeForm) {
            BoardForm(form = formState, viewModel = viewModel)
        }
    }

    actionSheetBoard?.let { board ->
        UrsBottomSheet(onDismissRequest = viewModel::closeActionSheet) {
            BoardActionSheet(
                isFavorite = board.isFavorite,
                onRename = { viewModel.openRenameForm(board) },
                onToggleFavorite = viewModel::toggleFavorite,
                onDelete = viewModel::requestDelete,
            )
        }
    }

    if (pendingDeleteBoard != null) {
        UrsBottomSheet(onDismissRequest = viewModel::cancelDelete) {
            DeleteConfirmSheet(onConfirm = viewModel::confirmDelete, onCancel = viewModel::cancelDelete)
        }
    }
}

@Composable
private fun BoardsList(
    boards: List<KanbanBoardEntity>,
    onOpenBoard: (KanbanBoardEntity) -> Unit,
    onLongPress: (KanbanBoardEntity) -> Unit,
) {
    if (boards.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            UrsText(
                stringResource(R.string.kanban_boards_empty),
                modifier = Modifier.align(Alignment.Center),
                color = UrsTheme.colors.onSurfaceMuted,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ursScreenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        items(boards, key = { it.id }) { board ->
            BoardRow(board = board, onOpenBoard = onOpenBoard, onLongPress = onLongPress)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoardRow(board: KanbanBoardEntity, onOpenBoard: (KanbanBoardEntity) -> Unit, onLongPress: (KanbanBoardEntity) -> Unit) {
    UrsCard(
        radius = Radius.row,
        contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.xl),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { onOpenBoard(board) }, onLongClick = { onLongPress(board) }),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrsText(
                board.name,
                style = BoardRowTitleStyle,
                color = UrsTheme.colors.accent,
                modifier = Modifier.weight(1f),
            )
            BoardSyncStatusPill(board.syncStatus)
        }
    }
}

@Composable
private fun BoardSyncStatusPill(status: SyncStatus) {
    when (status) {
        SyncStatus.PENDING -> UrsPill(text = stringResource(R.string.fill_status_pending))
        SyncStatus.FAILED -> UrsPill(
            text = stringResource(R.string.fill_status_failed),
            containerColor = FormErrorColor.copy(alpha = 0.15f),
            contentColor = FormErrorColor,
        )
        SyncStatus.SYNCED -> Unit
    }
}

@Composable
private fun BoardForm(form: KanbanBoardFormState, viewModel: KanbanBoardsViewModel) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(
            stringResource(
                if (form.editingBoardId != null) R.string.kanban_board_rename else R.string.kanban_board_add,
            ),
            style = UrsTheme.typography.screenTitle,
        )

        UrsTextField(
            value = form.name,
            onValueChange = viewModel::setName,
            label = stringResource(R.string.kanban_board_name),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (form.submitFailed) {
            UrsText(stringResource(R.string.error_save), color = FormErrorColor, style = UrsTheme.typography.body)
        }

        UrsButton(
            text = stringResource(if (form.submitting) R.string.saving else R.string.save),
            onClick = viewModel::submit,
            enabled = form.isValid && !form.submitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BoardActionSheet(isFavorite: Boolean, onRename: () -> Unit, onToggleFavorite: () -> Unit, onDelete: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l)) {
        ActionSheetRow(label = stringResource(R.string.kanban_board_rename), icon = Icons.Filled.Edit, onClick = onRename)
        ActionSheetRow(
            label = stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add),
            icon = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
            onClick = onToggleFavorite,
        )
        ActionSheetRow(
            label = stringResource(R.string.kanban_board_delete),
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
private fun DeleteConfirmSheet(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.kanban_board_delete_confirm_title), style = UrsTheme.typography.cardTitle)
        UrsText(
            stringResource(R.string.kanban_board_delete_confirm_body),
            style = UrsTheme.typography.body,
            color = UrsTheme.colors.onSurfaceMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsOutlinedButton(text = stringResource(R.string.cancel), onClick = onCancel, modifier = Modifier.weight(1f))
            UrsButton(text = stringResource(R.string.kanban_board_delete), onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}
