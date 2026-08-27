package ch.mcfx.urs.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsCheckbox
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

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

    val colors = UrsTheme.colors

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        UrsText(stringResource(R.string.notifications_title), style = UrsTheme.typography.screenTitle)
        UrsText(
            stringResource(R.string.notifications_description),
            style = UrsTheme.typography.caption,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(bottom = Spacing.s),
        )

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
        ActionRow(
            label = stringResource(R.string.notifications_send_test),
            enabled = hasNotificationPermission,
            onClick = viewModel::sendTestNotification,
        )

        val choreRemindersEnabled by viewModel.choreRemindersEnabled.collectAsStateWithLifecycle()
        ToggleRow(
            label = stringResource(R.string.notifications_chore_overdue_label),
            checked = choreRemindersEnabled,
            onCheckedChange = viewModel::setChoreRemindersEnabled,
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    UrsCard(
        radius = Radius.row,
        contentPadding = PaddingValues(horizontal = Spacing.l, vertical = 14.dp),
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrsText(label, style = UrsTheme.typography.body)
            UrsCheckbox(checked = checked, onCheckedChange = onCheckedChange)
        }
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
                UrsPill(text = "✓ " + stringResource(R.string.notifications_granted))
            } else {
                UrsOutlinedButton(text = stringResource(R.string.notifications_grant), onClick = onGrant)
            }
        }
    }
}

@Composable
private fun ActionRow(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = UrsTheme.colors
    val alpha = if (enabled) 1f else colors.disabledAlpha
    UrsCard(
        radius = Radius.row,
        contentPadding = PaddingValues(horizontal = Spacing.l, vertical = 14.dp),
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrsText(label, style = UrsTheme.typography.body, color = colors.onSurface.copy(alpha = alpha))
            UrsText("→", style = UrsTheme.typography.statAccent, color = colors.accent.copy(alpha = alpha))
        }
    }
}
