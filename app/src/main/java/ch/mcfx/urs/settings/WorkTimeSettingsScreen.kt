package ch.mcfx.urs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

@Composable
fun WorkTimeSettingsScreen(viewModel: WorkTimeSettingsViewModel = viewModel(factory = WorkTimeSettingsViewModel.Factory)) {
    val targetHours by viewModel.targetHours.collectAsStateWithLifecycle()
    val employmentPercent by viewModel.employmentPercent.collectAsStateWithLifecycle()
    val hourlyWage by viewModel.hourlyWage.collectAsStateWithLifecycle()
    val justSaved by viewModel.justSaved.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsTextField(
            value = employmentPercent,
            onValueChange = viewModel::setEmploymentPercent,
            label = stringResource(R.string.worktime_settings_employment_percent_label),
            supportingText = stringResource(R.string.worktime_settings_employment_percent_hint),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        UrsTextField(
            value = targetHours,
            onValueChange = viewModel::setTargetHours,
            label = stringResource(R.string.worktime_settings_target_hours_label),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        UrsTextField(
            value = hourlyWage,
            onValueChange = viewModel::setHourlyWage,
            label = stringResource(R.string.worktime_settings_hourly_wage_label),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        UrsButton(
            text = stringResource(R.string.save),
            onClick = viewModel::save,
            modifier = Modifier.fillMaxWidth(),
        )

        if (justSaved) {
            UrsText(stringResource(R.string.worktime_settings_saved), color = UrsTheme.colors.onSurfaceMuted)
        }
    }
}
