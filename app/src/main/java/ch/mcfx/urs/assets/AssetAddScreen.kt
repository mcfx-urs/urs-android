package ch.mcfx.urs.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.AssetCategory
import ch.mcfx.urs.data.local.AssetCommentEntity
import ch.mcfx.urs.data.local.AssetComponentEntity
import ch.mcfx.urs.data.local.AssetEntity
import ch.mcfx.urs.data.toRaw
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsDateField
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.components.ursFormScrollPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import java.util.Locale

private val FormErrorColor = Color(0xFFD64545)

@Composable
fun AssetAddScreen(
    onDone: () -> Unit,
    /** Null creates a new asset; set edits that existing row (see [AssetsViewModel.openFormForEdit]). */
    assetId: Long? = null,
    viewModel: AssetsViewModel = viewModel(factory = AssetsViewModel.Factory),
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val showForm by viewModel.showForm.collectAsStateWithLifecycle()
    val components by viewModel.components.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { if (assetId != null) viewModel.openFormForEdit(assetId) else viewModel.openForm() }

    var hasOpened by remember { mutableStateOf(false) }
    LaunchedEffect(showForm) {
        if (showForm) {
            hasOpened = true
        } else if (hasOpened) {
            onDone()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showForm) {
            AssetForm(form = formState, components = components, comments = comments, viewModel = viewModel)
        } else if (assetId != null) {
            UrsProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun AssetForm(
    form: AssetFormState,
    components: List<AssetComponentEntity>,
    comments: List<AssetCommentEntity>,
    viewModel: AssetsViewModel,
) {
    val focusManager = LocalFocusManager.current
    val nextFieldAction = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })

    Column(
        modifier = Modifier.ursFormScrollPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsTextField(
            value = form.name,
            onValueChange = viewModel::setName,
            label = stringResource(R.string.assets_name),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = nextFieldAction,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsDropdownField(
            label = stringResource(R.string.assets_category),
            options = AssetCategory.entries,
            selectedLabel = form.category?.toRaw(),
            optionLabel = { it.toRaw() },
            onSelect = viewModel::setCategory,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsTextField(
            value = form.location,
            onValueChange = viewModel::setLocation,
            label = stringResource(R.string.assets_location),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = nextFieldAction,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (form.isEditing) {
            val statusLabels = assetStatusLabels()
            UrsDropdownField(
                label = stringResource(R.string.assets_status),
                options = listOf(AssetEntity.STATUS_ACTIVE, AssetEntity.STATUS_SOLD, AssetEntity.STATUS_DISPOSED, AssetEntity.STATUS_LOST),
                selectedLabel = statusLabels.getValue(form.status),
                optionLabel = { statusLabels.getValue(it) },
                onSelect = viewModel::setStatus,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        TagsEditor(form = form, viewModel = viewModel)

        if (form.isEditing) {
            ComponentsEditor(components = components, form = form, viewModel = viewModel)
            CommentsEditor(comments = comments, form = form, viewModel = viewModel)
        } else {
            FirstComponentSection(form = form, viewModel = viewModel)
        }

        if (form.submitFailed) {
            UrsText(stringResource(R.string.error_save), color = FormErrorColor, style = UrsTheme.typography.body)
        }

        UrsButton(
            text = stringResource(if (form.submitting) R.string.saving else R.string.save),
            onClick = viewModel::submit,
            enabled = form.isValid && !form.submitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Plain lookup map, not a per-call @Composable function — UrsDropdownField's
// optionLabel is a plain (T) -> String lambda, which can't invoke
// stringResource itself. Duplicated (file-private) in AssetsScreen.kt too.
@Composable
private fun assetStatusLabels(): Map<String, String> = mapOf(
    AssetEntity.STATUS_ACTIVE to stringResource(R.string.assets_status_active),
    AssetEntity.STATUS_SOLD to stringResource(R.string.assets_status_sold),
    AssetEntity.STATUS_DISPOSED to stringResource(R.string.assets_status_disposed),
    AssetEntity.STATUS_LOST to stringResource(R.string.assets_status_lost),
)

@Composable
private fun TagsEditor(form: AssetFormState, viewModel: AssetsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (form.tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                form.tags.forEach { tag ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UrsPill(text = tag)
                        UrsIconButton(
                            onClick = { viewModel.removeTag(tag) },
                            contentDescription = stringResource(R.string.assets_remove_tag, tag),
                            imageVector = Icons.Filled.Close,
                        )
                    }
                }
            }
        }
        UrsTextField(
            value = form.tagInput,
            onValueChange = viewModel::setTagInput,
            label = stringResource(R.string.assets_field_tags),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.addTag(form.tagInput) }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FirstComponentSection(form: AssetFormState, viewModel: AssetsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        UrsText(stringResource(R.string.assets_first_purchase), style = UrsTheme.typography.caption, color = UrsTheme.colors.onSurfaceMuted)
        UrsTextField(
            value = form.firstComponent.description,
            onValueChange = viewModel::setFirstComponentDescription,
            label = stringResource(R.string.assets_component_description),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsTextField(
            value = form.firstComponent.manufacturer,
            onValueChange = viewModel::setFirstComponentManufacturer,
            label = stringResource(R.string.assets_component_manufacturer),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsTextField(
                value = form.firstComponent.price,
                onValueChange = viewModel::setFirstComponentPrice,
                label = stringResource(R.string.assets_component_price),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            UrsDateField(
                value = form.firstComponent.purchaseDate,
                onValueChange = viewModel::setFirstComponentPurchaseDate,
                label = stringResource(R.string.assets_component_date),
                modifier = Modifier.weight(1f),
            )
        }
        UrsTextField(
            value = form.firstComponent.dealer,
            onValueChange = viewModel::setFirstComponentDealer,
            label = stringResource(R.string.assets_component_dealer),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ComponentsEditor(components: List<AssetComponentEntity>, form: AssetFormState, viewModel: AssetsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        UrsText(stringResource(R.string.assets_components), style = UrsTheme.typography.caption, color = UrsTheme.colors.onSurfaceMuted)
        components.forEach { component ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    UrsText(component.description, style = UrsTheme.typography.body)
                    UrsText(
                        String.format(Locale.US, "CHF %.2f · %s", component.price.toDoubleOrNull() ?: 0.0, component.purchaseDate),
                        style = UrsTheme.typography.caption,
                        color = UrsTheme.colors.onSurfaceMuted,
                    )
                }
                UrsIconButton(
                    onClick = { viewModel.deleteComponent(component) },
                    contentDescription = stringResource(R.string.assets_component_delete),
                    imageVector = Icons.Filled.Close,
                )
            }
        }
        UrsTextField(
            value = form.newComponentInput.description,
            onValueChange = viewModel::setNewComponentDescription,
            label = stringResource(R.string.assets_component_description),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsTextField(
            value = form.newComponentInput.manufacturer,
            onValueChange = viewModel::setNewComponentManufacturer,
            label = stringResource(R.string.assets_component_manufacturer),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsTextField(
                value = form.newComponentInput.price,
                onValueChange = viewModel::setNewComponentPrice,
                label = stringResource(R.string.assets_component_price),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            UrsDateField(
                value = form.newComponentInput.purchaseDate,
                onValueChange = viewModel::setNewComponentPurchaseDate,
                label = stringResource(R.string.assets_component_date),
                modifier = Modifier.weight(1f),
            )
        }
        UrsTextField(
            value = form.newComponentInput.dealer,
            onValueChange = viewModel::setNewComponentDealer,
            label = stringResource(R.string.assets_component_dealer),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsButton(
            text = stringResource(R.string.assets_component_add),
            onClick = viewModel::addComponent,
            enabled = form.newComponentInput.isValid,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CommentsEditor(comments: List<AssetCommentEntity>, form: AssetFormState, viewModel: AssetsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        UrsText(stringResource(R.string.assets_comments), style = UrsTheme.typography.caption, color = UrsTheme.colors.onSurfaceMuted)
        comments.forEach { comment ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    UrsText(comment.text, style = UrsTheme.typography.body)
                    UrsText(comment.date, style = UrsTheme.typography.caption, color = UrsTheme.colors.onSurfaceMuted)
                }
                UrsIconButton(
                    onClick = { viewModel.deleteComment(comment) },
                    contentDescription = stringResource(R.string.assets_comment_delete),
                    imageVector = Icons.Filled.Close,
                )
            }
        }
        UrsTextField(
            value = form.newCommentInput.text,
            onValueChange = viewModel::setNewCommentText,
            label = stringResource(R.string.assets_comment_add),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsDateField(
            value = form.newCommentInput.date,
            onValueChange = viewModel::setNewCommentDate,
            label = stringResource(R.string.assets_component_date),
            modifier = Modifier.fillMaxWidth(),
        )
        UrsButton(
            text = stringResource(R.string.assets_comment_add),
            onClick = viewModel::addComment,
            enabled = form.newCommentInput.isValid,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
