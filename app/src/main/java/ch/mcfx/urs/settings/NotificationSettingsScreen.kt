package ch.mcfx.urs.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R

@Composable
fun NotificationSettingsScreen(
    viewModel: NotificationSettingsViewModel = viewModel(factory = NotificationSettingsViewModel.Factory),
) {
    val context = LocalContext.current

    fun checkNotificationsGranted() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    var hasNotificationPermission by remember { mutableStateOf(checkNotificationsGranted()) }
    var canScheduleExactAlarms by remember { mutableStateOf(viewModel.canScheduleExactAlarms()) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { hasNotificationPermission = it }

    // The exact-alarm toggle lives in system settings, not a dialog this app
    // controls — re-check both permissions whenever the user comes back to
    // this screen (e.g. returning from that settings page).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = checkNotificationsGranted()
                canScheduleExactAlarms = viewModel.canScheduleExactAlarms()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.notifications_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.notifications_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PermissionRow(
                    label = stringResource(R.string.notifications_permission_label),
                    granted = hasNotificationPermission,
                    onGrant = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
                PermissionRow(
                    label = stringResource(R.string.notifications_exact_alarm_label),
                    granted = canScheduleExactAlarms,
                    onGrant = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                )
            }
        }

        Button(onClick = viewModel::sendTestNotification, enabled = hasNotificationPermission) {
            Text(stringResource(R.string.notifications_send_test))
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        if (granted) {
            Text(stringResource(R.string.notifications_granted), color = MaterialTheme.colorScheme.primary)
        } else {
            OutlinedButton(onClick = onGrant) { Text(stringResource(R.string.notifications_grant)) }
        }
    }
}
