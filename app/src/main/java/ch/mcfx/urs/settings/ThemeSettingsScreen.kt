package ch.mcfx.urs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.ThemePreference
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

private val THEME_OPTIONS = listOf(ThemePreference.SYSTEM, ThemePreference.LIGHT, ThemePreference.DARK)

@Composable
fun ThemeSettingsScreen(
    viewModel: ThemeSettingsViewModel = viewModel(factory = ThemeSettingsViewModel.Factory),
) {
    val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
    val colors = UrsTheme.colors

    val themeLabels = mapOf(
        ThemePreference.SYSTEM to stringResource(R.string.theme_option_system),
        ThemePreference.LIGHT to stringResource(R.string.theme_option_light),
        ThemePreference.DARK to stringResource(R.string.theme_option_dark),
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        UrsText(stringResource(R.string.theme_settings_title), style = UrsTheme.typography.screenTitle)
        UrsText(
            stringResource(R.string.theme_settings_description),
            style = UrsTheme.typography.caption,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(bottom = Spacing.s),
        )

        UrsDropdownField(
            label = stringResource(R.string.theme_settings_label),
            options = THEME_OPTIONS,
            selectedLabel = themeLabels[themePreference],
            optionLabel = { themeLabels[it] ?: it.name },
            onSelect = viewModel::setThemePreference,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
