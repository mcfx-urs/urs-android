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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import ch.mcfx.urs.location.GradientMode
import ch.mcfx.urs.lifemap.TimeRange
import ch.mcfx.urs.ui.components.UrsColorWheelDialog
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsFilterChip
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
private val STATIONARY_THRESHOLD_OPTIONS_METERS = listOf(0L, 10L, 25L, 50L, 100L, 250L)
private val GEOFENCE_RADIUS_OPTIONS_METERS = listOf(50L, 100L, 150L, 200L, 300L, 500L)
private val STILL_FALLBACK_OPTIONS_MINUTES = listOf(0L, 5L, 10L, 15L, 30L, 60L)
private val MAX_GRADIENT_CHUNKS_OPTIONS = listOf(10, 15, 20, 30, 50, 75, 100)
private val MIN_GRADIENT_CHUNK_METERS_OPTIONS = listOf(10L, 25L, 50L, 100L, 200L)

@Composable
fun LocationHistorySettingsScreen(
    viewModel: LocationHistorySettingsViewModel = viewModel(factory = LocationHistorySettingsViewModel.Factory),
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val intervalMinutes by viewModel.intervalMinutes.collectAsStateWithLifecycle()
    val stationaryThresholdMeters by viewModel.stationaryThresholdMeters.collectAsStateWithLifecycle()
    val precisionModeEnabled by viewModel.precisionModeEnabled.collectAsStateWithLifecycle()
    val trackColors by viewModel.trackColors.collectAsStateWithLifecycle()
    val gradientMode by viewModel.gradientMode.collectAsStateWithLifecycle()
    val trackHaloEnabled by viewModel.trackHaloEnabled.collectAsStateWithLifecycle()
    val segmentChunkingCutoff by viewModel.segmentChunkingCutoff.collectAsStateWithLifecycle()
    val maxGradientChunksPerSegment by viewModel.maxGradientChunksPerSegment.collectAsStateWithLifecycle()
    val minGradientChunkMeters by viewModel.minGradientChunkMeters.collectAsStateWithLifecycle()
    var editingTrackStop by remember { mutableStateOf<Int?>(null) }

    val geofenceAdaptiveEnabled by viewModel.geofenceAdaptiveEnabled.collectAsStateWithLifecycle()
    val geofenceRadiusMeters by viewModel.geofenceRadiusMeters.collectAsStateWithLifecycle()
    val geofenceSparseIntervalMinutes by viewModel.geofenceSparseIntervalMinutes.collectAsStateWithLifecycle()
    val activityPauseEnabled by viewModel.activityPauseEnabled.collectAsStateWithLifecycle()
    val activityStillFallbackMinutes by viewModel.activityStillFallbackMinutes.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var hasForegroundPermission by remember { mutableStateOf(hasForegroundLocationPermission(context)) }
    var hasBackgroundPermission by remember { mutableStateOf(hasBackgroundLocationPermission(context)) }
    var hasExactAlarmPermission by remember { mutableStateOf(viewModel.canScheduleExactAlarms()) }
    var hasActivityPermission by remember { mutableStateOf(viewModel.hasActivityRecognitionPermission()) }

    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasForegroundPermission = result.values.any { it }
    }

    val activityPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasActivityPermission = granted
        if (granted) viewModel.rearmIfNeeded()
    }

    // Background location and exact-alarm access can only be granted/revoked
    // from system Settings (deep-linked below), not a dialog this app
    // controls — re-check all three permissions whenever the user returns to
    // this screen, same pattern as NotificationSettingsScreen's exact-alarm
    // row.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasForegroundPermission = hasForegroundLocationPermission(context)
                hasBackgroundPermission = hasBackgroundLocationPermission(context)
                hasExactAlarmPermission = viewModel.canScheduleExactAlarms()
                hasActivityPermission = viewModel.hasActivityRecognitionPermission()
                viewModel.rearmIfNeeded()
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

    val stationaryThresholdLabels = mapOf(
        0L to stringResource(R.string.location_history_stationary_threshold_0),
        10L to stringResource(R.string.location_history_stationary_threshold_10),
        25L to stringResource(R.string.location_history_stationary_threshold_25),
        50L to stringResource(R.string.location_history_stationary_threshold_50),
        100L to stringResource(R.string.location_history_stationary_threshold_100),
        250L to stringResource(R.string.location_history_stationary_threshold_250),
    )

    val geofenceRadiusLabels = mapOf(
        50L to stringResource(R.string.location_history_geofence_radius_50),
        100L to stringResource(R.string.location_history_geofence_radius_100),
        150L to stringResource(R.string.location_history_geofence_radius_150),
        200L to stringResource(R.string.location_history_geofence_radius_200),
        300L to stringResource(R.string.location_history_geofence_radius_300),
        500L to stringResource(R.string.location_history_geofence_radius_500),
    )

    // 0 = pause; the rest reuse the same "every N min" phrasing as the
    // capture-interval dropdown above.
    val stillFallbackLabels = mapOf(0L to stringResource(R.string.location_history_still_fallback_pause)) + intervalLabels

    // Same TimeRange labels the Life Map screen's own range dropdown uses
    // (GitHub issue #93) — this is the same underlying concept, not a
    // separately-worded duplicate.
    val chunkingCutoffLabels = mapOf(
        TimeRange.LAST_DAY to stringResource(R.string.life_map_range_last_day),
        TimeRange.LAST_WEEK to stringResource(R.string.life_map_range_last_week),
        TimeRange.LAST_MONTH to stringResource(R.string.life_map_range_last_month),
        TimeRange.LAST_3_MONTHS to stringResource(R.string.life_map_range_last_3_months),
        TimeRange.LAST_6_MONTHS to stringResource(R.string.life_map_range_last_6_months),
        TimeRange.LAST_YEAR to stringResource(R.string.life_map_range_last_year),
        TimeRange.ALL to stringResource(R.string.life_map_range_all),
    )
    val maxGradientChunksLabels = mapOf(
        10 to stringResource(R.string.location_history_chunking_max_chunks_10),
        15 to stringResource(R.string.location_history_chunking_max_chunks_15),
        20 to stringResource(R.string.location_history_chunking_max_chunks_20),
        30 to stringResource(R.string.location_history_chunking_max_chunks_30),
        50 to stringResource(R.string.location_history_chunking_max_chunks_50),
        75 to stringResource(R.string.location_history_chunking_max_chunks_75),
        100 to stringResource(R.string.location_history_chunking_max_chunks_100),
    )
    val minGradientChunkMetersLabels = mapOf(
        10L to stringResource(R.string.location_history_chunking_min_meters_10),
        25L to stringResource(R.string.location_history_chunking_min_meters_25),
        50L to stringResource(R.string.location_history_chunking_min_meters_50),
        100L to stringResource(R.string.location_history_chunking_min_meters_100),
        200L to stringResource(R.string.location_history_chunking_min_meters_200),
    )

    val colors = UrsTheme.colors

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.xl),
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

            UrsDropdownField(
                label = stringResource(R.string.location_history_stationary_threshold_label),
                options = STATIONARY_THRESHOLD_OPTIONS_METERS,
                selectedLabel = stationaryThresholdLabels[stationaryThresholdMeters],
                optionLabel = { stationaryThresholdLabels[it] ?: it.toString() },
                onSelect = viewModel::setStationaryThresholdMeters,
                modifier = Modifier.fillMaxWidth(),
            )

            UrsCard(
                radius = Radius.row,
                contentPadding = PaddingValues(horizontal = Spacing.l, vertical = 14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UrsText(stringResource(R.string.location_history_precision_mode), style = UrsTheme.typography.body)
                        UrsCheckbox(checked = precisionModeEnabled, onCheckedChange = viewModel::setPrecisionModeEnabled)
                    }
                    UrsText(
                        stringResource(R.string.location_history_precision_mode_description),
                        style = UrsTheme.typography.caption,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier.padding(top = Spacing.s),
                    )
                }
            }

            if (precisionModeEnabled) {
                // Exact alarms can only be granted/revoked from system
                // Settings, not a dialog this app controls — same deep-link
                // pattern as NotificationSettingsScreen's own row.
                PermissionRow(
                    label = stringResource(R.string.location_history_exact_alarm_permission_label),
                    granted = hasExactAlarmPermission,
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

            UrsText(
                stringResource(R.string.location_history_adaptive_section_title),
                style = UrsTheme.typography.cardTitle,
                modifier = Modifier.padding(top = Spacing.m),
            )

            UrsCard(
                radius = Radius.row,
                contentPadding = PaddingValues(horizontal = Spacing.l, vertical = 14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UrsText(stringResource(R.string.location_history_geofence_adaptive), style = UrsTheme.typography.body)
                        UrsCheckbox(checked = geofenceAdaptiveEnabled, onCheckedChange = viewModel::setGeofenceAdaptiveEnabled)
                    }
                    UrsText(
                        stringResource(R.string.location_history_geofence_adaptive_description),
                        style = UrsTheme.typography.caption,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier.padding(top = Spacing.s),
                    )
                    if (geofenceAdaptiveEnabled) {
                        UrsDropdownField(
                            label = stringResource(R.string.location_history_geofence_radius_label),
                            options = GEOFENCE_RADIUS_OPTIONS_METERS,
                            selectedLabel = geofenceRadiusLabels[geofenceRadiusMeters],
                            optionLabel = { geofenceRadiusLabels[it] ?: it.toString() },
                            onSelect = viewModel::setGeofenceRadiusMeters,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.m),
                        )
                        UrsDropdownField(
                            label = stringResource(R.string.location_history_geofence_sparse_interval_label),
                            options = INTERVAL_OPTIONS_MINUTES,
                            selectedLabel = intervalLabels[geofenceSparseIntervalMinutes],
                            optionLabel = { intervalLabels[it] ?: it.toString() },
                            onSelect = viewModel::setGeofenceSparseIntervalMinutes,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
                        )
                    }
                }
            }

            UrsCard(
                radius = Radius.row,
                contentPadding = PaddingValues(horizontal = Spacing.l, vertical = 14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UrsText(stringResource(R.string.location_history_activity_pause), style = UrsTheme.typography.body)
                        UrsCheckbox(checked = activityPauseEnabled, onCheckedChange = viewModel::setActivityPauseEnabled)
                    }
                    UrsText(
                        stringResource(R.string.location_history_activity_pause_description),
                        style = UrsTheme.typography.caption,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier.padding(top = Spacing.s),
                    )
                    if (activityPauseEnabled) {
                        // Activity recognition is a normal runtime-dialog
                        // permission (unlike background location/exact
                        // alarms above) — a launcher, not a Settings deep link.
                        PermissionRow(
                            label = stringResource(R.string.location_history_activity_permission_label),
                            granted = hasActivityPermission,
                            onGrant = { activityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
                            modifier = Modifier.padding(top = Spacing.m),
                        )
                        UrsDropdownField(
                            label = stringResource(R.string.location_history_still_fallback_label),
                            options = STILL_FALLBACK_OPTIONS_MINUTES,
                            selectedLabel = stillFallbackLabels[activityStillFallbackMinutes],
                            optionLabel = { stillFallbackLabels[it] ?: it.toString() },
                            onSelect = viewModel::setActivityStillFallbackMinutes,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
                        )
                    }
                }
            }
        }

        UrsText(
            stringResource(R.string.location_history_track_appearance),
            style = UrsTheme.typography.cardTitle,
            modifier = Modifier.padding(top = Spacing.m),
        )
        UrsCard(
            radius = Radius.row,
            contentPadding = PaddingValues(horizontal = Spacing.l, vertical = 14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    UrsFilterChip(
                        label = stringResource(R.string.location_history_gradient_mode_hue),
                        selected = gradientMode == GradientMode.HUE,
                        onClick = { viewModel.setGradientMode(GradientMode.HUE) },
                    )
                    UrsFilterChip(
                        label = stringResource(R.string.location_history_gradient_mode_intensity),
                        selected = gradientMode == GradientMode.INTENSITY,
                        onClick = { viewModel.setGradientMode(GradientMode.INTENSITY) },
                    )
                }

                if (gradientMode == GradientMode.HUE) {
                    UrsText(stringResource(R.string.location_history_track_colors), style = UrsTheme.typography.body)
                    UrsText(
                        stringResource(R.string.location_history_track_colors_hint),
                        style = UrsTheme.typography.caption,
                        color = colors.onSurfaceMuted,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        trackColors.forEachIndexed { index, argb ->
                            if (index > 0) {
                                UrsText("→", style = UrsTheme.typography.body, color = colors.onSurfaceMuted)
                            }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(2.dp, colors.onSurfaceMuted.copy(alpha = 0.4f), CircleShape)
                                    .clickable { editingTrackStop = index },
                            )
                        }
                    }
                } else {
                    // Reuses the "newest" hue-mode stop (index 2) as the
                    // intensity base colour — see LifeMapViewModel — so this
                    // mode needs no separate stored colour of its own.
                    UrsText(stringResource(R.string.location_history_track_base_color), style = UrsTheme.typography.body)
                    UrsText(
                        stringResource(R.string.location_history_track_base_color_hint),
                        style = UrsTheme.typography.caption,
                        color = colors.onSurfaceMuted,
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(trackColors[2]))
                            .border(2.dp, colors.onSurfaceMuted.copy(alpha = 0.4f), CircleShape)
                            .clickable { editingTrackStop = 2 },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UrsText(stringResource(R.string.location_history_track_halo), style = UrsTheme.typography.body)
                    UrsCheckbox(checked = trackHaloEnabled, onCheckedChange = viewModel::setTrackHaloEnabled)
                }
            }
        }

        UrsText(
            stringResource(R.string.location_history_chunking_section_title),
            style = UrsTheme.typography.cardTitle,
            modifier = Modifier.padding(top = Spacing.m),
        )
        UrsCard(
            radius = Radius.row,
            contentPadding = PaddingValues(horizontal = Spacing.l, vertical = 14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                UrsText(
                    stringResource(R.string.location_history_chunking_description),
                    style = UrsTheme.typography.caption,
                    color = colors.onSurfaceMuted,
                )
                UrsDropdownField(
                    label = stringResource(R.string.location_history_chunking_cutoff_label),
                    options = TimeRange.entries,
                    selectedLabel = chunkingCutoffLabels[segmentChunkingCutoff],
                    optionLabel = { chunkingCutoffLabels[it] ?: it.name },
                    onSelect = viewModel::setSegmentChunkingCutoff,
                    modifier = Modifier.fillMaxWidth(),
                )
                UrsDropdownField(
                    label = stringResource(R.string.location_history_chunking_max_chunks_label),
                    options = MAX_GRADIENT_CHUNKS_OPTIONS,
                    selectedLabel = maxGradientChunksLabels[maxGradientChunksPerSegment],
                    optionLabel = { maxGradientChunksLabels[it] ?: it.toString() },
                    onSelect = viewModel::setMaxGradientChunksPerSegment,
                    modifier = Modifier.fillMaxWidth(),
                )
                UrsDropdownField(
                    label = stringResource(R.string.location_history_chunking_min_meters_label),
                    options = MIN_GRADIENT_CHUNK_METERS_OPTIONS,
                    selectedLabel = minGradientChunkMetersLabels[minGradientChunkMeters],
                    optionLabel = { minGradientChunkMetersLabels[it] ?: it.toString() },
                    onSelect = viewModel::setMinGradientChunkMeters,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    editingTrackStop?.let { index ->
        UrsColorWheelDialog(
            initialColor = trackColors[index],
            title = stringResource(R.string.location_history_track_color_pick),
            onConfirm = {
                viewModel.setTrackColor(index, it)
                editingTrackStop = null
            },
            onDismiss = { editingTrackStop = null },
        )
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    onGrant: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = UrsTheme.colors
    val alpha = if (enabled) 1f else colors.disabledAlpha
    UrsCard(
        radius = Radius.row,
        contentPadding = PaddingValues(horizontal = Spacing.l, vertical = 14.dp),
        modifier = modifier.fillMaxWidth(),
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
