package ch.mcfx.urs.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

// No "error" role in the design system's palette yet (see Color.kt) — same
// local-constant pattern already used in FuelStationsScreen/VpnSettingsScreen.
private val FormErrorColor = Color(0xFFD64545)

/**
 * Shown instead of the whole app (drawer + nav host) whenever
 * [AuthTokenStore.isLoggedIn] is false — see [ch.mcfx.urs.navigation.AppNavigation],
 * which owns that branch. Not part of the NavHost's own route graph, so
 * there's no back-stack entry to accidentally return to after logging in.
 */
@Composable
fun LoginScreen(viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory)) {
    val form by viewModel.formState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val passwordFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xl),
        ) {
            Image(
                painter = painterResource(R.drawable.urs_bear_logo),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
            UrsText(
                stringResource(R.string.app_name),
                style = UrsTheme.typography.brand,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }

        UrsTextField(
            value = form.userName,
            onValueChange = viewModel::setUserName,
            label = stringResource(R.string.login_username),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { passwordFocusRequester.requestFocus() }),
            modifier = Modifier.fillMaxWidth(),
        )

        UrsTextField(
            value = form.password,
            onValueChange = viewModel::setPassword,
            label = stringResource(R.string.login_password),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    viewModel.submit()
                },
            ),
            visualTransformation = PasswordVisualTransformation(),
            focusRequester = passwordFocusRequester,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
        )

        if (form.submitFailed) {
            UrsText(
                stringResource(R.string.login_error),
                color = FormErrorColor,
                style = UrsTheme.typography.body,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }

        UrsButton(
            text = stringResource(if (form.submitting) R.string.login_submitting else R.string.login_submit),
            onClick = viewModel::submit,
            enabled = form.isValid && !form.submitting,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.l),
        )
    }
}
