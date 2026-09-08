package ch.mcfx.urs.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsColors
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Same "no error role in the design system yet" workaround as
// AboutScreen.kt/ServiceScreen.kt/WorkTimeScreen.kt.
private val FormErrorColor = Color(0xFFD64545)

// Deliberately plain monospace lines, not UrsCard bubbles — GitHub issue #60
// follow-up: the card-per-entry bottom sheet made it hard to scan a run of
// events at a glance, a plain terminal-style trail reads faster for this.
private val LogTimeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Medium)
private val LogEventStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
private val LogDetailStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Normal)

// Time-only, no date: the 500-entry cap (see LocationCaptureLogDao) covers a
// window of at most a few hours once the activity-recognition heartbeat is
// on, so the date would just be visual noise here.
private val LogTimestampFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

@Composable
fun LocationCaptureLogScreen(viewModel: LocationCaptureLogViewModel = viewModel(factory = LocationCaptureLogViewModel.Factory)) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val currentState by viewModel.currentState.collectAsStateWithLifecycle()
    val colors = UrsTheme.colors

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.l, vertical = Spacing.m)) {
        UrsText(
            stringResource(R.string.about_location_capture_details_title),
            style = UrsTheme.typography.screenTitle,
            modifier = Modifier.padding(bottom = Spacing.m),
        )
        LocationCaptureCurrentStateHeader(currentState, colors)
        if (entries.isEmpty()) {
            UrsText(
                stringResource(R.string.about_location_capture_empty),
                style = UrsTheme.typography.body,
                color = colors.onSurfaceMuted,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                contentPadding = PaddingValues(bottom = Spacing.l),
            ) {
                items(entries, key = { it.id }) { entry -> LocationCaptureLogLine(entry, colors) }
            }
        }
    }
}

// Pinned above the scrolling log, same tag+detail grammar as a log line
// (below) — the live current-state readout the owner asked for on top of
// the historical trail: what activity/tier is in effect right now, and
// since when, without having to scroll for the most recent POLL/ARMED line.
@Composable
private fun LocationCaptureCurrentStateHeader(state: LocationCaptureCurrentStateUi, colors: UrsColors) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.padding(bottom = Spacing.l),
    ) {
        LocationCaptureLiveLine("ACTIVITY", activityStateText(state), colors)
        LocationCaptureLiveLine("GEOFENCE", geofenceStateText(state), colors)
    }
}

@Composable
private fun LocationCaptureLiveLine(tag: String, detail: String, colors: UrsColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        UrsText(text = tag, style = LogEventStyle, color = colors.accent)
        UrsText(text = detail, style = LogDetailStyle, color = colors.onSurface)
    }
}

private fun activityStateText(state: LocationCaptureCurrentStateUi): String {
    if (!state.activityEnabled) return "toggle off"
    val label = state.activityLabel ?: "no classification yet"
    val motion = if (state.activityIsStill) "still" else "moving"
    val since = if (state.activityStillSinceMillis > 0) " — $motion since ${formatLogTime(state.activityStillSinceMillis)}" else ""
    return "$label (${state.activityConfidence}%)$since"
}

private fun geofenceStateText(state: LocationCaptureCurrentStateUi): String {
    if (!state.geofenceEnabled) return "toggle off"
    val tier = if (state.geofenceIsDense) "dense" else "sparse"
    val since = if (state.geofenceDenseSinceMillis > 0) " since ${formatLogTime(state.geofenceDenseSinceMillis)}" else " (not yet armed)"
    return "$tier$since"
}

@Composable
private fun LocationCaptureLogLine(entry: LocationCaptureLogEntryUi, colors: UrsColors) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            UrsText(text = formatLogTime(entry.timestampMillis), style = LogTimeStyle, color = colors.onSurfaceMuted)
            UrsText(text = entry.eventType, style = LogEventStyle, color = eventColor(entry.eventType, colors))
        }
        UrsText(text = entry.detail, style = LogDetailStyle, color = colors.onSurface)
    }
}

// _ERROR/_SKIPPED/_TIMEOUT event types (see LocationGeofenceManager/
// LocationHistorySettingsViewModel/LocationActivityRecognitionReceiver/
// LocationCapture) flag something that didn't happen as expected — worth
// standing out from the routine ARMED/CHANGED/FIRED/POLL/GPS_READ trail.
private fun eventColor(eventType: String, colors: UrsColors): Color =
    if (eventType.endsWith("_ERROR") || eventType.endsWith("_SKIPPED") || eventType.endsWith("_TIMEOUT")) {
        FormErrorColor
    } else {
        colors.accent
    }

private fun formatLogTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(LogTimestampFormat)
