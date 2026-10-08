package ch.mcfx.urs.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.mcfx.urs.R
import ch.mcfx.urs.data.CatalogRepository
import ch.mcfx.urs.data.DEFAULT_SIZE_UNIT
import ch.mcfx.urs.data.SIZE_UNITS
import ch.mcfx.urs.data.local.CatalogProductEntity
import ch.mcfx.urs.data.parseQuantity
import ch.mcfx.urs.data.remote.BarcodeSuggestionDto
import ch.mcfx.urs.data.remote.apiError
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * A scanned barcode no catalog product has yet, being turned into a clone of
 * a template product. Brand and size start from the Open Food Facts
 * suggestion and stay editable.
 */
data class CloneDraft(
    val barcode: String,
    val templateId: String? = null,
    val templateName: String? = null,
    val brand: String = "",
    val sizeAmount: String = "",
    val sizeUnit: String = DEFAULT_SIZE_UNIT,
    val submitting: Boolean = false,
    // Backend message of the last failure (null with [failed] = generic failure).
    val errorMessage: String? = null,
    val failed: Boolean = false,
) {
    private val amountValue: Double?
        get() = sizeAmount.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

    val canSubmit: Boolean
        get() = templateId != null && !submitting &&
            (brand.isNotBlank() || amountValue != null) &&
            (sizeAmount.isBlank() || amountValue != null)

    // Canonical amount for the backend, "" when no size was entered.
    val amountForBackend: String
        get() = amountValue?.let { java.math.BigDecimal(it.toString()).stripTrailingZeros().toPlainString() }.orEmpty()
}

/** State holder for the clone-from-scan sheet, shared by the Inventory and Shopping List add flows. */
class CloneDraftController(
    private val scope: CoroutineScope,
    private val repository: CatalogRepository,
) {
    private val _draft = MutableStateFlow<CloneDraft?>(null)
    val draft: StateFlow<CloneDraft?> = _draft.asStateFlow()

    private val _templateQuery = MutableStateFlow("")
    val templateQuery: StateFlow<String> = _templateQuery.asStateFlow()

    private val _templateResults = MutableStateFlow<List<CatalogProductEntity>>(emptyList())
    val templateResults: StateFlow<List<CatalogProductEntity>> = _templateResults.asStateFlow()

    fun open(barcode: String, suggestion: BarcodeSuggestionDto?) {
        val size = suggestion?.quantity?.let(::parseQuantity)
        _templateQuery.value = suggestion?.name.orEmpty()
        _templateResults.value = emptyList()
        _draft.value = CloneDraft(
            barcode = barcode,
            brand = suggestion?.brand?.substringBefore(',')?.trim().orEmpty(),
            sizeAmount = size?.first.orEmpty(),
            sizeUnit = size?.second ?: DEFAULT_SIZE_UNIT,
        )
        if (_templateQuery.value.isNotBlank()) setTemplateQuery(_templateQuery.value)
    }

    fun dismiss() {
        _draft.value = null
    }

    fun setTemplateQuery(query: String) {
        _templateQuery.value = query
        scope.launch {
            _templateResults.value = if (query.isBlank()) emptyList() else repository.searchTemplates(query)
        }
    }

    fun selectTemplate(template: CatalogProductEntity?) {
        _draft.update { it?.copy(templateId = template?.id, templateName = template?.name, errorMessage = null, failed = false) }
    }

    fun setBrand(brand: String) = _draft.update { it?.copy(brand = brand) }

    fun setSizeAmount(amount: String) = _draft.update { it?.copy(sizeAmount = amount) }

    fun setSizeUnit(unit: String) = _draft.update { it?.copy(sizeUnit = unit) }

    /**
     * Creates the clone with the scanned barcode and hands it to [onCreated].
     * If a clone with the same template, brand and size already existed, the
     * backend returns that one and the barcode is mapped to it afterwards.
     */
    fun submit(onCreated: suspend (CatalogProductEntity) -> Unit) {
        val draft = _draft.value ?: return
        val templateId = draft.templateId ?: return
        if (!draft.canSubmit) return
        scope.launch {
            _draft.update { it?.copy(submitting = true, errorMessage = null, failed = false) }
            try {
                val amount = draft.amountForBackend
                var clone = repository.createClone(
                    templateId = templateId,
                    brand = draft.brand.trim(),
                    sizeAmount = amount,
                    sizeUnit = if (amount.isEmpty()) "" else draft.sizeUnit,
                    barcode = draft.barcode,
                )
                if (draft.barcode !in clone.barcodeList) {
                    repository.addBarcode(clone.id, draft.barcode)
                    clone = repository.getProduct(clone.id) ?: clone
                }
                onCreated(clone)
                _draft.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                val message = e.apiError()?.error?.takeIf { it.isNotBlank() }
                _draft.update { it?.copy(submitting = false, errorMessage = message, failed = message == null) }
            } catch (_: Exception) {
                _draft.update { it?.copy(submitting = false, failed = true) }
            }
        }
    }
}

private val CloneErrorColor = Color(0xFFD64545)

/** Bottom-sheet content for [CloneDraftController]; shown while its draft is non-null. */
@Composable
fun CloneFromScanSheet(controller: CloneDraftController, onSubmit: () -> Unit) {
    val draft by controller.draft.collectAsStateWithLifecycle()
    val templateQuery by controller.templateQuery.collectAsStateWithLifecycle()
    val templateResults by controller.templateResults.collectAsStateWithLifecycle()
    val current = draft ?: return

    UrsBottomSheet(onDismissRequest = controller::dismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l)
                .padding(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            UrsText(text = stringResource(R.string.product_scan_variant_title), style = UrsTheme.typography.cardTitle)
            UrsText(text = current.barcode, color = UrsTheme.colors.onSurfaceMuted)

            UrsText(text = stringResource(R.string.product_management_template_label), style = UrsTheme.typography.caption)
            if (current.templateId == null) {
                CatalogProductPicker(
                    query = templateQuery,
                    onQueryChange = controller::setTemplateQuery,
                    results = templateResults,
                    onSelect = controller::selectTemplate,
                )
            } else {
                UrsText(text = current.templateName.orEmpty())
                UrsOutlinedButton(
                    text = stringResource(R.string.product_management_template_change),
                    onClick = { controller.selectTemplate(null) },
                )
            }

            UrsTextField(
                value = current.brand,
                onValueChange = controller::setBrand,
                label = stringResource(R.string.product_management_brand_label),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Top) {
                UrsTextField(
                    value = current.sizeAmount,
                    onValueChange = controller::setSizeAmount,
                    label = stringResource(R.string.product_management_size_amount_label),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                UrsDropdownField(
                    label = stringResource(R.string.product_management_size_unit_label),
                    options = SIZE_UNITS,
                    selectedLabel = current.sizeUnit,
                    optionLabel = { it },
                    onSelect = controller::setSizeUnit,
                    modifier = Modifier.weight(1f),
                )
            }

            if (current.errorMessage != null) {
                UrsText(text = current.errorMessage, color = CloneErrorColor)
            } else if (current.failed) {
                UrsText(text = stringResource(R.string.error_save), color = CloneErrorColor)
            }
            UrsButton(
                text = stringResource(if (current.submitting) R.string.saving else R.string.product_scan_variant_create),
                onClick = onSubmit,
                enabled = current.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
