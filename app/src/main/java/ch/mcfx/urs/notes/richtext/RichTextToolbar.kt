package ch.mcfx.urs.notes.richtext

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Link
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

// Smaller than UrsIconButton's 48dp/24dp default — a dense row of 8 icons reads better at
// this size, and it's a secondary formatting tool, not a primary action needing the full
// accessibility-minimum touch target.
private val ToolbarIconButtonSize = 36.dp
private val ToolbarIconSize = 18.dp

/**
 * The 8-icon formatting row, always fully shown while the field is focused
 * (no separate collapsed/expanded state). Horizontally scrollable since 8
 * touch targets can still exceed a narrow phone's width even at this
 * reduced size — scrolling rather than shrinking further keeps every
 * target tappable.
 */
@Composable
internal fun RichTextToolbar(
    activeBold: Boolean,
    activeItalic: Boolean,
    activeUnderline: Boolean,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit,
    onLink: () -> Unit,
    onBullet: () -> Unit,
    onNumbered: () -> Unit,
    onIndent: () -> Unit,
    onOutdent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UrsTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        UrsIconButton(
            onClick = onBold,
            contentDescription = stringResource(R.string.note_richtext_bold),
            imageVector = Icons.Filled.FormatBold,
            tint = if (activeBold) colors.accent else colors.onSurface,
            size = ToolbarIconButtonSize,
            iconSize = ToolbarIconSize,
        )
        UrsIconButton(
            onClick = onItalic,
            contentDescription = stringResource(R.string.note_richtext_italic),
            imageVector = Icons.Filled.FormatItalic,
            tint = if (activeItalic) colors.accent else colors.onSurface,
            size = ToolbarIconButtonSize,
            iconSize = ToolbarIconSize,
        )
        UrsIconButton(
            onClick = onUnderline,
            contentDescription = stringResource(R.string.note_richtext_underline),
            imageVector = Icons.Filled.FormatUnderlined,
            tint = if (activeUnderline) colors.accent else colors.onSurface,
            size = ToolbarIconButtonSize,
            iconSize = ToolbarIconSize,
        )
        UrsIconButton(
            onClick = onLink,
            contentDescription = stringResource(R.string.note_richtext_link),
            imageVector = Icons.Filled.Link,
            size = ToolbarIconButtonSize,
            iconSize = ToolbarIconSize,
        )
        UrsIconButton(
            onClick = onBullet,
            contentDescription = stringResource(R.string.note_richtext_bullet_list),
            imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
            size = ToolbarIconButtonSize,
            iconSize = ToolbarIconSize,
        )
        UrsIconButton(
            onClick = onNumbered,
            contentDescription = stringResource(R.string.note_richtext_numbered_list),
            imageVector = Icons.Filled.FormatListNumbered,
            size = ToolbarIconButtonSize,
            iconSize = ToolbarIconSize,
        )
        UrsIconButton(
            onClick = onIndent,
            contentDescription = stringResource(R.string.note_richtext_indent),
            imageVector = Icons.AutoMirrored.Filled.FormatIndentIncrease,
            size = ToolbarIconButtonSize,
            iconSize = ToolbarIconSize,
        )
        UrsIconButton(
            onClick = onOutdent,
            contentDescription = stringResource(R.string.note_richtext_outdent),
            imageVector = Icons.AutoMirrored.Filled.FormatIndentDecrease,
            size = ToolbarIconButtonSize,
            iconSize = ToolbarIconSize,
        )
    }
}
