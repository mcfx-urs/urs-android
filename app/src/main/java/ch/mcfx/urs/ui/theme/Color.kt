package ch.mcfx.urs.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Full color set for one Begleiter theme variant (light or dark). Mirrors
 * the roles `androidx.compose.material3.ColorScheme` used to provide, kept
 * intentionally small since this design only needs a handful of roles.
 *
 * Values are taken verbatim from the finalized mockup's CSS custom
 * properties (`.bg-light`/`.bg-dark` in the "urs — Begleiter, finalisiert"
 * artifact), not eyeballed off a screenshot — that source has exact hex
 * codes, a screenshot only has lossy rendered pixels.
 */
data class UrsColors(
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val shadowColor: Color,
    val shadowAlpha: Float,
    val border: Color,
    /** Whole-card opacity for a "coming soon" tile, plus its blended (not plain surface) background. */
    val disabledAlpha: Float,
)

val LightUrsColors = UrsColors(
    background = Color(0xFFFBF8F3),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2B2420),
    onSurfaceMuted = Color(0xFF8A7C6C),
    accent = Color(0xFF6EA23A),
    onAccent = Color(0xFFFFFFFF),
    shadowColor = Color(0xFF2B2420),
    shadowAlpha = 0.18f,
    border = Color.Transparent,
    disabledAlpha = 0.6f,
)

val DarkUrsColors = UrsColors(
    background = Color(0xFF12160F),
    surface = Color(0xFF1B211A),
    onSurface = Color(0xFFE9EFE6),
    onSurfaceMuted = Color(0xFF8FA08F),
    accent = Color(0xFF8BD600),
    onAccent = Color(0xFF12160F),
    shadowColor = Color.Black,
    shadowAlpha = 0.6f,
    border = Color(0xFF8BD600).copy(alpha = 0.14f),
    disabledAlpha = 0.6f,
)
