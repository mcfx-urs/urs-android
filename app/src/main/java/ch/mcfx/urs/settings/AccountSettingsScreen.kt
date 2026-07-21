package ch.mcfx.urs.settings

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCheckbox
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * Logging out here just calls [AccountSettingsViewModel.logout], which
 * clears [ch.mcfx.urs.auth.AuthTokenStore] — the actual navigation back to
 * [ch.mcfx.urs.auth.LoginScreen] happens automatically wherever
 * `AppNavigation` observes `isLoggedIn` flipping to `false`, not from
 * anything on this screen.
 *
 * The biometric toggle is opt-in (the user's own wording, 2026-07-17:
 * "wenn ich als User Biometrie aktiviere") — off by default, only shown at
 * all if [BiometricManager] reports strong biometric hardware is actually
 * enrolled. Turning it on immediately runs a real [BiometricPrompt], both
 * to create the gating Keystore key and to prove it works right now,
 * rather than just flipping a flag and finding out at the next app open.
 *
 * Also hosts the former standalone Work Settings screen (employment %,
 * target hours, hourly wage) as a second section below the account info —
 * merged in as part of the Settings restructure (About/Account/General).
 */
@Composable
fun AccountSettingsScreen(
    viewModel: AccountSettingsViewModel = viewModel(factory = AccountSettingsViewModel.Factory),
    workTimeViewModel: WorkTimeSettingsViewModel = viewModel(factory = WorkTimeSettingsViewModel.Factory),
) {
    val context = LocalContext.current
    val gate = viewModel.biometricGate

    val canOfferBiometric = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
    var biometricEnabled by remember { mutableStateOf(gate.isEnabled) }
    // Resolved here, not inside the onCheckedChange lambda below — stringResource
    // is @Composable-only, that lambda isn't.
    val biometricPromptTitle = stringResource(R.string.biometric_prompt_title)
    val cancelLabel = stringResource(R.string.cancel)

    val targetHours by workTimeViewModel.targetHours.collectAsStateWithLifecycle()
    val employmentPercent by workTimeViewModel.employmentPercent.collectAsStateWithLifecycle()
    val hourlyWage by workTimeViewModel.hourlyWage.collectAsStateWithLifecycle()
    val justSaved by workTimeViewModel.justSaved.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        UrsText(
            stringResource(R.string.account_logged_in_as, viewModel.userName ?: ""),
            style = UrsTheme.typography.body,
        )

        if (canOfferBiometric) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrsText(stringResource(R.string.account_biometric_unlock), style = UrsTheme.typography.body)
                UrsCheckbox(
                    checked = biometricEnabled,
                    onCheckedChange = { checked ->
                        if (!checked) {
                            gate.disable()
                            biometricEnabled = false
                        } else {
                            val cipher = gate.prepareCipher()
                            if (cipher != null) {
                                val activity = context as FragmentActivity
                                BiometricPrompt(
                                    activity,
                                    ContextCompat.getMainExecutor(activity),
                                    object : BiometricPrompt.AuthenticationCallback() {
                                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                            result.cryptoObject?.cipher?.let(gate::confirmUnlock)
                                            gate.enable()
                                            biometricEnabled = true
                                        }
                                        // onAuthenticationError/onAuthenticationFailed: leave
                                        // biometricEnabled at its current (false) value — the
                                        // checkbox just stays unchecked, no separate error UI.
                                    },
                                ).authenticate(
                                    BiometricPrompt.PromptInfo.Builder()
                                        .setTitle(biometricPromptTitle)
                                        .setNegativeButtonText(cancelLabel)
                                        .build(),
                                    BiometricPrompt.CryptoObject(cipher),
                                )
                            }
                        }
                    },
                )
            }
        }

        UrsText(stringResource(R.string.settings_tile_work_time), style = UrsTheme.typography.cardTitle)

        UrsTextField(
            value = employmentPercent,
            onValueChange = workTimeViewModel::setEmploymentPercent,
            label = stringResource(R.string.worktime_settings_employment_percent_label),
            supportingText = stringResource(R.string.worktime_settings_employment_percent_hint),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        UrsTextField(
            value = targetHours,
            onValueChange = workTimeViewModel::setTargetHours,
            label = stringResource(R.string.worktime_settings_target_hours_label),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        UrsTextField(
            value = hourlyWage,
            onValueChange = workTimeViewModel::setHourlyWage,
            label = stringResource(R.string.worktime_settings_hourly_wage_label),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        UrsButton(
            text = stringResource(R.string.save),
            onClick = workTimeViewModel::save,
            modifier = Modifier.fillMaxWidth(),
        )

        if (justSaved) {
            UrsText(stringResource(R.string.worktime_settings_saved), color = UrsTheme.colors.onSurfaceMuted)
        }

        UrsOutlinedButton(
            text = stringResource(R.string.account_log_out),
            onClick = viewModel::logout,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
