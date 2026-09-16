package ch.mcfx.urs.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import ch.mcfx.urs.data.local.OutboxStatus
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
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
fun AboutScreen(
    onOpenLocationCaptureLog: () -> Unit,
    onOpenChangelog: () -> Unit,
    viewModel: AboutViewModel = viewModel(factory = AboutViewModel.Factory),
) {
    val colors = UrsTheme.colors
    val version = BuildConfig.VERSION_NAME.substringBefore("-")
    val buildType = BuildConfig.BUILD_TYPE.replaceFirstChar { it.uppercase() }

    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val backendState by viewModel.backendState.collectAsStateWithLifecycle()
    val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()
    val outboxEntries by viewModel.outboxEntries.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val locationCaptureStatus by viewModel.locationCaptureStatus.collectAsStateWithLifecycle()

    var showSyncDetails by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    SyncStateRow(syncState, onClick = { showSyncDetails = true })
                    BackendStateRow(backendState, onRecheck = viewModel::recheckBackend)
                    VpnStateRow(vpnState)
                    LocationCaptureStateRow(locationCaptureStatus, onClick = onOpenLocationCaptureLog)
                }
            }

            UrsCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.m).clickable(onClick = onOpenChangelog)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    UrsText(stringResource(R.string.about_changelog_row), style = UrsTheme.typography.body, color = colors.accent)
                }
            }
        }

        if (showSyncDetails) {
            UrsBottomSheet(onDismissRequest = { showSyncDetails = false }) {
                SyncDetailSheet(
                    entries = outboxEntries,
                    isSyncing = isSyncing,
                    onSyncNow = viewModel::syncNow,
                )
            }
        }
    }
}

@Composable
private fun SyncStateRow(state: AboutSyncState, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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
private fun SyncDetailSheet(
    entries: List<OutboxEntryUi>,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.about_sync_details_title), style = UrsTheme.typography.cardTitle)

        UrsButton(
            text = stringResource(if (isSyncing) R.string.about_sync_syncing else R.string.about_sync_now),
            onClick = onSyncNow,
            enabled = !isSyncing,
            modifier = Modifier.fillMaxWidth(),
        )

        if (entries.isEmpty()) {
            UrsText(
                stringResource(R.string.about_sync_nothing_queued),
                style = UrsTheme.typography.body,
                color = UrsTheme.colors.onSurfaceMuted,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                entries.forEach { entry -> OutboxEntryRow(entry) }
            }
        }
    }
}

@Composable
private fun OutboxEntryRow(entry: OutboxEntryUi) {
    val failed = entry.status == OutboxStatus.FAILED
    UrsCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrsText(
                    text = stringResource(R.string.about_sync_entry_label, stringResource(entry.verbRes), stringResource(entry.domainRes)),
                    style = UrsTheme.typography.body,
                )
                if (failed) {
                    UrsPill(
                        text = stringResource(R.string.about_sync_status_failed),
                        containerColor = FormErrorColor.copy(alpha = 0.15f),
                        contentColor = FormErrorColor,
                    )
                } else {
                    UrsPill(text = stringResource(R.string.about_sync_status_pending))
                }
            }
            UrsText(
                text = stringResource(R.string.about_sync_entry_queued, formatEpochMillis(entry.createdAt)),
                style = UrsTheme.typography.caption,
                color = UrsTheme.colors.onSurfaceMuted,
            )
            if (failed) {
                UrsText(
                    text = stringResource(R.string.about_sync_entry_attempts, entry.retryCount),
                    style = UrsTheme.typography.caption,
                    color = UrsTheme.colors.onSurfaceMuted,
                )
                entry.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                    UrsText(text = error, style = UrsTheme.typography.caption, color = FormErrorColor)
                }
            }
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

@Composable
private fun LocationCaptureStateRow(status: LocationCaptureStatus, onClick: () -> Unit) {
    val colors = UrsTheme.colors
    val (labelRes, color) = when (status) {
        LocationCaptureStatus.DISABLED -> R.string.about_location_capture_status_disabled to colors.onSurfaceMuted
        LocationCaptureStatus.PAUSED -> R.string.about_location_capture_status_paused to colors.onSurfaceMuted
        LocationCaptureStatus.DENSE -> R.string.about_location_capture_status_dense to colors.accent
        LocationCaptureStatus.SPARSE -> R.string.about_location_capture_status_sparse to colors.accent
        LocationCaptureStatus.FIXED -> R.string.about_location_capture_status_fixed to colors.accent
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrsText(stringResource(R.string.about_state_location_capture), style = UrsTheme.typography.body)
        UrsPill(text = stringResource(labelRes), containerColor = color.copy(alpha = 0.15f), contentColor = color)
    }
}

