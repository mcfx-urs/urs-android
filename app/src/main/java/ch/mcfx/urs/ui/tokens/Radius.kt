package ch.mcfx.urs.ui.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Shared corner-radius scale for the Begleiter design system, matching
 * the finalized mockup's CSS exactly (`.card { border-radius:
 * 20px }`, `.row { border-radius: 14px }` — there is only one grid-card
 * radius, not a separate bigger one for the featured card).
 */
object Radius {
    val card = 20.dp
    val row = 14.dp
    val pill = RoundedCornerShape(50)
}
