package ch.mcfx.urs.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * Centered modal shown when leaving a form/sheet with unsaved edits (back
 * press, Home icon, scrim tap) — same centered-overlay treatment as
 * [UrsErrorDialog], the app's only other non-bottom-sheet popup.
 */
@Composable
fun UrsDiscardChangesDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit, modifier: Modifier = Modifier) {
    Dialog(onDismissRequest = onKeepEditing) {
        UrsCard(modifier = modifier.fillMaxWidth()) {
            UrsText(text = stringResource(R.string.discard_changes_title), style = UrsTheme.typography.cardTitle)
            Spacer(modifier = Modifier.height(Spacing.s))
            UrsText(text = stringResource(R.string.discard_changes_message), style = UrsTheme.typography.body)
            Spacer(modifier = Modifier.height(Spacing.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                UrsOutlinedButton(
                    text = stringResource(R.string.keep_editing),
                    onClick = onKeepEditing,
                    modifier = Modifier.weight(1f),
                )
                UrsButton(
                    text = stringResource(R.string.discard),
                    onClick = onDiscard,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
