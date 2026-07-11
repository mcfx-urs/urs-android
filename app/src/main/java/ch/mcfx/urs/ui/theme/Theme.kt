package ch.mcfx.urs.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalUrsColors = staticCompositionLocalOf { LightUrsColors }
private val LocalUrsTypography = staticCompositionLocalOf { DefaultUrsTypography }

/**
 * Begleiter design-system theme. Descendant composables read the active
 * palette/type scale via [UrsTheme.colors] and [UrsTheme.typography].
 *
 * Migration-only bridge: this still wraps content in an `androidx.compose
 * .material3.MaterialTheme`, mapped from the same [UrsColors] instance,
 * purely so screens not yet migrated off Material 3 keep following
 * light/dark instead of being frozen on M3's static default light scheme.
 * Remove this bridge — and the `material3` import along with it — once
 * every screen has moved onto the new component set.
 */
@Composable
fun UrsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkUrsColors else LightUrsColors
    val bridgeColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.background,
            onBackground = colors.onSurface,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surface,
            onSurfaceVariant = colors.onSurfaceMuted,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.background,
            onBackground = colors.onSurface,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surface,
            onSurfaceVariant = colors.onSurfaceMuted,
        )
    }

    CompositionLocalProvider(
        LocalUrsColors provides colors,
        LocalUrsTypography provides DefaultUrsTypography,
    ) {
        MaterialTheme(colorScheme = bridgeColorScheme, content = content)
    }
}

object UrsTheme {
    val colors: UrsColors
        @Composable
        get() = LocalUrsColors.current

    val typography: UrsTypography
        @Composable
        get() = LocalUrsTypography.current
}
