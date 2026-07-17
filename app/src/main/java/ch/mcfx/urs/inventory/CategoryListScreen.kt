package ch.mcfx.urs.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsSquareTile
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

/** Tile-grid browse view over one inventory's tracked-product categories — see [CategoriesViewModel]'s own doc comment. */
@Composable
fun CategoryListScreen(
    inventoryId: String,
    onOpenCategory: (InventoryCategoryGroup) -> Unit,
    viewModel: CategoriesViewModel = viewModel(
        factory = CategoriesViewModel.factory(inventoryId, stringResource(R.string.inventory_uncategorized)),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        CategoriesUiState.Loading -> Box(Modifier.fillMaxSize()) { UrsProgressIndicator(Modifier.align(Alignment.Center)) }

        is CategoriesUiState.Data -> CategoryGrid(groups = state.groups, onOpenCategory = onOpenCategory)
    }
}

@Composable
private fun CategoryGrid(groups: List<InventoryCategoryGroup>, onOpenCategory: (InventoryCategoryGroup) -> Unit) {
    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            UrsText(
                stringResource(R.string.inventory_categories_empty),
                modifier = Modifier.align(Alignment.Center),
                color = UrsTheme.colors.onSurfaceMuted,
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(groups, key = { it.categoryId ?: "uncategorized" }) { group ->
            UrsSquareTile(
                title = group.categoryName,
                catalogImageId = null,
                quantityBadge = group.productCount.toString(),
                onClick = { onOpenCategory(group) },
            )
        }
    }
}
