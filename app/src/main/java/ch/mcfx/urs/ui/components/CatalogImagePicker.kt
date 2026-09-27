package ch.mcfx.urs.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.R
import ch.mcfx.urs.data.local.CatalogProductEntity
import ch.mcfx.urs.data.remote.CatalogImageDto
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * Image-reuse picker — every existing [CatalogImageDto] as a tile
 * grid, same shape as a product grid ([UrsSquareTile] in a 3-column
 * [LazyVerticalGrid]) so picking a photo to reuse feels like the rest of the
 * catalog UI rather than a bespoke gallery widget. No upload here — this
 * only ever offers images already seeded on the backend's image volume.
 *
 * The search field narrows [images] by [CatalogImageDto.linkedNames]
 * (the product/category name(s) the backend resolves for each image) —
 * scrolling the grid stays the default way to browse, this only filters it.
 * An image with no linked name has nothing to match, so it drops out of any
 * non-blank search rather than showing up as an unexplained unlabeled hit.
 *
 * [initialQuery] seeds the search field instead of leaving it blank — used
 * by the barcode-scan quick-create flows (mcfx-urs/urs-android#91) to open
 * this picker pre-filtered to the resolved product name, surfacing
 * same-name existing images as one-tap suggestions instead of only "browse
 * everything". Still a plain, editable search field afterward — no separate
 * "suggestions" mode.
 */
@Composable
fun CatalogImagePicker(images: List<CatalogImageDto>, onSelect: (String) -> Unit, modifier: Modifier = Modifier, initialQuery: String = "") {
    if (images.isEmpty()) {
        UrsText(
            text = stringResource(R.string.product_management_image_picker_empty),
            color = UrsTheme.colors.onSurfaceMuted,
            modifier = modifier.padding(Spacing.l),
        )
        return
    }

    var query by remember { mutableStateOf(initialQuery) }
    val filtered = remember(images, query) {
        if (query.isBlank()) images else images.filter { it.linkedNames.contains(query, ignoreCase = true) }
    }

    Column(modifier = modifier) {
        UrsTextField(
            value = query,
            onValueChange = { query = it },
            label = stringResource(R.string.product_management_image_picker_search_label),
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
        )

        if (filtered.isEmpty()) {
            UrsText(
                text = stringResource(R.string.product_management_image_picker_no_matches),
                color = UrsTheme.colors.onSurfaceMuted,
                modifier = Modifier.padding(Spacing.l),
            )
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            items(filtered, key = { it.id }) { image ->
                UrsSquareTile(
                    title = "",
                    catalogImageId = image.id.toIntOrNull(),
                    onClick = { onSelect(image.id) },
                )
            }
        }
    }
}

/**
 * Existing-product picker — a live, search-driven result list (same search
 * flow Inventory's own product-search step uses, `catalogRepository.search`
 * via the caller's ViewModel — this composable is stateless, unlike
 * [CatalogImagePicker]'s own client-side filter over a preloaded list, since
 * the full catalog isn't preloaded here). Used by the barcode quick-create
 * flow (mcfx-urs/urs-android#113) to let picking one existing product
 * inherit its category and image together, instead of only an image via
 * [CatalogImagePicker]. A plain (non-lazy) list — result counts here are
 * small, name-matched suggestions, not a full catalog browse.
 */
@Composable
fun CatalogProductPicker(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<CatalogProductEntity>,
    onSelect: (CatalogProductEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        UrsTextField(
            value = query,
            onValueChange = onQueryChange,
            label = stringResource(R.string.product_management_image_picker_search_label),
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
        )

        if (query.isBlank()) return@Column

        if (results.isEmpty()) {
            UrsText(
                text = stringResource(R.string.product_existing_picker_no_matches),
                color = UrsTheme.colors.onSurfaceMuted,
                modifier = Modifier.padding(horizontal = Spacing.l),
            )
            return@Column
        }

        Column(
            modifier = Modifier.padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            results.forEach { product ->
                UrsCard(
                    radius = Radius.row,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = { onSelect(product) }),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        UrsSquareTile(
                            title = "",
                            catalogImageId = product.catalogImageId,
                            onClick = { onSelect(product) },
                            modifier = Modifier.size(40.dp),
                        )
                        UrsText(product.name, style = UrsTheme.typography.body)
                    }
                }
            }
        }
    }
}
