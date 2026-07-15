package ch.mcfx.urs.worktime

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Auto-inserts the `HH:mm` separator as the user types digits (e.g. "0730"
 * becomes "07:30" while typing) — military-time entry without a picker.
 * Digit-only extraction makes this idempotent whether the user types the
 * colon themselves or not.
 *
 * Takes/returns [TextFieldValue], not a plain `String`: [UrsTextField]'s
 * `String` overload keeps whatever selection *index* the keystroke produced
 * and just swaps in the new text, which points at the wrong character once
 * the text has been transformed to a different length (inserting a `:` the
 * user didn't type) — the cursor lands before the last digit instead of
 * after it, so the next keystroke inserts in the wrong place. Forcing the
 * selection to the end of the formatted text every time sidesteps that.
 */
fun formatTimeInput(input: TextFieldValue): TextFieldValue {
    val digits = input.text.filter { it.isDigit() }.take(4)
    val formatted = if (digits.length <= 2) digits else "${digits.take(2)}:${digits.drop(2)}"
    return TextFieldValue(text = formatted, selection = TextRange(formatted.length))
}

/** Wraps a plain string value for [UrsTextField]'s `TextFieldValue` overload, cursor pinned to the end. */
fun timeFieldValue(text: String): TextFieldValue = TextFieldValue(text = text, selection = TextRange(text.length))

private val TimePattern = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

fun isValidTimeInput(value: String): Boolean = TimePattern.matches(value)
