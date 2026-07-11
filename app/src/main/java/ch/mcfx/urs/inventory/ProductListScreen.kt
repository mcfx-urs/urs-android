package ch.mcfx.urs.inventory

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.remote.InventoryProductDto
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsCheckbox
import ch.mcfx.urs.ui.components.UrsFab
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

// Fixed warning-color tones, independent of the light/dark theme palette.
private val FirstWarningColor = Color(0xFFE0813F)
private val SecondWarningColor = Color(0xFFD64545)

// No "error" role in the design system's palette yet (see Color.kt) — kept
// distinct from the two warning colors above, which mean something else
// (stock level, not form validation).
private val FormErrorColor = Color(0xFFD64545)

// Same reasoning as FuelScreen's FabIconStyle — the type scale has no "big
// FAB glyph" size of its own.
private val FabIconStyle = TextStyle(fontSize = 28.sp)

@Composable
fun ProductListScreen(
    categoryId: String,
    categoryName: String,
    viewModel: ProductsViewModel = viewModel(factory = ProductsViewModel.factory(categoryId, categoryName)),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val showForm by viewModel.showForm.collectAsStateWithLifecycle()
    val settingsForm by viewModel.settingsForm.collectAsStateWithLifecycle()
    val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            UrsText(
                categoryName,
                style = UrsTheme.typography.screenTitle,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
            )
            when (val state = uiState) {
                ProductsUiState.Loading -> Box(Modifier.fillMaxSize()) {
                    UrsProgressIndicator(Modifier.align(Alignment.Center))
                }

                is ProductsUiState.Error -> Box(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        UrsText(stringResource(R.string.error_load), style = UrsTheme.typography.body)
                        Spacer(Modifier.height(Spacing.l))
                        UrsButton(text = stringResource(R.string.retry), onClick = viewModel::load)
                    }
                }

                is ProductsUiState.Data -> ProductList(
                    products = state.products,
                    onIncrement = viewModel::increment,
                    onDecrement = viewModel::decrement,
                    onDeleteProduct = viewModel::deleteProduct,
                    onLongPress = viewModel::openSettings,
                )
            }
        }

        if (uiState is ProductsUiState.Data) {
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
            ProductForm(form = formState, viewModel = viewModel)
        }
    }

    if (showSettings) {
        UrsBottomSheet(onDismissRequest = viewModel::closeSettings) {
            ProductSettingsForm(form = settingsForm, viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductList(
    products: List<InventoryProductDto>,
    onIncrement: (InventoryProductDto) -> Unit,
    onDecrement: (InventoryProductDto) -> Unit,
    onDeleteProduct: (String) -> Unit,
    onLongPress: (InventoryProductDto) -> Unit,
) {
    if (products.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            UrsText(
                stringResource(R.string.inventory_products_empty),
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
        items(products, key = { it.id }) { product ->
            // null = "not currently tracked" (paused) — one step below 0,
            // not the same as it. Suppresses warning colors regardless of
            // thresholds, since there's no meaningful stock level to warn
            // about while a product isn't being tracked.
            val quantity = product.quantity.toIntOrNull()
            val secondThreshold = product.secondThreshold.toIntOrNull()
            val firstThreshold = product.firstThreshold.toIntOrNull()
            val warningColor = when {
                quantity == null -> null
                secondThreshold != null && quantity <= secondThreshold -> SecondWarningColor
                firstThreshold != null && quantity <= firstThreshold -> FirstWarningColor
                else -> null
            }
            val contentColor = if (warningColor != null) Color.White else UrsTheme.colors.onSurface

            UrsCard(
                radius = Radius.row,
                contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s),
                backgroundColor = warningColor ?: UrsTheme.colors.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = { onLongPress(product) }),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UrsText(
                        product.name,
                        style = UrsTheme.typography.cardTitle,
                        color = contentColor,
                        modifier = Modifier.weight(1f),
                    )
                    UrsIconButton(
                        onClick = { onDecrement(product) },
                        enabled = quantity != null,
                        contentDescription = stringResource(R.string.inventory_product_decrement),
                        imageVector = Icons.Filled.Remove,
                        tint = contentColor,
                    )
                    UrsText(
                        quantity?.toString() ?: stringResource(R.string.inventory_product_not_tracked),
                        style = UrsTheme.typography.cardTitle.copy(textAlign = TextAlign.Center),
                        color = contentColor,
                        modifier = Modifier.width(32.dp),
                    )
                    UrsIconButton(
                        onClick = { onIncrement(product) },
                        contentDescription = stringResource(R.string.inventory_product_increment),
                        imageVector = Icons.Filled.Add,
                        tint = contentColor,
                    )
                    UrsIconButton(
                        onClick = { onDeleteProduct(product.id) },
                        contentDescription = stringResource(R.string.inventory_product_remove, product.name),
                        imageVector = Icons.Filled.Close,
                        tint = contentColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductForm(form: ProductFormState, viewModel: ProductsViewModel) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.inventory_product_add), style = UrsTheme.typography.screenTitle)

        UrsTextField(
            value = form.name,
            onValueChange = viewModel::setName,
            label = stringResource(R.string.inventory_product_name),
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

@Composable
private fun ProductSettingsForm(form: ProductSettingsFormState, viewModel: ProductsViewModel) {
    val product = form.product ?: return
    val thresholdOrderError = stringResource(R.string.inventory_settings_error_threshold_order)
    val reminderFieldsError = stringResource(R.string.inventory_settings_error_reminder_fields)
    val reminderTitle = stringResource(R.string.inventory_settings_reminder_title, product.name)
    val reminderBody = stringResource(R.string.inventory_settings_reminder_body, product.name)

    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(
            stringResource(R.string.inventory_settings_title, product.name),
            style = UrsTheme.typography.screenTitle,
        )

        UrsTextField(
            value = form.quantity,
            onValueChange = viewModel::setSettingsQuantity,
            label = stringResource(R.string.inventory_settings_quantity),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsTextField(
            value = form.firstThreshold,
            onValueChange = viewModel::setFirstThreshold,
            label = stringResource(R.string.inventory_settings_first_threshold),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsTextField(
            value = form.secondThreshold,
            onValueChange = viewModel::setSecondThreshold,
            label = stringResource(R.string.inventory_settings_second_threshold),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            UrsCheckbox(checked = form.reminderEnabled, onCheckedChange = viewModel::setReminderEnabled)
            Spacer(Modifier.width(Spacing.s))
            UrsText(stringResource(R.string.inventory_settings_reminder_enable), style = UrsTheme.typography.body)
        }

        if (form.reminderEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                UrsTextField(
                    value = form.reminderHour,
                    onValueChange = viewModel::setReminderHour,
                    label = stringResource(R.string.inventory_settings_reminder_hour),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                UrsTextField(
                    value = form.reminderMinute,
                    onValueChange = viewModel::setReminderMinute,
                    label = stringResource(R.string.inventory_settings_reminder_minute),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            UrsTextField(
                value = form.reminderThreshold,
                onValueChange = viewModel::setReminderThreshold,
                label = stringResource(R.string.inventory_settings_reminder_threshold),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (form.error != null) {
            UrsText(form.error, color = FormErrorColor, style = UrsTheme.typography.body)
        }

        UrsButton(
            text = stringResource(if (form.submitting) R.string.saving else R.string.save),
            onClick = {
                viewModel.submitSettings(
                    thresholdOrderError = thresholdOrderError,
                    reminderFieldsError = reminderFieldsError,
                    reminderTitle = reminderTitle,
                    reminderBody = reminderBody,
                )
            },
            enabled = !form.submitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
