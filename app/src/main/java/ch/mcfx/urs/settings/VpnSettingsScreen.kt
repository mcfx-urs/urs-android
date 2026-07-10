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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.vpn.VpnConnectionState
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun VpnSettingsScreen(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val configText by viewModel.configText.collectAsStateWithLifecycle()
    val newSsidDraft by viewModel.newSsidDraft.collectAsStateWithLifecycle()
    val homeSsids by viewModel.homeSsids.collectAsStateWithLifecycle()
    val justSaved by viewModel.justSaved.collectAsStateWithLifecycle()
    val tunnelState by viewModel.tunnelState.collectAsStateWithLifecycle()

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
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.vpn_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.vpn_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isEditingConfig) {
                    OutlinedTextField(
                        value = configText,
                        onValueChange = viewModel::setConfigText,
                        label = { Text(stringResource(R.string.vpn_config_label)) },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedButton(
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
                    ) {
                        Text(stringResource(R.string.vpn_scan_qr))
                    }

                    Button(
                        onClick = {
                            viewModel.saveConfig()
                            isEditingConfig = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(if (justSaved) R.string.vpn_saved else R.string.vpn_save))
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(
                                if (configText.isNotBlank()) R.string.vpn_config_configured else R.string.vpn_config_not_configured,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(onClick = { isEditingConfig = true }) {
                            Text(stringResource(R.string.vpn_edit_config))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(tunnelStatusLabel(tunnelState)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (tunnelState == VpnConnectionState.CONNECTED) {
                        OutlinedButton(onClick = viewModel::disconnect) {
                            Text(stringResource(R.string.vpn_disconnect))
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                val permissionIntent = viewModel.wireGuardManager.permissionIntentIfNeeded()
                                if (permissionIntent != null) {
                                    vpnPermissionLauncher.launch(permissionIntent)
                                } else {
                                    viewModel.connect()
                                }
                            },
                            enabled = tunnelState != VpnConnectionState.CONNECTING,
                        ) {
                            Text(stringResource(R.string.vpn_connect))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(stringResource(R.string.vpn_home_ssids_title), style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newSsidDraft,
                        onValueChange = viewModel::setNewSsidDraft,
                        label = { Text(stringResource(R.string.vpn_home_ssid_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            if (!hasWifiDetectionPermissions) {
                                wifiPermissionsLauncher.launch(wifiPermissions)
                            }
                            viewModel.addSsid()
                        },
                    ) { Text(stringResource(R.string.vpn_home_ssid_add)) }
                }

                if (homeSsids.isEmpty()) {
                    Text(
                        stringResource(R.string.vpn_home_ssids_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (!hasWifiDetectionPermissions) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.vpn_location_permission_needed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = { wifiPermissionsLauncher.launch(wifiPermissions) },
                        ) {
                            Text(stringResource(R.string.vpn_grant))
                        }
                    }
                }
            }
        }

        items(homeSsids.toList(), key = { it }) { ssid ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(ssid, style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { viewModel.removeSsid(ssid) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.vpn_ssid_remove, ssid))
                    }
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
