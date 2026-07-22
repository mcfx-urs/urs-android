package ch.mcfx.urs.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ch.mcfx.urs.R
import ch.mcfx.urs.data.remote.CatalogImageDto
import ch.mcfx.urs.ui.theme.UrsTheme
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
 */
@Composable
fun CatalogImagePicker(images: List<CatalogImageDto>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    if (images.isEmpty()) {
        UrsText(
            text = stringResource(R.string.product_management_image_picker_empty),
            color = UrsTheme.colors.onSurfaceMuted,
            modifier = modifier.padding(Spacing.l),
        )
        return
    }

    var query by remember { mutableStateOf("") }
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
