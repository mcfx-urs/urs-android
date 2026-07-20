package ch.mcfx.urs.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ch.mcfx.urs.R
import ch.mcfx.urs.data.remote.CatalogImageDto
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * image-reuse picker — every existing [CatalogImageDto] as a tile
 * grid, same shape as a product grid ([UrsSquareTile] in a 3-column
 * [LazyVerticalGrid]) so picking a photo to reuse feels like the rest of the
 * catalog UI rather than a bespoke gallery widget. No upload here — this
 * only ever offers images already seeded on the backend's image volume.
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

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = modifier,
    ) {
        items(images, key = { it.id }) { image ->
            UrsSquareTile(
                title = "",
                catalogImageId = image.id.toIntOrNull(),
                onClick = { onSelect(image.id) },
            )
        }
    }
}
