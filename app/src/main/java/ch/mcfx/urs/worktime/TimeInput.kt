package ch.mcfx.urs.worktime

/**
 * Auto-inserts the `HH:mm` separator as the user types digits (e.g. "0730"
 * becomes "07:30" while typing) — military-time entry without a picker.
 * Digit-only extraction makes this idempotent whether the user types the
 * colon themselves or not, and makes backspace behave naturally, since the
 * separator is always re-derived from whatever digits remain rather than
 * tracked as separate state.
 */
fun formatTimeInput(input: String): String {
    val digits = input.filter { it.isDigit() }.take(4)
    return if (digits.length <= 2) digits else "${digits.take(2)}:${digits.drop(2)}"
}

private val TimePattern = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

fun isValidTimeInput(value: String): Boolean = TimePattern.matches(value)
