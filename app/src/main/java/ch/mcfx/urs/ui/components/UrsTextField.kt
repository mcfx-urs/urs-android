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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.ui.theme.UrsColors
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * Bordered text input — replacement for `material3.OutlinedTextField`, built
 * directly on [BasicTextField] rather than `material3.TextField`.
 *
 * [label] always renders as a small caption above the field, regardless of
 * focus or value — deliberately not Material's floating-label animation,
 * which shifts the label between an inline and an above-field position and
 * changes the field's height when it does.
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
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    /** Lets a caller programmatically move focus onto this field, e.g. a newly added form row. */
    focusRequester: FocusRequester? = null,
    /** Small caption rendered below the field, e.g. a "last known value" hint. */
    supportingText: String? = null,
    /** E.g. [androidx.compose.ui.text.input.PasswordVisualTransformation] to mask a password field's input. */
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val interactionSource = remember { MutableInteractionSource() }
    UrsTextFieldChrome(
        label = label,
        modifier = modifier,
        supportingText = supportingText,
        interactionSource = interactionSource,
    ) { colors ->
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            textStyle = UrsTheme.typography.body.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.accent),
            interactionSource = interactionSource,
            visualTransformation = visualTransformation,
        )
    }
}

/**
 * [TextFieldValue] variant of the field above — needed whenever the caller
 * must control cursor/selection explicitly, e.g. a format-as-you-type input.
 * The plain `String` overload keeps whatever selection index the user's
 * keystroke produced and just swaps in the new text, which is wrong once
 * that text has been transformed to a different length (see
 * `ch.mcfx.urs.worktime`'s military-time auto-formatting, which inserts a
 * `:` the user didn't type) — the cursor ends up pointing at the wrong
 * character instead of where the user actually just typed.
 */
@Composable
fun UrsTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
    supportingText: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    UrsTextFieldChrome(
        label = label,
        modifier = modifier,
        supportingText = supportingText,
        interactionSource = interactionSource,
    ) { colors ->
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            textStyle = UrsTheme.typography.body.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.accent),
            interactionSource = interactionSource,
        )
    }
}

// Shared label/border/box chrome for both UrsTextField overloads above —
// they differ only in the value type they hand to BasicTextField. Internal
// (not private) so other bespoke fields built directly on BasicTextField,
// e.g. the notes rich-text editor, can reuse the same visual chrome instead
// of duplicating it.
@Composable
internal fun UrsTextFieldChrome(
    label: String,
    modifier: Modifier,
    supportingText: String?,
    interactionSource: MutableInteractionSource,
    field: @Composable (UrsColors) -> Unit,
) {
    val colors = UrsTheme.colors
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (focused) colors.accent else colors.onSurfaceMuted.copy(alpha = 0.3f)
    val shape = RoundedCornerShape(Radius.row)

    Column(modifier = modifier) {
        UrsText(
            text = label,
            style = UrsTheme.typography.caption,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(start = Spacing.m, bottom = Spacing.xs),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, shape)
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
        ) {
            field(colors)
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
