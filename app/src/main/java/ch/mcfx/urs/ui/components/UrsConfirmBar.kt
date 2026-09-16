package ch.mcfx.urs.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * Self-dismissing confirmation bar shell (message + optional trailing
 * actions), the shared shape behind e.g. shoppinglist's "added to the
 * list" Undo/Edit bar and Kanban's plain save confirmation. A
 * [UrsCard]-style bar rather than a Material3 Snackbar, matching this
 * app's existing precedent over introducing a new component type.
 */
@Composable
fun UrsConfirmBar(
    message: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    UrsCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            UrsText(message, style = UrsTheme.typography.body, modifier = Modifier.weight(1f))
            actions()
        }
    }
}
