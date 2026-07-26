package ch.mcfx.urs.ui.components

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
 * Centered modal popup for a single error message plus one acknowledgement
 * action — the app's only *centered* (non-bottom-sheet) overlay, reserved
 * for failures the user must actively dismiss. Generic on purpose: any
 * screen can reuse this for its own error state, not just image
 * generation — callers own the message text and when to show/clear it.
 */
@Composable
fun UrsErrorDialog(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        UrsCard(modifier = modifier.fillMaxWidth()) {
            if (title != null) {
                UrsText(text = title, style = UrsTheme.typography.cardTitle)
                Spacer(modifier = Modifier.height(Spacing.s))
            }
            UrsText(text = message, style = UrsTheme.typography.body)
            Spacer(modifier = Modifier.height(Spacing.l))
            UrsButton(
                text = stringResource(R.string.ok),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
