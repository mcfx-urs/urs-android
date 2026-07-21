package ch.mcfx.urs.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

@SuppressLint("MissingPermission") // BluetoothDevice.name/.address are only read once hasPermission is true
@Composable
fun ObdSetupScreen(viewModel: ObdViewModel = viewModel(factory = ObdViewModel.Factory)) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()

    var hasPermission by remember { mutableStateOf(viewModel.hasPermission()) }
    var pairedDeviceName by remember { mutableStateOf(viewModel.pairedDevice()?.name) }
    var showDevicePicker by remember { mutableStateOf(false) }

    // BLUETOOTH_SCAN is requested alongside BLUETOOTH_CONNECT, not just the latter -
    // ObdManager.hasRequiredPermissions() requires both (BLUETOOTH_SCAN specifically
    // covers cancelDiscovery() in BluetoothObdTransport.open()).
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasPermission = viewModel.hasPermission()
        pairedDeviceName = viewModel.pairedDevice()?.name
    }
    val bluetoothPermissions = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)

    // Pairing (system Bluetooth settings) and the permission grant dialog
    // both happen outside this screen's own composition, so re-check
    // whenever the user comes back to it - same pattern as
    // NotificationSettingsScreen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = viewModel.hasPermission()
                pairedDeviceName = viewModel.pairedDevice()?.name
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        UrsText(stringResource(R.string.obd_setup_title), style = UrsTheme.typography.screenTitle)

        PermissionRow(
            label = stringResource(R.string.obd_permission_label),
            granted = hasPermission,
            onGrant = { permissionLauncher.launch(bluetoothPermissions) },
        )

        DeviceRow(
            deviceName = pairedDeviceName,
            enabled = hasPermission,
            onChooseDevice = { showDevicePicker = true },
        )

        ConnectionRow(
            state = connectionState,
            canConnect = hasPermission && pairedDeviceName != null,
            onConnect = viewModel::connectToPairedDevice,
            onDisconnect = viewModel::disconnect,
        )
    }

    if (showDevicePicker) {
        DevicePickerSheet(
            devices = viewModel.pairedDevices(),
            onDismissRequest = { showDevicePicker = false },
            onSelect = { device ->
                viewModel.selectDevice(device)
                pairedDeviceName = device.name
                showDevicePicker = false
            },
            onOpenBluetoothSettings = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
        )
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    UrsCard(
        radius = Radius.row,
        contentPadding = PaddingValues(horizontal = Spacing.l, vertical = 14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrsText(label, style = UrsTheme.typography.body)
            if (granted) {
                UrsPill(text = "✓ " + stringResource(R.string.obd_permission_granted))
            } else {
                UrsOutlinedButton(text = stringResource(R.string.obd_permission_grant), onClick = onGrant)
            }
        }
    }
}

@Composable
private fun DeviceRow(deviceName: String?, enabled: Boolean, onChooseDevice: () -> Unit) {
    UrsCard(
        radius = Radius.row,
        contentPadding = PaddingValues(horizontal = Spacing.l, vertical = 14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                UrsText(stringResource(R.string.obd_device_label), style = UrsTheme.typography.body)
                UrsText(
                    text = if (enabled && deviceName != null) deviceName else stringResource(R.string.obd_device_not_selected),
                    style = UrsTheme.typography.caption,
                    color = UrsTheme.colors.onSurfaceMuted,
                )
            }
            UrsOutlinedButton(
                text = stringResource(if (deviceName != null) R.string.obd_device_change else R.string.obd_device_choose),
                onClick = onChooseDevice,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun ConnectionRow(state: ObdConnectionState, canConnect: Boolean, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    UrsCard(
        radius = Radius.row,
        contentPadding = PaddingValues(horizontal = Spacing.l, vertical = 14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrsText(stringResource(statusLabel(state)), style = UrsTheme.typography.cardTitle)
            if (state == ObdConnectionState.CONNECTED || state == ObdConnectionState.RECONNECTING) {
                UrsOutlinedButton(text = stringResource(R.string.obd_disconnect), onClick = onDisconnect)
            } else {
                UrsOutlinedButton(
                    text = stringResource(R.string.obd_connect),
                    onClick = onConnect,
                    enabled = canConnect && state != ObdConnectionState.CONNECTING && state != ObdConnectionState.INITIALIZING,
                )
            }
        }
    }
}

// Lists every bonded device, not just ones matching the BT200 name hint -
// see ObdManager.findPairedDevice's doc comment: cheap ELM327 clones often
// advertise a serial number instead of a vendor name, so the user picking
// explicitly is the reliable path, name-matching is only a convenience.
@Composable
private fun DevicePickerSheet(
    devices: List<BluetoothDevice>,
    onDismissRequest: () -> Unit,
    onSelect: (BluetoothDevice) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
) {
    UrsBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            UrsText(stringResource(R.string.obd_choose_device_title), style = UrsTheme.typography.screenTitle)

            if (devices.isEmpty()) {
                UrsText(
                    stringResource(R.string.obd_choose_device_empty),
                    style = UrsTheme.typography.body,
                    color = UrsTheme.colors.onSurfaceMuted,
                )
            } else {
                devices.forEach { device ->
                    UrsCard(
                        radius = Radius.row,
                        contentPadding = PaddingValues(horizontal = Spacing.l, vertical = 14.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(device) },
                    ) {
                        UrsText(device.name ?: device.address, style = UrsTheme.typography.body)
                    }
                }
            }

            UrsOutlinedButton(
                text = stringResource(R.string.obd_open_bluetooth_settings),
                onClick = onOpenBluetoothSettings,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
