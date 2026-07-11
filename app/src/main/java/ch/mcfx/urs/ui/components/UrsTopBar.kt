package ch.mcfx.urs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * Fixed-height app bar — replacement for `material3.TopAppBar`. Same
 * two-slot shape (`navigationIcon` + `title`) so call sites barely change;
 * background matches the screen behind it rather than a separate app-bar
 * surface color.
 *
 * `material3.Scaffold`/`TopAppBar` handled the status-bar inset
 * automatically; a plain `Row` doesn't, so it's applied explicitly here —
 * background is painted first (outermost) so it extends behind the status
 * bar, and the inset padding pushes the actual bar content (icon/title)
 * below it instead of behind the system status bar icons.
 */
@Composable
fun UrsTopBar(
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    title: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(UrsTheme.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(64.dp)
            .padding(horizontal = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        navigationIcon()
        title()
    }
}
