package ch.mcfx.urs.shoppinglist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.ShoppingListItemDetail
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsCheckbox
import ch.mcfx.urs.ui.components.UrsFab
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

// Same reasoning as ProductListScreen's own local constant — the type scale
// has no "big FAB glyph" size of its own.
private val FabIconStyle = TextStyle(fontSize = 28.sp)

@Composable
fun ListDetailScreen(
    listId: String,
    viewModel: ListDetailViewModel = viewModel(factory = ListDetailViewModel.factory(listId)),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showAddProduct by viewModel.showAddProduct.collectAsStateWithLifecycle()
    val noteForm by viewModel.noteForm.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            ListDetailUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))

            is ListDetailUiState.Data -> Column(Modifier.fillMaxSize()) {
                UrsText(
                    state.listName,
                    style = UrsTheme.typography.screenTitle,
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
                )
                ItemGroups(
                    groups = state.groups,
                    onToggleChecked = viewModel::toggleChecked,
                    onLongPress = viewModel::openNoteForm,
                )
            }
        }

        if (uiState is ListDetailUiState.Data) {
            UrsFab(
                onClick = viewModel::openAddProduct,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
            ) {
                UrsText(text = "+", style = FabIconStyle, color = UrsTheme.colors.onAccent)
            }
        }
    }

    if (showAddProduct) {
        UrsBottomSheet(onDismissRequest = viewModel::closeAddProduct) {
            AddProductScreen(listId = listId)
        }
    }

    if (noteForm.item != null) {
        UrsBottomSheet(onDismissRequest = viewModel::closeNoteForm) {
            NoteForm(form = noteForm, viewModel = viewModel)
        }
    }
}

@Composable
private fun ItemGroups(
    groups: List<ShoppingListCategoryGroup>,
    onToggleChecked: (ShoppingListItemDetail) -> Unit,
    onLongPress: (ShoppingListItemDetail) -> Unit,
) {
    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            UrsText(
                stringResource(R.string.shoppinglist_items_empty),
                modifier = Modifier.align(Alignment.Center),
                color = UrsTheme.colors.onSurfaceMuted,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        groups.forEach { group ->
            item(key = "header-${group.categoryName}") {
                UrsText(
                    group.categoryName,
                    style = UrsTheme.typography.caption,
                    color = UrsTheme.colors.onSurfaceMuted,
                    modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.xs),
                )
            }
            items(group.items, key = { it.item.id }) { detail ->
                ItemRow(detail = detail, onToggleChecked = onToggleChecked, onLongPress = onLongPress)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemRow(
    detail: ShoppingListItemDetail,
    onToggleChecked: (ShoppingListItemDetail) -> Unit,
    onLongPress: (ShoppingListItemDetail) -> Unit,
) {
    val checked = detail.item.checked
    val contentColor = if (checked) UrsTheme.colors.onSurfaceMuted else UrsTheme.colors.onSurface

    UrsCard(
        radius = Radius.row,
        contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { onToggleChecked(detail) }, onLongClick = { onLongPress(detail) }),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            UrsCheckbox(checked = checked, onCheckedChange = { onToggleChecked(detail) })
            Spacer(Modifier.width(Spacing.m))
            Column(modifier = Modifier.weight(1f)) {
                UrsText(detail.productName, style = UrsTheme.typography.cardTitle, color = contentColor)
                val note = detail.item.note
                if (!note.isNullOrBlank()) {
                    UrsText(note, style = UrsTheme.typography.caption, color = UrsTheme.colors.onSurfaceMuted)
                }
            }
        }
    }
}

@Composable
private fun NoteForm(form: AddNoteFormState, viewModel: ListDetailViewModel) {
    val detail = form.item ?: return
    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(detail.productName, style = UrsTheme.typography.screenTitle)
        UrsTextField(
            value = form.note,
            onValueChange = viewModel::setNote,
            label = stringResource(R.string.shoppinglist_item_note),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsButton(
            text = stringResource(R.string.save),
            onClick = viewModel::saveNote,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
