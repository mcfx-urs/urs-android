package ch.mcfx.urs.inventory

import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import ch.mcfx.urs.settings.PortraitCaptureActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.local.CatalogProductEntity
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.ui.components.CatalogImagePicker
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsCheckbox
import ch.mcfx.urs.ui.components.UrsFab
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.components.ursScreenContentPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

// Fixed warning-color tones, independent of the light/dark theme palette —
// same values as the earlier row list this restores.
private val FirstWarningColor = Color(0xFFE0813F)
private val SecondWarningColor = Color(0xFFD64545)

private val FabIconStyle = TextStyle(fontSize = 28.sp)

// Quantity-change sweep (GitHub issue #8) — confirms a −/+ tap landed, since
// the number and the buttons sit close together. Fixed colors regardless of
// theme/row background, same reasoning as the warning tones above.
private val SweepDecreaseColor = Color(0xFFE5484D)
private val SweepIncreaseColor = Color(0xFF2E6BFF)
private const val SweepWidthFraction = 0.4f
private const val SweepDurationMillis = 450
private const val SweepReducedMotionDurationMillis = 150
private const val SweepPeakAlpha = 0.55f

private enum class SweepDirection { DECREASE, INCREASE }

@Composable
fun ProductListScreen(
    inventoryId: String,
    inventoryName: String,
    viewModel: ProductsViewModel = viewModel(
        factory = ProductsViewModel.factory(inventoryId, inventoryName),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val showForm by viewModel.showForm.collectAsStateWithLifecycle()
    val settingsForm by viewModel.settingsForm.collectAsStateWithLifecycle()
    val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()
    val imageSuggestionsOpen by viewModel.imageSuggestionsOpen.collectAsStateWithLifecycle()
    val suggestionImages by viewModel.images.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            UrsText(
                inventoryName,
                style = UrsTheme.typography.screenTitle,
                color = UrsTheme.colors.accent,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
            )
            when (val state = uiState) {
                ProductsUiState.Loading -> Box(Modifier.fillMaxSize()) {
                    UrsProgressIndicator(Modifier.align(Alignment.Center))
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
            AddInventoryProductForm(form = formState, query = query, results = results, viewModel = viewModel)
        }
    }

    if (showSettings) {
        UrsBottomSheet(onDismissRequest = viewModel::closeSettings) {
            ProductSettingsForm(form = settingsForm, viewModel = viewModel)
        }
    }

    if (imageSuggestionsOpen) {
        UrsBottomSheet(onDismissRequest = viewModel::cancelImageSuggestions) {
            UrsText(
                text = stringResource(R.string.product_image_suggestions_title),
                style = UrsTheme.typography.cardTitle,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
            )
            CatalogImagePicker(
                images = suggestionImages,
                initialQuery = query,
                onSelect = viewModel::confirmCreateAndTrackWithImage,
            )
            UrsOutlinedButton(
                text = stringResource(R.string.product_image_suggestions_skip),
                onClick = { viewModel.confirmCreateAndTrackWithImage(null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductList(
    products: List<InventoryProductTile>,
    onIncrement: (InventoryProductTile) -> Unit,
    onDecrement: (InventoryProductTile) -> Unit,
    onDeleteProduct: (InventoryProductTile) -> Unit,
    onLongPress: (InventoryProductTile) -> Unit,
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
        contentPadding = ursScreenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(products, key = { it.product.id }) { tile ->
            // null = "not currently tracked" (paused) — one step below 0,
            // not the same as it. Suppresses warning colors regardless of
            // thresholds, since there's no meaningful stock level to warn
            // about while a product isn't being tracked.
            val product = tile.product
            val quantity = product.quantity
            val warningColor = when {
                quantity == null -> null
                product.secondThreshold != null && quantity <= product.secondThreshold -> SecondWarningColor
                product.firstThreshold != null && quantity <= product.firstThreshold -> FirstWarningColor
                else -> null
            }
            val contentColor = if (warningColor != null) Color.White else UrsTheme.colors.accent
            // The stepper/settings/delete actions all need a real backend id.
            val synced = product.serverId != null

            var sweepDirection by remember { mutableStateOf(SweepDirection.INCREASE) }
            // 0 = never triggered (no overlay at all yet); LaunchedEffect keyed
            // on this restarts the animation on every tap, including repeated
            // taps in the same direction, which a value/enum key wouldn't.
            var sweepTriggerId by remember { mutableIntStateOf(0) }
            val sweepProgress = remember { Animatable(0f) }
            val context = LocalContext.current
            val reduceMotion = remember {
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
            LaunchedEffect(sweepTriggerId) {
                if (sweepTriggerId == 0) return@LaunchedEffect
                sweepProgress.snapTo(0f)
                sweepProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = if (reduceMotion) SweepReducedMotionDurationMillis else SweepDurationMillis,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }

            UrsCard(
                radius = Radius.row,
                contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s),
                backgroundColor = warningColor ?: UrsTheme.colors.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = { onLongPress(tile) }),
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UrsText(
                            tile.name,
                            style = UrsTheme.typography.cardTitle,
                            color = contentColor,
                            modifier = Modifier.weight(1f),
                        )
                        ProductSyncStatusPill(product.syncStatus)
                        UrsIconButton(
                            onClick = {
                                onDecrement(tile)
                                sweepDirection = SweepDirection.DECREASE
                                sweepTriggerId++
                            },
                            enabled = synced && quantity != null,
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
                            onClick = {
                                onIncrement(tile)
                                sweepDirection = SweepDirection.INCREASE
                                sweepTriggerId++
                            },
                            enabled = synced,
                            contentDescription = stringResource(R.string.inventory_product_increment),
                            imageVector = Icons.Filled.Add,
                            tint = contentColor,
                        )
                        UrsIconButton(
                            onClick = { onDeleteProduct(tile) },
                            enabled = synced,
                            contentDescription = stringResource(R.string.inventory_product_remove, tile.name),
                            imageVector = Icons.Filled.Close,
                            tint = contentColor,
                        )
                    }
                    if (sweepTriggerId != 0 && sweepProgress.value < 1f) {
                        QuantitySweepOverlay(direction = sweepDirection, progress = sweepProgress.value, reduceMotion = reduceMotion)
                    }
                }
            }
        }
    }
}

// Solid gradient block, ~40% of the row's width, soft feathered edges via
// the transparent-to-color-to-transparent gradient — decrease sweeps left to
// right, increase sweeps right to left. With reduce-motion on, no
// translation at all: a plain opacity flash across the full row instead.
@Composable
private fun BoxScope.QuantitySweepOverlay(direction: SweepDirection, progress: Float, reduceMotion: Boolean) {
    val color = if (direction == SweepDirection.DECREASE) SweepDecreaseColor else SweepIncreaseColor
    // Rises and falls smoothly across the animation instead of an abrupt cut.
    val alpha = kotlin.math.sin(progress * Math.PI).toFloat().coerceIn(0f, 1f) * SweepPeakAlpha

    if (reduceMotion) {
        Box(Modifier.matchParentSize().background(color.copy(alpha = alpha)))
        return
    }

    BoxWithConstraints(Modifier.matchParentSize()) {
        val leftToRight = direction == SweepDirection.DECREASE
        val startFraction = if (leftToRight) -SweepWidthFraction else 1f
        val endFraction = if (leftToRight) 1f else -SweepWidthFraction
        val currentFraction = startFraction + (endFraction - startFraction) * progress
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(SweepWidthFraction)
                .offset(x = maxWidth * currentFraction)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = alpha), Color.Transparent))),
        )
    }
}

