package ch.mcfx.urs.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalUrsColors = staticCompositionLocalOf { LightUrsColors }
private val LocalUrsTypography = staticCompositionLocalOf { DefaultUrsTypography }

/**
 * Begleiter design-system theme. Descendant composables read the
 * active palette/type scale via [UrsTheme.colors] and [UrsTheme.typography].
 * No Material 3 dependency — every screen in the app now uses this
 * component set directly (the temporary `MaterialTheme` bridge that used to
 * live here was removed once the last screen finished migrating).
 */
@Composable
fun UrsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkUrsColors else LightUrsColors

    CompositionLocalProvider(
        LocalUrsColors provides colors,
        LocalUrsTypography provides DefaultUrsTypography,
        content = content,
    )
}

object UrsTheme {
    val colors: UrsColors
        @Composable
        get() = LocalUrsColors.current

    val typography: UrsTypography
        @Composable
        get() = LocalUrsTypography.current
}

/** User-facing override for [UrsTheme]'s `darkTheme` param — see [ThemePreference.resolveDarkTheme]. */
enum class ThemePreference { SYSTEM, LIGHT, DARK }

@Composable
fun ThemePreference.resolveDarkTheme(): Boolean = when (this) {
    ThemePreference.SYSTEM -> isSystemInDarkTheme()
    ThemePreference.LIGHT -> false
    ThemePreference.DARK -> true
}
