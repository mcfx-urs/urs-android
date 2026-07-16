package ch.mcfx.urs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * Logging out here just calls [AccountSettingsViewModel.logout], which
 * clears [ch.mcfx.urs.auth.AuthTokenStore] — the actual navigation back to
 * [ch.mcfx.urs.auth.LoginScreen] happens automatically wherever
 * `AppNavigation` observes `isLoggedIn` flipping to `false`, not from
 * anything on this screen.
 */
@Composable
fun AccountSettingsScreen(viewModel: AccountSettingsViewModel = viewModel(factory = AccountSettingsViewModel.Factory)) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        UrsText(
            stringResource(R.string.account_logged_in_as, viewModel.userName ?: ""),
            style = UrsTheme.typography.body,
        )
        UrsOutlinedButton(
            text = stringResource(R.string.account_log_out),
            onClick = viewModel::logout,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
