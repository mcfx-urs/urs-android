package ch.mcfx.urs.notes.richtext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsDiscardChangesDialog
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * Popup for inserting a new link or editing/removing an existing one — same
 * [UrsBottomSheet]-based small-form pattern as `NoteDetailScreen`'s delete
 * confirmation sheet.
 */
@Composable
internal fun LinkEditSheet(
    initialText: String,
    initialUrl: String,
    isExisting: Boolean,
    onSave: (text: String, url: String) -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var confirmingDiscard by remember { mutableStateOf(false) }
    val textFocusRequester = remember { FocusRequester() }
    val urlFocusRequester = remember { FocusRequester() }

    // Auto-focus the field that still needs input, so the keyboard targets it
    // immediately instead of staying attached to whatever was focused behind
    // the sheet (the content field, now hidden underneath it) until the user
    // manually taps into the sheet themselves.
    LaunchedEffect(Unit) {
        if (initialText.isBlank()) textFocusRequester.requestFocus() else urlFocusRequester.requestFocus()
    }

    val requestDismiss = { if (text != initialText || url != initialUrl) confirmingDiscard = true else onDismiss() }

    UrsBottomSheet(onDismissRequest = requestDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            UrsText(
                stringResource(if (isExisting) R.string.note_richtext_link_edit_title else R.string.note_richtext_link_insert_title),
                style = UrsTheme.typography.cardTitle,
            )
            UrsTextField(
                value = text,
                onValueChange = { text = it },
                label = stringResource(R.string.note_richtext_link_text_label),
                singleLine = true,
                focusRequester = textFocusRequester,
                modifier = Modifier.fillMaxWidth(),
            )
            UrsTextField(
                value = url,
                onValueChange = { url = it },
                label = stringResource(R.string.note_richtext_link_url_label),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                focusRequester = urlFocusRequester,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isExisting) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    UrsOutlinedButton(
                        text = stringResource(R.string.note_richtext_link_open),
                        onClick = onOpen,
                        modifier = Modifier.weight(1f),
                    )
                    UrsOutlinedButton(
                        text = stringResource(R.string.note_richtext_link_remove),
                        onClick = onRemove,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                UrsOutlinedButton(text = stringResource(R.string.cancel), onClick = requestDismiss, modifier = Modifier.weight(1f))
                UrsButton(
                    text = stringResource(R.string.note_richtext_link_save),
                    onClick = { onSave(text.trim(), url.trim()) },
                    enabled = text.isNotBlank() && url.isNotBlank(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (confirmingDiscard) {
        UrsDiscardChangesDialog(
            onDiscard = {
                confirmingDiscard = false
                onDismiss()
            },
            onKeepEditing = { confirmingDiscard = false },
        )
    }
}
