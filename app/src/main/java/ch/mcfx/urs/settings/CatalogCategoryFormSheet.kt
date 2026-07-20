package ch.mcfx.urs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.CatalogImagePicker
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsSquareTile
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

// No "error" role in the design system's palette yet — see CatalogProductFormSheet.
private val FormErrorColor = Color(0xFFD64545)

/**
 * Create/edit form for a manually-created catalog category — same
 * dual-purpose shape as [CatalogProductFormSheet], minus the category picker
 * (a category doesn't have one of its own).
 */
@Composable
fun CatalogCategoryFormSheet(viewModel: ProductManagementViewModel, onDismissRequest: () -> Unit) {
    val form by viewModel.categoryForm.collectAsStateWithLifecycle()
    val images by viewModel.images.collectAsStateWithLifecycle()
    var imagePickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadImages() }

    val currentForm = form ?: return
    UrsBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            UrsText(
                text = stringResource(
                    if (currentForm.editingId != null) R.string.product_management_edit_category else R.string.product_management_add_category,
                ),
                style = UrsTheme.typography.cardTitle,
            )

            UrsTextField(
                value = currentForm.name,
                onValueChange = viewModel::setCategoryName,
                label = stringResource(R.string.product_management_name_label),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            UrsText(text = stringResource(R.string.product_management_image_label), style = UrsTheme.typography.caption)
            Row(verticalAlignment = Alignment.CenterVertically) {
                UrsSquareTile(
                    title = "",
                    catalogImageId = currentForm.imageId?.toIntOrNull(),
                    onClick = { imagePickerOpen = true },
                    modifier = Modifier.size(72.dp),
                )
            }

            if (currentForm.submitFailed) {
                UrsText(text = stringResource(R.string.error_save), color = FormErrorColor)
            }

            UrsButton(
                text = stringResource(if (currentForm.submitting) R.string.saving else R.string.save),
                onClick = viewModel::submitCategoryForm,
                enabled = currentForm.isValid && !currentForm.submitting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (imagePickerOpen) {
        UrsBottomSheet(onDismissRequest = { imagePickerOpen = false }) {
            UrsText(
                text = stringResource(R.string.product_management_image_picker_title),
                style = UrsTheme.typography.cardTitle,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
            )
            CatalogImagePicker(
                images = images,
                onSelect = {
                    viewModel.setCategoryImage(it)
                    imagePickerOpen = false
                },
            )
        }
    }
}
