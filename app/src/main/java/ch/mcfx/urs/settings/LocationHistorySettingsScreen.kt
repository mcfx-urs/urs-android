package ch.mcfx.urs.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

private val foregroundLocationPermissions =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

private fun hasForegroundLocationPermission(context: Context) =
    foregroundLocationPermissions.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

// A distinct runtime permission only from API 29 on — below that, a granted
// foreground permission already covers background use.
private fun hasBackgroundLocationPermission(context: Context) =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private val INTERVAL_OPTIONS_MINUTES = listOf(1L, 2L, 5L, 10L, 15L, 30L, 60L, 120L, 240L)

@Composable
fun LocationHistorySettingsScreen(
    viewModel: LocationHistorySettingsViewModel = viewModel(factory = LocationHistorySettingsViewModel.Factory),
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val intervalMinutes by viewModel.intervalMinutes.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var hasForegroundPermission by remember { mutableStateOf(hasForegroundLocationPermission(context)) }
    var hasBackgroundPermission by remember { mutableStateOf(hasBackgroundLocationPermission(context)) }

    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasForegroundPermission = result.values.any { it }
    }

    // Background location can only be granted/revoked from system Settings
    // (deep-linked below), not a dialog this app controls — re-check both
    // permissions whenever the user returns to this screen, same pattern as
    // NotificationSettingsScreen's exact-alarm row.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasForegroundPermission = hasForegroundLocationPermission(context)
                hasBackgroundPermission = hasBackgroundLocationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val intervalLabels = mapOf(
        1L to stringResource(R.string.location_history_interval_1),
        2L to stringResource(R.string.location_history_interval_2),
        5L to stringResource(R.string.location_history_interval_5),
        10L to stringResource(R.string.location_history_interval_10),
        15L to stringResource(R.string.location_history_interval_15),
        30L to stringResource(R.string.location_history_interval_30),
        60L to stringResource(R.string.location_history_interval_60),
        120L to stringResource(R.string.location_history_interval_120),
        240L to stringResource(R.string.location_history_interval_240),
    )

    val colors = UrsTheme.colors

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        UrsText(stringResource(R.string.location_history_title), style = UrsTheme.typography.screenTitle)
        UrsText(
            stringResource(R.string.location_history_description),
            style = UrsTheme.typography.caption,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(bottom = Spacing.s),
        )

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
                UrsText(stringResource(R.string.location_history_enable), style = UrsTheme.typography.body)
                UrsCheckbox(checked = enabled, onCheckedChange = viewModel::setEnabled)
            }
        }

        if (enabled) {
            PermissionRow(
                label = stringResource(R.string.location_history_foreground_permission_label),
                granted = hasForegroundPermission,
                onGrant = { foregroundPermissionLauncher.launch(foregroundLocationPermissions) },
            )
            // Android forbids requesting foreground and background location
            // in the same dialog on API 30+, and background location can't
            // be requested via a normal runtime dialog at all from API 29 on
            // — the reliable path is this deep link to the app's own
            // settings page, same as NotificationSettingsScreen's
            // SCHEDULE_EXACT_ALARM row.
            PermissionRow(
                label = stringResource(R.string.location_history_background_permission_label),
                granted = hasBackgroundPermission,
                enabled = hasForegroundPermission,
                onGrant = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                },
            )

            UrsDropdownField(
                label = stringResource(R.string.location_history_interval_label),
                options = INTERVAL_OPTIONS_MINUTES,
                selectedLabel = intervalLabels[intervalMinutes],
                optionLabel = { intervalLabels[it] ?: it.toString() },
                onSelect = viewModel::setIntervalMinutes,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit, enabled: Boolean = true) {
    val colors = UrsTheme.colors
    val alpha = if (enabled) 1f else colors.disabledAlpha
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
            UrsText(label, style = UrsTheme.typography.body, color = colors.onSurface.copy(alpha = alpha))
            if (granted) {
                UrsPill(text = "✓ " + stringResource(R.string.location_history_granted))
            } else {
                UrsOutlinedButton(
                    text = stringResource(R.string.location_history_grant),
                    onClick = onGrant,
                    enabled = enabled,
                )
            }
        }
    }
}
