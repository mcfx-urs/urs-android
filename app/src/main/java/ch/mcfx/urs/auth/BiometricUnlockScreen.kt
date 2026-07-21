package ch.mcfx.urs.auth

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * Shown by [ch.mcfx.urs.navigation.AppNavigation] whenever
 * [BiometricGate.needsUnlock] is true — a real session already exists
 * ([AuthTokenStore.isLoggedIn] is true), this is purely the local
 * re-entry gate on top of it. Immediately fires a [BiometricPrompt] on
 * first composition; [onUsePasswordInstead] (also the automatic path if
 * the device's biometric enrollment changed, see [BiometricGate]'s doc
 * comment) just logs the real session out, which routes back to
 * [LoginScreen] the normal way — no separate "confirm with password"
 * flow to maintain.
 */
@Composable
fun BiometricUnlockScreen(gate: BiometricGate, onUsePasswordInstead: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    var failed by remember { mutableStateOf(false) }
    val onUsePasswordInsteadState = rememberUpdatedState(onUsePasswordInstead)

    fun prompt() {
        failed = false
        val cipher = gate.prepareCipher()
        if (cipher == null) {
            // Biometric enrollment changed since this key was created — the
            // user's own stated requirement: this is the one case a real
            // password re-login is required again. Also disables the gate
            // (not just logs out): the old key is permanently dead, so
            // leaving it "enabled" would just re-trigger this exact branch
            // again right after the fresh login. Re-enabling is then a
            // deliberate step in Settings, which creates a fresh valid key.
            gate.disable()
            onUsePasswordInsteadState.value()
            return
        }

        val biometricPrompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    result.cryptoObject?.cipher?.let(gate::confirmUnlock)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Includes the user tapping the negative button, cancelling,
                    // or too many failed attempts — never treated as an
                    // enrollment change (that's the cipher == null path above).
                    failed = true
                }

                override fun onAuthenticationFailed() {
                    // A single wrong fingerprint/face — the prompt itself stays
                    // open for another attempt, nothing to do here.
                }
            },
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.biometric_prompt_title))
            .setNegativeButtonText(activity.getString(R.string.biometric_prompt_use_password))
            .build()
        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    LaunchedEffect(Unit) { prompt() }

    Column(
        modifier = Modifier.fillMaxSize().background(UrsTheme.colors.background).padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.urs_bear_logo),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
        )
        UrsText(
            stringResource(R.string.biometric_unlock_title),
            style = UrsTheme.typography.brand,
            modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.xl),
        )
        if (failed) {
            UrsButton(
                text = stringResource(R.string.biometric_unlock_retry),
                onClick = ::prompt,
                modifier = Modifier.fillMaxWidth(),
            )
            UrsOutlinedButton(
                text = stringResource(R.string.biometric_prompt_use_password),
                onClick = onUsePasswordInstead,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
            )
        }
    }
}
