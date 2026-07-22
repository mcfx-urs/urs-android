package ch.mcfx.urs.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsCheckbox
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing
import ch.mcfx.urs.vpn.VpnConnectionState
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

// No "error" role in the design system's palette yet (see Color.kt) — same
// local-constant pattern already used in FuelStationsScreen/ProductListScreen.
private val FormErrorColor = Color(0xFFD64545)

@Composable
fun VpnSettingsScreen(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val configText by viewModel.configText.collectAsStateWithLifecycle()
    val newSsidDraft by viewModel.newSsidDraft.collectAsStateWithLifecycle()
    val homeSsids by viewModel.homeSsids.collectAsStateWithLifecycle()
    val justSaved by viewModel.justSaved.collectAsStateWithLifecycle()
    val tunnelState by viewModel.tunnelState.collectAsStateWithLifecycle()
    val exclusiveModeEnabled by viewModel.exclusiveModeEnabled.collectAsStateWithLifecycle()

    val context = LocalContext.current
    fun checkWifiDetectionPermissions() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) ==
            PackageManager.PERMISSION_GRANTED
    var hasWifiDetectionPermissions by remember { mutableStateOf(checkWifiDetectionPermissions()) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { viewModel.setConfigText(it) }
    }
    // Config text is sensitive (a private key) and rarely needs re-entering,
    // so it's hidden behind an explicit Edit action once something is
    // already saved — shown by default only for first-time setup.
    var isEditingConfig by remember { mutableStateOf(configText.isBlank()) }
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.connect()
    }
    // Required to auto-detect the home SSID (see WifiSsidReader) — requested
    // here, when the user actively saves a home network, rather than
    // unprompted on app start. Both permissions are requested together:
    // modern Android needs NEARBY_WIFI_DEVICES (not just location) to hand
    // back a real, unredacted SSID.
    val wifiPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { hasWifiDetectionPermissions = checkWifiDetectionPermissions() }
    val wifiPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                UrsText(stringResource(R.string.vpn_title), style = UrsTheme.typography.screenTitle)
                UrsText(
                    stringResource(R.string.vpn_description),
                    style = UrsTheme.typography.body,
                    color = UrsTheme.colors.onSurfaceMuted,
                )

                if (isEditingConfig) {
                    UrsTextField(
                        value = configText,
                        onValueChange = viewModel::setConfigText,
                        label = stringResource(R.string.vpn_config_label),
                        singleLine = false,
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    UrsOutlinedButton(
                        text = stringResource(R.string.vpn_scan_qr),
                        onClick = {
                            scanLauncher.launch(
                                ScanOptions()
                                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    .setBeepEnabled(false)
                                    .setOrientationLocked(true)
                                    .setCaptureActivity(PortraitCaptureActivity::class.java),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    UrsButton(
                        text = stringResource(if (justSaved) R.string.vpn_saved else R.string.vpn_save),
                        onClick = {
                            viewModel.saveConfig()
                            isEditingConfig = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UrsText(
                            stringResource(
                                if (configText.isNotBlank()) R.string.vpn_config_configured else R.string.vpn_config_not_configured,
                            ),
                            style = UrsTheme.typography.body,
                        )
                        UrsOutlinedButton(
                            text = stringResource(R.string.vpn_edit_config),
                            onClick = { isEditingConfig = true },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UrsText(
                        stringResource(tunnelStatusLabel(tunnelState)),
                        style = UrsTheme.typography.cardTitle,
                    )
                    if (tunnelState == VpnConnectionState.CONNECTED) {
                        UrsOutlinedButton(text = stringResource(R.string.vpn_disconnect), onClick = viewModel::disconnect)
                    } else {
                        UrsOutlinedButton(
                            text = stringResource(R.string.vpn_connect),
                            onClick = {
                                val permissionIntent = viewModel.wireGuardManager.permissionIntentIfNeeded()
                                if (permissionIntent != null) {
                                    vpnPermissionLauncher.launch(permissionIntent)
                                } else {
                                    viewModel.connect()
                                }
                            },
                            enabled = tunnelState != VpnConnectionState.CONNECTING,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        UrsText(stringResource(R.string.vpn_exclusive_mode_title), style = UrsTheme.typography.body)
                        UrsText(
                            stringResource(R.string.vpn_exclusive_mode_description),
                            style = UrsTheme.typography.caption,
                            color = UrsTheme.colors.onSurfaceMuted,
                        )
                    }
                    UrsCheckbox(checked = exclusiveModeEnabled, onCheckedChange = viewModel::setExclusiveMode)
                }

                Spacer(Modifier.height(Spacing.l))

                UrsText(stringResource(R.string.vpn_home_ssids_title), style = UrsTheme.typography.cardTitle)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UrsTextField(
                        value = newSsidDraft,
                        onValueChange = viewModel::setNewSsidDraft,
                        label = stringResource(R.string.vpn_home_ssid_label),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    UrsButton(
                        text = stringResource(R.string.vpn_home_ssid_add),
                        onClick = {
                            if (!hasWifiDetectionPermissions) {
                                wifiPermissionsLauncher.launch(wifiPermissions)
                            }
                            viewModel.addSsid()
                        },
                    )
                }

                if (homeSsids.isEmpty()) {
                    UrsText(
                        stringResource(R.string.vpn_home_ssids_empty),
                        style = UrsTheme.typography.body,
                        color = UrsTheme.colors.onSurfaceMuted,
                    )
                } else if (!hasWifiDetectionPermissions) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UrsText(
                            stringResource(R.string.vpn_location_permission_needed),
                            style = UrsTheme.typography.body,
                            color = FormErrorColor,
                            modifier = Modifier.weight(1f),
                        )
                        UrsOutlinedButton(
                            text = stringResource(R.string.vpn_grant),
                            onClick = { wifiPermissionsLauncher.launch(wifiPermissions) },
                        )
                    }
                }
            }
        }

        items(homeSsids.toList(), key = { it }) { ssid ->
            UrsCard(radius = Radius.row, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UrsText(ssid, style = UrsTheme.typography.body)
                    UrsIconButton(
                        onClick = { viewModel.removeSsid(ssid) },
                        contentDescription = stringResource(R.string.vpn_ssid_remove, ssid),
                        imageVector = Icons.Filled.Close,
                    )
                }
            }
        }
    }
}

private fun tunnelStatusLabel(state: VpnConnectionState): Int = when (state) {
    VpnConnectionState.DISCONNECTED -> R.string.vpn_status_disconnected
    VpnConnectionState.CONNECTING -> R.string.vpn_status_connecting
    VpnConnectionState.CONNECTED -> R.string.vpn_status_connected
    VpnConnectionState.ERROR -> R.string.vpn_status_error
}
