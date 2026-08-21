package ch.mcfx.urs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.components.ursFormScrollPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

// Same reasoning as other screens' local text-style/color constants — the
// design system's type scale doesn't have an "error" role in its palette yet.
private val FormErrorColor = Color(0xFFD64545)

@Composable
fun WageRulesSettingsScreen(viewModel: WageRulesViewModel = viewModel(factory = WageRulesViewModel.Factory)) {
    val vacationPay by viewModel.vacationPaySurchargePercent.collectAsStateWithLifecycle()
    val holiday by viewModel.holidaySurchargePercent.collectAsStateWithLifecycle()
    val thirteenthMonth by viewModel.thirteenthMonthSurchargePercent.collectAsStateWithLifecycle()
    val ahvIvEo by viewModel.ahvIvEoDeductionPercent.collectAsStateWithLifecycle()
    val alv by viewModel.alvDeductionPercent.collectAsStateWithLifecycle()
    val suvaNbu by viewModel.suvaNbuDeductionPercent.collectAsStateWithLifecycle()
    val ktg by viewModel.ktgDeductionPercent.collectAsStateWithLifecycle()
    val bvg by viewModel.bvgDeductionAmount.collectAsStateWithLifecycle()
    val justSaved by viewModel.justSaved.collectAsStateWithLifecycle()
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()

    val decimalKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .ursFormScrollPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.worktime_wage_rules_description), style = UrsTheme.typography.body, color = UrsTheme.colors.onSurfaceMuted)

        UrsTextField(
            value = vacationPay,
            onValueChange = viewModel::setVacationPaySurchargePercent,
            label = stringResource(R.string.worktime_wage_rules_vacation_pay_label),
            keyboardOptions = decimalKeyboard,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsTextField(
            value = holiday,
            onValueChange = viewModel::setHolidaySurchargePercent,
            label = stringResource(R.string.worktime_wage_rules_holiday_label),
            keyboardOptions = decimalKeyboard,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsTextField(
            value = thirteenthMonth,
            onValueChange = viewModel::setThirteenthMonthSurchargePercent,
            label = stringResource(R.string.worktime_wage_rules_thirteenth_month_label),
            keyboardOptions = decimalKeyboard,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsTextField(
            value = ahvIvEo,
            onValueChange = viewModel::setAhvIvEoDeductionPercent,
            label = stringResource(R.string.worktime_wage_rules_ahv_iv_eo_label),
            keyboardOptions = decimalKeyboard,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsTextField(
            value = alv,
            onValueChange = viewModel::setAlvDeductionPercent,
            label = stringResource(R.string.worktime_wage_rules_alv_label),
            keyboardOptions = decimalKeyboard,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsTextField(
            value = suvaNbu,
            onValueChange = viewModel::setSuvaNbuDeductionPercent,
            label = stringResource(R.string.worktime_wage_rules_suva_nbu_label),
            keyboardOptions = decimalKeyboard,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsTextField(
            value = ktg,
            onValueChange = viewModel::setKtgDeductionPercent,
            label = stringResource(R.string.worktime_wage_rules_ktg_label),
            keyboardOptions = decimalKeyboard,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsTextField(
            value = bvg,
            onValueChange = viewModel::setBvgDeductionAmount,
            label = stringResource(R.string.worktime_wage_rules_bvg_label),
            keyboardOptions = decimalKeyboard,
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
        if (saveFailed) {
            UrsText(stringResource(R.string.error_save), color = FormErrorColor)
        }
    }
}