@Composable
private fun ProductSyncStatusPill(status: SyncStatus) {
    when (status) {
        SyncStatus.PENDING -> UrsPill(text = stringResource(R.string.fill_status_pending))
        SyncStatus.FAILED -> UrsPill(
            text = stringResource(R.string.fill_status_failed),
            containerColor = SecondWarningColor.copy(alpha = 0.15f),
            contentColor = SecondWarningColor,
        )
        SyncStatus.SYNCED -> Unit
    }
}

@Composable
private fun AddInventoryProductForm(
    form: AddProductFormState,
    query: String,
    results: List<CatalogProductEntity>,
    viewModel: ProductsViewModel,
) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl).heightIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.inventory_product_add), style = UrsTheme.typography.screenTitle)

        UrsTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            label = stringResource(R.string.inventory_product_name),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
            result.contents?.let { viewModel.onBarcodeScanned(it) }
        }
        UrsOutlinedButton(
            text = stringResource(if (form.scanning) R.string.product_scanning else R.string.product_scan_barcode),
            enabled = !form.scanning,
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

        if (query.isNotBlank()) {
            if (results.isEmpty()) {
                UrsText(
                    stringResource(R.string.shoppinglist_add_product_no_results),
                    color = UrsTheme.colors.onSurfaceMuted,
                    style = UrsTheme.typography.body,
                )
                if (form.submitFailed) {
                    UrsText(stringResource(R.string.error_save), color = UrsTheme.colors.onSurfaceMuted, style = UrsTheme.typography.body)
                }
                UrsButton(
                    text = stringResource(R.string.shoppinglist_add_custom_product),
                    onClick = viewModel::createAndTrackProduct,
                    enabled = !form.submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    items(results, key = { it.id }) { result ->
                        CatalogResultRow(result = result, onClick = { viewModel.trackExistingProduct(result) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogResultRow(result: CatalogProductEntity, onClick: () -> Unit) {
    UrsCard(
        radius = Radius.row,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsIcon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = UrsTheme.colors.accent,
                modifier = Modifier.width(24.dp),
            )
            UrsText(result.name, style = UrsTheme.typography.body)
        }
    }
}

@Composable
private fun ProductSettingsForm(form: ProductSettingsFormState, viewModel: ProductsViewModel) {
    val tile = form.product ?: return
    val thresholdOrderError = stringResource(R.string.inventory_settings_error_threshold_order)
    val reminderFieldsError = stringResource(R.string.inventory_settings_error_reminder_fields)
    val saveError = stringResource(R.string.error_save)
    val reminderTitle = stringResource(R.string.inventory_settings_reminder_title, tile.name)
    val reminderBody = stringResource(R.string.inventory_settings_reminder_body, tile.name)

    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(
            stringResource(R.string.inventory_settings_title, tile.name),
            style = UrsTheme.typography.screenTitle,
        )

        UrsTextField(
            value = form.quantity,
            onValueChange = viewModel::setSettingsQuantity,
            label = stringResource(R.string.inventory_settings_quantity),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Weekly-batch quick-adjust (e.g. medication prepared once a week,
        // always 7 units) — fixed step, independent of the row's own −/+1.
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsOutlinedButton(
                text = stringResource(R.string.inventory_settings_quantity_minus_seven),
                onClick = { viewModel.adjustSettingsQuantityBySeven(-7) },
                modifier = Modifier.weight(1f),
            )
            UrsOutlinedButton(
                text = stringResource(R.string.inventory_settings_quantity_plus_seven),
                onClick = { viewModel.adjustSettingsQuantityBySeven(7) },
                modifier = Modifier.weight(1f),
            )
        }
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
            UrsText(form.error, color = UrsTheme.colors.onSurfaceMuted, style = UrsTheme.typography.body)
        }

        UrsButton(
            text = stringResource(if (form.submitting) R.string.saving else R.string.save),
            onClick = {
                viewModel.submitSettings(
                    thresholdOrderError = thresholdOrderError,
                    reminderFieldsError = reminderFieldsError,
                    saveError = saveError,
                    reminderTitle = reminderTitle,
                    reminderBody = reminderBody,
                )
            },
            enabled = !form.submitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
