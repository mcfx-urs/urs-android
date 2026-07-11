package ch.mcfx.urs.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Plain text-style set for the Begleiter design system — deliberately not a
 * Material typography scale, just the handful of styles the mockup uses.
 *
 * Sizes/weights are taken verbatim from the finalized mockup's CSS (the
 * "urs — Begleiter, finalisiert" artifact), not estimated from a
 * screenshot.
 */
data class UrsTypography(
    /** The "urs" wordmark next to the bear logo — `.topbar .brand` (21px/800). */
    val brand: TextStyle,
    /** A screen's own title, e.g. "Notifications" — `.list-mock h3` (16px, bold). */
    val screenTitle: TextStyle,
    /** Card/tile label, e.g. "Fuel" — `.card .label` (14px/700). */
    val cardTitle: TextStyle,
    /** Row label, greeting line — `.lede`/`.row span` (14px/400). */
    val body: TextStyle,
    /** Screen description line — `.list-mock .cap` (13px/400). */
    val caption: TextStyle,
    /** Stat text and status-pill text, always in accent color — `.card .stat`/`.row .status` (12px/700). */
    val statAccent: TextStyle,
    /** "bald"/soon tag — `.card.soon .tag` (9px/600, uppercase, letter-spaced). */
    val tag: TextStyle,
)

val DefaultUrsTypography = UrsTypography(
    brand = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 21.sp,
        fontWeight = FontWeight.ExtraBold,
    ),
    screenTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
    ),
    cardTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
    ),
    body = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
    ),
    caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
    ),
    statAccent = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
    ),
    tag = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
    ),
)
