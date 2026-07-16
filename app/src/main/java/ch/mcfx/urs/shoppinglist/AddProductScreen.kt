package ch.mcfx.urs.shoppinglist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.local.InventoryCategoryEntity
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsFilterChip
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * Content of the "add a product to this list" sheet, opened from
 * [ListDetailScreen]'s FAB. A single sheet instance switches between three
 * mutually exclusive modes (search, confirm-note, add-custom-product) driven
 * directly by [AddProductViewModel]'s state, rather than nesting a second
 * [ch.mcfx.urs.ui.components.UrsBottomSheet] inside the first one — that
 * would fight the outer sheet's own `wrapContentHeight` sizing for no real
 * benefit here, since none of these three modes need to be visible at the
 * same time as another.
 */
@Composable
fun AddProductScreen(
    listId: String,
    viewModel: AddProductViewModel = viewModel(factory = AddProductViewModel.factory(listId)),
) {
    val noteInput by viewModel.noteInput.collectAsStateWithLifecycle()
    val showCustomForm by viewModel.showCustomForm.collectAsStateWithLifecycle()

    when {
        noteInput != null -> NoteInputMode(state = noteInput, viewModel = viewModel)
        showCustomForm -> CustomProductMode(viewModel = viewModel)
        else -> SearchMode(viewModel = viewModel)
    }
}

@Composable
private fun SearchMode(viewModel: AddProductViewModel) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl).heightIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.shoppinglist_add_product_title), style = UrsTheme.typography.screenTitle)

        UrsTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            label = stringResource(R.string.shoppinglist_add_product_search),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = viewModel::openCustomForm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            UrsIcon(imageVector = Icons.Filled.Add, contentDescription = null, tint = UrsTheme.colors.accent)
            UrsText(
                stringResource(R.string.shoppinglist_add_custom_product),
                style = UrsTheme.typography.body,
                color = UrsTheme.colors.accent,
            )
        }

        if (query.isNotBlank()) {
            if (results.isEmpty()) {
                UrsText(
                    stringResource(R.string.shoppinglist_add_product_no_results),
                    color = UrsTheme.colors.onSurfaceMuted,
                    style = UrsTheme.typography.body,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    items(results, key = { it.name + it.hashCode() }) { result ->
                        ResultRow(result = result, onClick = { viewModel.selectResult(result) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(result: ProductSearchResult, onClick: () -> Unit) {
    UrsCard(
        radius = Radius.row,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            // No image loading in this phase (Coil/AsyncImage is out of
            // scope) — every result shows the same generic placeholder icon.
            UrsIcon(
                imageVector = Icons.Filled.ShoppingBasket,
                contentDescription = null,
                tint = UrsTheme.colors.onSurfaceMuted,
                modifier = Modifier.size(24.dp),
            )
            UrsText(result.name, style = UrsTheme.typography.body)
        }
    }
}

// Deliberately does not close the sheet on confirm (see AddProductScreen's
// own doc comment) — successfully adding a result returns to SearchMode
// with the same query/results still in view, so adding the same product
// again with a different note (or adding several different results in a
// row) never needs the FAB to be tapped again in between.
@Composable
private fun NoteInputMode(state: NoteInputState?, viewModel: AddProductViewModel) {
    if (state == null) return

    // Only a household product (not a not-yet-added catalog entry) can have
    // note history — see InventoryProductEntity.recentNote1's doc comment.
    val recentNotes = (state.result as? ProductSearchResult.Household)
        ?.product
        ?.let { listOfNotNull(it.recentNote1, it.recentNote2, it.recentNote3) }
        ?.filter { it.isNotBlank() }
        .orEmpty()

    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(state.result.name, style = UrsTheme.typography.screenTitle)

        UrsTextField(
            value = state.note,
            onValueChange = viewModel::setNote,
            label = stringResource(R.string.shoppinglist_item_note),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Tapping a chip only fills the text field above, it never submits
        // by itself — the user can still edit the note before confirming,
        // and picking one doesn't reorder the history unless it's actually
        // resubmitted (the normal add-with-note flow below does that).
        if (recentNotes.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                recentNotes.forEach { note ->
                    UrsFilterChip(label = note, selected = false, onClick = { viewModel.setNote(note) })
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsOutlinedButton(
                text = stringResource(R.string.cancel),
                onClick = viewModel::closeNoteInput,
                modifier = Modifier.weight(1f),
            )
            UrsButton(
                text = stringResource(R.string.shoppinglist_add_product_add),
                onClick = { viewModel.confirmAdd() },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CustomProductMode(viewModel: AddProductViewModel) {
    val form by viewModel.customForm.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.shoppinglist_add_custom_product), style = UrsTheme.typography.screenTitle)

        UrsTextField(
            value = form.name,
            onValueChange = viewModel::setCustomName,
            label = stringResource(R.string.inventory_product_name),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        UrsDropdownField(
            label = stringResource(R.string.inventory_category_name),
            options = categories,
            selectedLabel = form.category?.name,
            optionLabel = InventoryCategoryEntity::name,
            onSelect = viewModel::setCustomCategory,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsOutlinedButton(
                text = stringResource(R.string.cancel),
                onClick = viewModel::closeCustomForm,
                modifier = Modifier.weight(1f),
            )
            UrsButton(
                text = stringResource(if (form.submitting) R.string.saving else R.string.save),
                onClick = viewModel::submitCustom,
                enabled = form.isValid && !form.submitting,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
