package ch.mcfx.urs.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.mcfx.urs.R
import ch.mcfx.urs.data.SIZE_UNITS
import ch.mcfx.urs.ui.components.CatalogImagePicker
import ch.mcfx.urs.ui.components.CatalogProductPicker
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsErrorDialog
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsSquareTile
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import ch.mcfx.urs.ui.tokens.Spacing

// No "error" role in the design system's palette yet — same local-constant
// pattern already used in WorkTimeScreen/WorkTimeAddScreen/FuelAddScreen.
private val FormErrorColor = Color(0xFFD64545)

/**
 * Create/edit form for a manually-created catalog product — shown
 * as a [UrsBottomSheet] from [ProductManagementScreen], driven entirely by
 * [ProductManagementViewModel.productForm] (dual-purpose: `editingId == null`
 * creates, otherwise edits that product). Category and image are both
 * optional — a product can stay uncategorized/imageless, same as today's
 * quick-create flow.
 */
@Composable
fun CatalogProductFormSheet(viewModel: ProductManagementViewModel, onDismissRequest: () -> Unit) {
    val form by viewModel.productForm.collectAsStateWithLifecycle()
    val categories by viewModel.allCategories.collectAsStateWithLifecycle()
    val templateQuery by viewModel.templateQuery.collectAsStateWithLifecycle()
    val templateResults by viewModel.templateResults.collectAsStateWithLifecycle()
    var imagePickerOpen by remember { mutableStateOf(false) }
    var templatePickerOpen by remember { mutableStateOf(false) }

    val currentForm = form ?: return
    UrsBottomSheet(onDismissRequest = onDismissRequest) {
        // UrsBottomSheet already shrinks to fit above the keyboard (its own
        // windowInsetsPadding), but this form's own content was never
        // scrollable — with the description field/Generate button added
        // the sheet can now be taller than the space left above
        // the keyboard, pushing whatever's focused (or the Save button)
        // out of view with no way to reach it. verticalScroll fixes that.
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l)
                .padding(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            UrsText(
                text = stringResource(
                    if (currentForm.editingId != null) R.string.product_management_edit_product else R.string.product_management_add_product,
                ),
                style = UrsTheme.typography.cardTitle,
            )

            UrsText(text = stringResource(R.string.product_management_template_label), style = UrsTheme.typography.caption)
            UrsText(text = currentForm.templateName ?: stringResource(R.string.product_management_template_none))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                UrsOutlinedButton(
                    text = stringResource(
                        if (currentForm.isClone) R.string.product_management_template_change else R.string.product_management_template_picker_title,
                    ),
                    onClick = { templatePickerOpen = true },
                )
                if (currentForm.isClone) {
                    UrsOutlinedButton(
                        text = stringResource(R.string.product_management_template_clear),
                        onClick = { viewModel.setTemplate(null) },
                    )
                }
            }

            if (currentForm.isClone) {
                UrsText(
                    text = stringResource(R.string.product_management_template_inherited),
                    style = UrsTheme.typography.caption,
                    color = UrsTheme.colors.onSurfaceMuted,
                )
                UrsTextField(
                    value = currentForm.brand,
                    onValueChange = viewModel::setBrand,
                    label = stringResource(R.string.product_management_brand_label),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Top) {
                    UrsTextField(
                        value = currentForm.sizeAmount,
                        onValueChange = viewModel::setSizeAmount,
                        label = stringResource(R.string.product_management_size_amount_label),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    UrsDropdownField(
                        label = stringResource(R.string.product_management_size_unit_label),
                        options = SIZE_UNITS,
                        selectedLabel = currentForm.sizeUnit,
                        optionLabel = { it },
                        onSelect = viewModel::setSizeUnit,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                UrsTextField(
                    value = currentForm.name,
                    onValueChange = viewModel::setProductName,
                    label = stringResource(R.string.product_management_name_label),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                val selectedCategory = categories.find { it.id == currentForm.categoryId }
                val noCategoryLabel = stringResource(R.string.product_management_no_category)
                UrsDropdownField(
                    label = stringResource(R.string.product_management_category_label),
                    options = listOf(null) + categories,
                    selectedLabel = selectedCategory?.name ?: noCategoryLabel,
                    optionLabel = { it?.name ?: noCategoryLabel },
                    onSelect = { viewModel.setProductCategory(it?.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            UrsText(text = stringResource(R.string.product_management_barcodes_label), style = UrsTheme.typography.caption)
            currentForm.barcodes.forEach { barcode ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UrsText(text = barcode, modifier = Modifier.weight(1f))
                    UrsIconButton(
                        onClick = { viewModel.removeBarcode(barcode) },
                        contentDescription = stringResource(R.string.product_management_barcode_remove),
                        imageVector = Icons.Filled.Close,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Top) {
                UrsTextField(
                    value = currentForm.barcodeInput,
                    onValueChange = viewModel::setBarcodeInput,
                    label = stringResource(R.string.product_management_barcode_add_label),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                UrsOutlinedButton(
                    text = stringResource(R.string.product_management_barcode_add),
                    onClick = viewModel::addBarcode,
                    enabled = currentForm.barcodeInput.isNotBlank(),
                )
            }
            if (currentForm.barcodeLookupRunning) {
                UrsProgressIndicator(size = 24.dp, strokeWidth = 2.dp)
            }
            currentForm.barcodeTakenBy?.let {
                UrsText(text = stringResource(R.string.product_management_barcode_taken_by, it), color = FormErrorColor)
            }
            val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
                result.contents?.let(viewModel::addScannedBarcode)
            }
            UrsOutlinedButton(
                text = stringResource(R.string.product_management_barcode_scan),
                onClick = {
                    scanLauncher.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.PRODUCT_CODE_TYPES)
                            .setBeepEnabled(false)
                            .setOrientationLocked(true)
                            .setCaptureActivity(PortraitCaptureActivity::class.java),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            UrsText(text = stringResource(R.string.product_management_image_label), style = UrsTheme.typography.caption)
            Row(verticalAlignment = Alignment.CenterVertically) {
                UrsSquareTile(
                    title = "",
                    catalogImageId = currentForm.imageId?.toIntOrNull() ?: currentForm.templateImageId,
                    onClick = { imagePickerOpen = true },
                    modifier = Modifier.size(72.dp),
                )
            }

            // Description is transient prompt detail for image
            // generation only, never sent to createProduct/updateProduct and
            // never persisted on the product itself.
            UrsTextField(
                value = currentForm.description,
                onValueChange = viewModel::setProductDescription,
                label = stringResource(R.string.product_management_description_label),
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            UrsOutlinedButton(
                text = stringResource(
                    if (currentForm.generatingImage) R.string.product_management_generating_image else R.string.product_management_generate_image,
                ),
                onClick = viewModel::generateProductImage,
                enabled = currentForm.imagePromptName.isNotBlank() && !currentForm.generatingImage,
                modifier = Modifier.fillMaxWidth(),
            )
            if (currentForm.errorMessage != null) {
                UrsText(text = currentForm.errorMessage, color = FormErrorColor)
            } else if (currentForm.nameConflict) {
                UrsText(text = stringResource(R.string.product_management_name_conflict), color = FormErrorColor)
            } else if (currentForm.submitFailed) {
                UrsText(text = stringResource(R.string.error_save), color = FormErrorColor)
            }

            UrsButton(
                text = stringResource(if (currentForm.submitting) R.string.saving else R.string.save),
                onClick = viewModel::submitProductForm,
                enabled = currentForm.isValid && !currentForm.submitting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (templatePickerOpen) {
        UrsBottomSheet(onDismissRequest = { templatePickerOpen = false }) {
            UrsText(
                text = stringResource(R.string.product_management_template_picker_title),
                style = UrsTheme.typography.cardTitle,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
            )
            CatalogProductPicker(
                query = templateQuery,
                onQueryChange = viewModel::setTemplateQuery,
                results = templateResults,
                onSelect = {
                    viewModel.setTemplate(it)
                    templatePickerOpen = false
                },
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
                loadPage = viewModel::searchImages,
                onSelect = {
                    viewModel.setProductImage(it)
                    imagePickerOpen = false
                },
            )
        }
    }

    if (currentForm.generateImageError != null) {
        val message = stringResource(
            when (currentForm.generateImageError) {
                ImageGenerationErrorKind.NETWORK -> R.string.product_management_generate_image_failed_network
                ImageGenerationErrorKind.SERVER -> R.string.product_management_generate_image_failed_server
                ImageGenerationErrorKind.UNKNOWN -> R.string.product_management_generate_image_failed_unknown
            },
        )
        UrsErrorDialog(
            title = stringResource(R.string.product_management_generate_image_failed),
            message = message,
            onDismiss = viewModel::dismissImageGenerationError,
        )
    }
}
