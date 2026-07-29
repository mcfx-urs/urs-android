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
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

private val LANGUAGE_OPTIONS = listOf(AppLanguage.ENGLISH, AppLanguage.SCHWIIZERDUTSCH)

@Composable
fun LanguageSettingsScreen(
    viewModel: LanguageSettingsViewModel = viewModel(factory = LanguageSettingsViewModel.Factory),
) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val colors = UrsTheme.colors

    val languageLabels = mapOf(
        AppLanguage.ENGLISH to stringResource(R.string.language_option_english),
        AppLanguage.SCHWIIZERDUTSCH to stringResource(R.string.language_option_schwiizerdutsch),
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        UrsText(stringResource(R.string.language_settings_title), style = UrsTheme.typography.screenTitle)
        UrsText(
            stringResource(R.string.language_settings_description),
            style = UrsTheme.typography.caption,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(bottom = Spacing.s),
        )

        UrsDropdownField(
            label = stringResource(R.string.language_settings_label),
            options = LANGUAGE_OPTIONS,
            selectedLabel = languageLabels[language],
            optionLabel = { languageLabels[it] ?: it.name },
            onSelect = viewModel::setLanguage,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
