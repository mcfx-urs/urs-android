package ch.mcfx.urs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

// Same reasoning as other screens' local text-style/color constants — the
// design system's type scale doesn't have an "error" role in its palette yet.
private val FormErrorColor = Color(0xFFD64545)

// Only reachable from Settings when AuthTokenStore.isSuperUser is true (see
// SettingsScreen) — the backend itself also rejects the restart
// call with 403 for anyone else, this screen is just the UI on top.
@Composable
fun AdminScreen(viewModel: AdminViewModel = viewModel(factory = AdminViewModel.Factory)) {
    val confirmingRestart by viewModel.confirmingRestart.collectAsStateWithLifecycle()
    val restarting by viewModel.restarting.collectAsStateWithLifecycle()
    val restartRequested by viewModel.restartRequested.collectAsStateWithLifecycle()
    val restartFailed by viewModel.restartFailed.collectAsStateWithLifecycle()
    val serverBackUp by viewModel.serverBackUp.collectAsStateWithLifecycle()
    val checkTimedOut by viewModel.checkTimedOut.collectAsStateWithLifecycle()
    val currentLogLevel by viewModel.currentLogLevel.collectAsStateWithLifecycle()
    val logLevelUpdateFailed by viewModel.logLevelUpdateFailed.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        if (confirmingRestart) {
            UrsText(stringResource(R.string.admin_restart_confirm_title), style = UrsTheme.typography.cardTitle)
            UrsText(stringResource(R.string.admin_restart_confirm_body), style = UrsTheme.typography.body)
            UrsButton(
                text = stringResource(R.string.admin_restart_server),
                onClick = viewModel::confirmRestart,
                modifier = Modifier.fillMaxWidth(),
            )
            UrsOutlinedButton(
                text = stringResource(R.string.cancel),
                onClick = viewModel::cancelRestart,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            UrsButton(
                text = stringResource(R.string.admin_restart_server),
                onClick = viewModel::requestRestart,
                enabled = !restarting,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (restarting) {
            UrsText(stringResource(R.string.admin_restart_in_progress), color = UrsTheme.colors.onSurfaceMuted)
        }
        if (restartRequested) {
            when {
                serverBackUp -> UrsText(stringResource(R.string.admin_restart_back_up), color = UrsTheme.colors.onSurfaceMuted)
                checkTimedOut -> UrsText(stringResource(R.string.admin_restart_check_timeout), color = FormErrorColor)
                else -> UrsText(stringResource(R.string.admin_restart_requested), color = UrsTheme.colors.onSurfaceMuted)
            }
        }
        if (restartFailed) {
            UrsText(stringResource(R.string.admin_restart_failed), color = FormErrorColor)
        }

        UrsDropdownField(
            label = stringResource(R.string.admin_log_level_label),
            options = LogLevels,
            selectedLabel = currentLogLevel,
            optionLabel = { it },
            onSelect = viewModel::setLogLevel,
            modifier = Modifier.fillMaxWidth(),
        )
        if (logLevelUpdateFailed) {
            UrsText(stringResource(R.string.admin_log_level_failed), color = FormErrorColor)
        }
    }
}

// The backend's accepted slog levels, low to high verbosity.
private val LogLevels = listOf("debug", "info", "warn", "error")
