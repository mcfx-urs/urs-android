package ch.mcfx.urs.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.remote.InventoryCategoryDto
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsFab
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

// Same reasoning as FuelScreen's FabIconStyle — the type scale has no "big
// FAB glyph" size of its own.
private val FabIconStyle = TextStyle(fontSize = 28.sp)

// No "error" role in the design system's palette yet (see Color.kt) — this
// mirrors ProductListScreen's own local warning-color constants: already
// decided, doesn't need to wait on the broader token set.
private val FormErrorColor = Color(0xFFD64545)

@Composable
fun CategoryListScreen(
    onOpenCategory: (InventoryCategoryDto) -> Unit,
    viewModel: CategoriesViewModel = viewModel(factory = CategoriesViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val showForm by viewModel.showForm.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            CategoriesUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))

            is CategoriesUiState.Error -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                UrsText(stringResource(R.string.error_load), style = UrsTheme.typography.body)
                Spacer(Modifier.height(Spacing.l))
                UrsButton(text = stringResource(R.string.retry), onClick = viewModel::load)
            }

            is CategoriesUiState.Data -> CategoryList(
                categories = state.categories,
                onOpenCategory = onOpenCategory,
                onDeleteCategory = viewModel::deleteCategory,
            )
        }

        if (uiState is CategoriesUiState.Data) {
            UrsFab(
                onClick = viewModel::openForm,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
            ) {
                UrsText(text = "+", style = FabIconStyle, color = UrsTheme.colors.onAccent)
            }
        }
    }

    if (showForm) {
        UrsBottomSheet(onDismissRequest = viewModel::closeForm) {
            CategoryForm(form = formState, viewModel = viewModel)
        }
    }
}

@Composable
private fun CategoryList(
    categories: List<InventoryCategoryDto>,
    onOpenCategory: (InventoryCategoryDto) -> Unit,
    onDeleteCategory: (String) -> Unit,
) {
    if (categories.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            UrsText(
                stringResource(R.string.inventory_categories_empty),
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
        items(categories, key = { it.id }) { category ->
            UrsCard(
                radius = Radius.row,
                contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCategory(category) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UrsText(
                        category.name,
                        style = UrsTheme.typography.cardTitle,
                        modifier = Modifier.weight(1f),
                    )
                    UrsIconButton(
                        onClick = { onDeleteCategory(category.id) },
                        contentDescription = stringResource(R.string.inventory_category_remove, category.name),
                        imageVector = Icons.Filled.Close,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryForm(form: CategoryFormState, viewModel: CategoriesViewModel) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.inventory_category_add), style = UrsTheme.typography.screenTitle)

        UrsTextField(
            value = form.name,
            onValueChange = viewModel::setName,
            label = stringResource(R.string.inventory_category_name),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (form.submitFailed) {
            UrsText(
                stringResource(R.string.error_save),
                color = FormErrorColor,
                style = UrsTheme.typography.body,
            )
        }

        UrsButton(
            text = stringResource(if (form.submitting) R.string.saving else R.string.save),
            onClick = viewModel::submit,
            enabled = form.isValid && !form.submitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
