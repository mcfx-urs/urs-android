package ch.mcfx.urs.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.BuildConfig
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import ch.mcfx.urs.vpn.VpnConnectionState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Same "no error role in the design system yet" workaround as
// ServiceScreen.kt/WorkTimeScreen.kt/FuelAddScreen.kt.
private val FormErrorColor = Color(0xFFD64545)

private val TimestampFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
private fun formatEpochMillis(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(TimestampFormat)

@Composable
fun AboutScreen(viewModel: AboutViewModel = viewModel(factory = AboutViewModel.Factory)) {
    val colors = UrsTheme.colors
    val version = BuildConfig.VERSION_NAME.substringBefore("-")
    val buildType = BuildConfig.BUILD_TYPE.replaceFirstChar { it.uppercase() }

    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val backendState by viewModel.backendState.collectAsStateWithLifecycle()
    val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Image(
            painter = painterResource(R.drawable.urs_bear_logo),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
        )
        UrsText(
            stringResource(R.string.app_name),
            style = UrsTheme.typography.brand,
            color = colors.accent,
            modifier = Modifier.padding(top = Spacing.l),
        )
        UrsText(
            stringResource(R.string.about_org),
            style = UrsTheme.typography.body,
            color = colors.accent,
        )
        UrsText(
            stringResource(R.string.about_joke, BuildConfig.JOKE_OF_THE_DAY),
            style = UrsTheme.typography.caption.copy(textAlign = TextAlign.Center),
            color = colors.accent,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.l),
        )
        UrsText(
            stringResource(R.string.about_version_build_type, version, buildType),
            style = UrsTheme.typography.caption,
            color = colors.accent,
            modifier = Modifier.padding(top = Spacing.l),
        )
        UrsText(
            stringResource(R.string.about_build_time, BuildConfig.BUILD_TIME),
            style = UrsTheme.typography.caption,
            color = colors.accent,
        )

        UrsCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                UrsText(
                    stringResource(R.string.about_state_title),
                    style = UrsTheme.typography.cardTitle,
                    color = colors.accent,
                )
                SyncStateRow(syncState)
                BackendStateRow(backendState, onRecheck = viewModel::recheckBackend)
                VpnStateRow(vpnState)
            }
        }
    }
}

@Composable
private fun SyncStateRow(state: AboutSyncState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrsText(stringResource(R.string.about_state_sync), style = UrsTheme.typography.body)
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            when {
                state.failedCount > 0 -> UrsPill(
                    text = stringResource(R.string.about_sync_failed_count, state.failedCount),
                    containerColor = FormErrorColor.copy(alpha = 0.15f),
                    contentColor = FormErrorColor,
                )
                state.pendingCount > 0 -> UrsPill(
                    text = stringResource(R.string.about_sync_pending_count, state.pendingCount),
                )
            }
            UrsText(
                text = state.lastSyncedAt?.let { stringResource(R.string.about_sync_last_synced, formatEpochMillis(it)) }
                    ?: stringResource(R.string.about_sync_never),
                style = UrsTheme.typography.caption,
                color = UrsTheme.colors.onSurfaceMuted,
            )
        }
    }
}

@Composable
private fun BackendStateRow(state: BackendState, onRecheck: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrsText(stringResource(R.string.about_state_backend), style = UrsTheme.typography.body)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            when (state) {
                BackendState.Checking -> UrsPill(
                    text = stringResource(R.string.about_backend_checking),
                    containerColor = UrsTheme.colors.onSurfaceMuted.copy(alpha = 0.15f),
                    contentColor = UrsTheme.colors.onSurfaceMuted,
                )
                BackendState.Reachable -> UrsPill(text = stringResource(R.string.about_backend_reachable))
                BackendState.Unreachable -> UrsPill(
                    text = stringResource(R.string.about_backend_unreachable),
                    containerColor = FormErrorColor.copy(alpha = 0.15f),
                    contentColor = FormErrorColor,
                )
            }
            UrsIconButton(
                onClick = onRecheck,
                contentDescription = stringResource(R.string.about_backend_recheck),
                imageVector = Icons.Filled.Refresh,
                tint = UrsTheme.colors.onSurfaceMuted,
            )
        }
    }
}

@Composable
private fun VpnStateRow(state: VpnConnectionState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrsText(stringResource(R.string.about_state_vpn), style = UrsTheme.typography.body)
        val (labelRes, color) = when (state) {
            VpnConnectionState.DISCONNECTED -> R.string.vpn_status_disconnected to UrsTheme.colors.onSurfaceMuted
            VpnConnectionState.CONNECTING -> R.string.vpn_status_connecting to UrsTheme.colors.onSurfaceMuted
            VpnConnectionState.CONNECTED -> R.string.vpn_status_connected to UrsTheme.colors.accent
            VpnConnectionState.ERROR -> R.string.vpn_status_error to FormErrorColor
        }
        UrsPill(text = stringResource(labelRes), containerColor = color.copy(alpha = 0.15f), contentColor = color)
    }
}
