package ch.mcfx.urs.data

import java.math.BigDecimal

/** Units the backend accepts for a clone's size. */
val SIZE_UNITS = listOf("g", "kg", "ml", "l", "pcs")

const val DEFAULT_SIZE_UNIT = "g"

/**
 * Splits a quantity text as Open Food Facts delivers it ("1 l", "250g",
 * "1,5 kg", "33 cl") into an amount and one of [SIZE_UNITS]. cl and dl are
 * converted to ml. Returns null for anything else (e.g. "6 x 33 cl"), which
 * the user then enters by hand.
 */
fun parseQuantity(text: String): Pair<String, String>? {
    val match = QUANTITY_PATTERN.matchEntire(text.trim()) ?: return null
    val amount = match.groupValues[1].replace(',', '.').toBigDecimalOrNull() ?: return null
    val (factor, unit) = when (match.groupValues[2].lowercase()) {
        "g" -> BigDecimal.ONE to "g"
        "kg" -> BigDecimal.ONE to "kg"
        "ml" -> BigDecimal.ONE to "ml"
        "cl" -> BigDecimal.TEN to "ml"
        "dl" -> BigDecimal(100) to "ml"
        "l", "lt" -> BigDecimal.ONE to "l"
        "pc", "pcs", "stk", "stück", "stueck", "piece", "pieces" -> BigDecimal.ONE to "pcs"
        else -> return null
    }
    val value = amount.multiply(factor)
    if (value.signum() <= 0) return null
    return value.stripTrailingZeros().toPlainString() to unit
}

private val QUANTITY_PATTERN = Regex("""(\d+(?:[.,]\d+)?)\s*([a-zA-Zäöü]+)""")
