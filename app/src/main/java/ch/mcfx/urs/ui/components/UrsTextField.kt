package ch.mcfx.urs.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * Bordered text input — replacement for `material3.OutlinedTextField`, built
 * directly on [BasicTextField] rather than `material3.TextField`.
 *
 * The label is a basic two-state affordance, not Material's full floating-
 * label animation: once there's a value or the field is focused, [label]
 * renders as a small caption above the input; while empty and unfocused, it
 * renders inline as placeholder-style text instead. Correctness of showing
 * the label matters here, not motion polish.
 */
@Composable
fun UrsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    /** Small caption rendered below the field, e.g. a "last known value" hint. */
    supportingText: String? = null,
) {
    val colors = UrsTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val labelAbove = focused || value.isNotEmpty()
    val borderColor = if (focused) colors.accent else colors.onSurfaceMuted.copy(alpha = 0.3f)
    val shape = RoundedCornerShape(Radius.row)

    Column(modifier = modifier) {
        if (labelAbove) {
            UrsText(
                text = label,
                style = UrsTheme.typography.caption,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(start = Spacing.m, bottom = Spacing.xs),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, shape)
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
        ) {
            if (!labelAbove) {
                UrsText(text = label, style = UrsTheme.typography.body, color = colors.onSurfaceMuted)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = singleLine,
                minLines = minLines,
                keyboardOptions = keyboardOptions,
                textStyle = UrsTheme.typography.body.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.accent),
                interactionSource = interactionSource,
            )
        }
        if (supportingText != null) {
            UrsText(
                text = supportingText,
                style = UrsTheme.typography.caption,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(start = Spacing.m, top = Spacing.xs),
            )
        }
    }
}
