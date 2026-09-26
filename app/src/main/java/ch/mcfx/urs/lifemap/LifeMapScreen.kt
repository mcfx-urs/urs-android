package ch.mcfx.urs.lifemap

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.local.LocationHistoryEntity
import ch.mcfx.urs.location.LocationUtils
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsDateField
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTimeField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

// Single-point fallback only — zoomToBoundingBox() is used whenever there's more than one point to fit.
private const val DEFAULT_ZOOM = 12.0

/** Extra margin around the fitted points' bounding box so the outermost points don't sit flush against the screen edge. */
private const val BOUNDING_BOX_PADDING_SCALE = 1.25f

// "Muted" base-map filter: drop most of the tile colour and lift it toward
// white, so a bright track sits clearly on top. Applied to osmdroid's tiles
// overlay, no alternative tile provider needed.
private val MUTED_TILE_FILTER = ColorMatrixColorFilter(
    ColorMatrix().apply {
        setSaturation(0.2f)
        postConcat(
            ColorMatrix(
                floatArrayOf(
                    0.9f, 0f, 0f, 0f, 24f,
                    0f, 0.9f, 0f, 0f, 24f,
                    0f, 0f, 0.9f, 0f, 24f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    },
)

/** Interpolates piecewise across [stops] (already ARGB [Int]s) by [fraction] in `0f..1f`. */
private fun blendGradientStops(stops: List<Int>, fraction: Float): Int {
    val scaled = fraction.coerceIn(0f, 1f) * (stops.size - 1)
    val index = scaled.toInt().coerceIn(0, stops.size - 2)
    return ColorUtils.blendARGB(stops[index], stops[index + 1], scaled - index)
}

/** Straight-line (non-geodesic) interpolation — plenty accurate at the short, sub-segment scale this is used for. */
private fun interpolateGeoPoint(from: GeoPoint, to: GeoPoint, t: Double): GeoPoint =
    GeoPoint(
        from.latitude + (to.latitude - from.latitude) * t,
        from.longitude + (to.longitude - from.longitude) * t,
    )

// No "error" role in the design system's palette yet — same local-constant pattern already used elsewhere (e.g. NoteDetailScreen).
private val FormErrorColor = Color(0xFFD64545)

private val CustomRangeTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Same ISO-date / 24h-`HH:mm` string convention [UrsDateField]/[UrsTimeField] use everywhere else in this app. */
private fun millisToDateAndTime(millis: Long): Pair<String, String> {
    val dateTime = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    return dateTime.toLocalDate().toString() to dateTime.toLocalTime().format(CustomRangeTimeFormatter)
}

private fun dateAndTimeToMillis(date: String, time: String): Long? {
    val localDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
    val localTime = runCatching { LocalTime.parse(time, CustomRangeTimeFormatter) }.getOrNull() ?: return null
    return LocalDateTime.of(localDate, localTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

@Composable
fun LifeMapScreen(viewModel: LifeMapViewModel = viewModel(factory = LifeMapViewModel.Factory)) {
    // selectedRange drives only the dropdown label — it updates the instant
    // the user taps an option, for immediate UI feedback. The map below
    // must never read it directly: see LifeMapPointsState's doc comment for
    // why the map needs range and points bundled from the same emission.
    val selectedRange by viewModel.selectedRange.collectAsStateWithLifecycle()
    val pointsState by viewModel.pointsState.collectAsStateWithLifecycle()
    val points = pointsState.points
    val mutedMap by viewModel.mutedMap.collectAsStateWithLifecycle()
    val trackStyle = viewModel.trackStyle
    val colors = UrsTheme.colors
    // Only the dark palette has a non-transparent border colour (same check UrsCard/UrsBottomSheet use).
    val darkTheme = colors.border.alpha > 0f
    val haloColorArgb = if (darkTheme) 0xFF0A0A0A.toInt() else 0xFFF7F7F7.toInt()
    var controlsSheetOpen by remember { mutableStateOf(false) }
    var customRangeSheetOpen by remember { mutableStateOf(false) }
    var customFromDate by remember { mutableStateOf("") }
    var customFromTime by remember { mutableStateOf("") }
    var customToDate by remember { mutableStateOf("") }
    var customToTime by remember { mutableStateOf("") }
    var customRangeError by remember { mutableStateOf(false) }
    // Two decoupled auto-advance pairs (GitHub issue #76 follow-up), not one
    // long chain: From Date confirmed opens To Date; separately, From Time
    // confirmed opens To Time. From Date and From Time are each always
    // opened by hand — one starts the date pair, the other the time pair,
    // independently of each other. Cancelling a dialog never bumps anything,
    // so a pair simply stops there; every field also stays individually
    // tappable at any time, in any order, so a user who only needs to change
    // e.g. the times (dates already correct) can just tap those two fields
    // directly.
    var toDateOpenSignal by remember { mutableIntStateOf(0) }
    var toTimeOpenSignal by remember { mutableIntStateOf(0) }

    val rangeLabels = mapOf(
        TimeRange.LAST_DAY to stringResource(R.string.life_map_range_last_day),
        TimeRange.LAST_WEEK to stringResource(R.string.life_map_range_last_week),
        TimeRange.LAST_MONTH to stringResource(R.string.life_map_range_last_month),
        TimeRange.LAST_3_MONTHS to stringResource(R.string.life_map_range_last_3_months),
        TimeRange.LAST_6_MONTHS to stringResource(R.string.life_map_range_last_6_months),
        TimeRange.LAST_YEAR to stringResource(R.string.life_map_range_last_year),
        TimeRange.ALL to stringResource(R.string.life_map_range_all),
    )

    // Full-bleed map with a single compact controls toggle floating on top,
    // rather than a Column splitting layout space between the two: osmdroid's
    // MapView calls requestLayout() on its own on every zoom/pan, and when it
    // shared a weighted Column slot with the dropdown, that self-triggered
    // relayout let the MapView grow past its allocated share and cover the
    // field above it. A fillMaxSize map has no sibling slot to grow into,
    // and the controls are composed after it in the same Box so they always
    // paint on top.
    Box(modifier = Modifier.fillMaxSize()) {
        LifeMapView(
            points = pointsState.points,
            // pointsState.range, not selectedRange — must always be the
            // range these exact points were queried for, never the (possibly
            // ahead-of-itself) dropdown selection. See LifeMapPointsState.
            selectedRange = pointsState.range,
            gradientStopsArgb = trackStyle.gradientStops,
            haloEnabled = trackStyle.haloEnabled,
            haloColorArgb = haloColorArgb,
            muted = mutedMap,
            segmentChunkingCutoffDays = trackStyle.segmentChunkingCutoffDays,
            maxGradientChunksPerSegment = trackStyle.maxGradientChunksPerSegment,
            minGradientChunkMeters = trackStyle.minGradientChunkMeters,
            modifier = Modifier.fillMaxSize(),
        )

        if (points.isEmpty()) {
            UrsText(
                stringResource(R.string.life_map_empty),
                style = UrsTheme.typography.body,
                color = UrsTheme.colors.onSurfaceMuted,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Standalone circular surface, not UrsCard/UrsFab — same
        // shadow/clip/background/border treatment as both, just tinted
        // neutral (UrsFab's accent fill would read as a primary action here,
        // not a secondary "open controls" affordance).
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Spacing.l)
                .shadow(
                    elevation = 6.dp,
                    shape = CircleShape,
                    ambientColor = colors.shadowColor.copy(alpha = colors.shadowAlpha),
                    spotColor = colors.shadowColor.copy(alpha = colors.shadowAlpha),
                )
                .clip(CircleShape)
                .background(colors.surface)
                .then(if (darkTheme) Modifier.border(1.dp, colors.border, CircleShape) else Modifier),
        ) {
            UrsIconButton(
                onClick = { controlsSheetOpen = true },
                contentDescription = stringResource(R.string.life_map_controls_label),
                imageVector = Icons.Filled.Tune,
            )
        }

        if (controlsSheetOpen) {
            UrsBottomSheet(onDismissRequest = { controlsSheetOpen = false }) {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    UrsText(
                        text = stringResource(R.string.life_map_controls_label),
                        style = UrsTheme.typography.cardTitle,
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        UrsDropdownField(
                            label = stringResource(R.string.life_map_range_label),
                            options = TimeRange.entries,
                            // Only a Preset selection maps onto one of the 7
                            // dropdown options — an active Custom pick (GitHub
                            // issue #76) has no matching entry, so it falls
                            // back to a dedicated "Custom range" label instead
                            // of leaving the field blank.
                            selectedLabel = when (val range = selectedRange) {
                                is LifeMapRange.Preset -> rangeLabels[range.range]
                                is LifeMapRange.Custom -> stringResource(R.string.life_map_range_custom)
                            },
                            optionLabel = { rangeLabels[it] ?: it.name },
                            onSelect = viewModel::selectRange,
                            modifier = Modifier.weight(1f),
                        )
                        // Opens the from/to sheet below — prefills it from the
                        // currently active custom range, or from the last day
                        // as a starting point otherwise.
                        UrsIconButton(
                            onClick = {
                                val (fromMillis, toMillis) = when (val range = selectedRange) {
                                    is LifeMapRange.Custom -> range.fromMillis to range.toMillis
                                    is LifeMapRange.Preset -> TimeRange.LAST_DAY.toSinceMillis() to System.currentTimeMillis()
                                }
                                val (fromDate, fromTime) = millisToDateAndTime(fromMillis)
                                val (toDate, toTime) = millisToDateAndTime(toMillis)
                                customFromDate = fromDate
                                customFromTime = fromTime
                                customToDate = toDate
                                customToTime = toTime
                                customRangeError = false
                                // Reset both auto-advance pairs, not just the
                                // field values: a leftover non-zero signal
                                // from a previous pass through this sheet
                                // fires immediately on the freshly-composed
                                // field below (LaunchedEffect always runs
                                // once on first composition, regardless of
                                // whether its key "changed" — there's no
                                // prior value for a brand-new composable
                                // instance to compare against), popping the
                                // To Date/To Time dialogs open in the
                                // background the instant the sheet reappears
                                // instead of waiting for an actual confirm.
                                toDateOpenSignal = 0
                                toTimeOpenSignal = 0
                                controlsSheetOpen = false
                                customRangeSheetOpen = true
                            },
                            contentDescription = stringResource(R.string.life_map_range_custom_button),
                            imageVector = Icons.Filled.DateRange,
                        )
                    }
                    // Single icon toggle replacing the former pair of
                    // standard/muted filter chips (GitHub issue #75) — tapping
                    // anywhere in the row flips mutedMap directly, the icon's
                    // tint and the label text both reflect the current state.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setMutedMap(!mutedMap) }
                            .padding(vertical = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        UrsIcon(
                            imageVector = Icons.Filled.Layers,
                            contentDescription = null,
                            tint = if (mutedMap) colors.accent else colors.onSurface,
                        )
                        UrsText(
                            text = stringResource(
                                if (mutedMap) R.string.life_map_map_style_muted else R.string.life_map_map_style_standard,
                            ),
                            style = UrsTheme.typography.body,
                        )
                    }
                }
            }
        }

        if (customRangeSheetOpen) {
            UrsBottomSheet(onDismissRequest = { customRangeSheetOpen = false }) {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    UrsText(
                        text = stringResource(R.string.life_map_range_custom),
                        style = UrsTheme.typography.cardTitle,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        UrsDateField(
                            value = customFromDate,
                            onValueChange = {
                                customFromDate = it
                                toDateOpenSignal++
                            },
                            label = stringResource(R.string.life_map_range_custom_from_date),
                            modifier = Modifier.weight(1f),
                        )
                        UrsTimeField(
                            value = customFromTime,
                            onValueChange = {
                                customFromTime = it
                                toTimeOpenSignal++
                            },
                            label = stringResource(R.string.life_map_range_custom_from_time),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        UrsDateField(
                            value = customToDate,
                            onValueChange = { customToDate = it },
                            label = stringResource(R.string.life_map_range_custom_to_date),
                            modifier = Modifier.weight(1f),
                            openSignal = toDateOpenSignal,
                        )
                        UrsTimeField(
                            value = customToTime,
                            onValueChange = { customToTime = it },
                            label = stringResource(R.string.life_map_range_custom_to_time),
                            modifier = Modifier.weight(1f),
                            openSignal = toTimeOpenSignal,
                        )
                    }
                    if (customRangeError) {
                        UrsText(
                            text = stringResource(R.string.life_map_range_custom_error),
                            style = UrsTheme.typography.body,
                            color = FormErrorColor,
                        )
                    }
                    UrsButton(
                        text = stringResource(R.string.life_map_range_custom_apply),
                        onClick = {
                            val fromMillis = dateAndTimeToMillis(customFromDate, customFromTime)
                            val toMillis = dateAndTimeToMillis(customToDate, customToTime)
                            if (fromMillis != null && toMillis != null && fromMillis < toMillis) {
                                viewModel.selectCustomRange(fromMillis, toMillis)
                                customRangeSheetOpen = false
                            } else {
                                customRangeError = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * osmdroid's [MapView] wrapped for Compose — the first [AndroidView] use in
 * this codebase. Unlike a pure-Compose surface, [MapView] owns real
 * platform resources (tile cache, GL surface) and needs explicit
 * `onResume()`/`onPause()` calls tied to the surrounding lifecycle, not just
 * recomposition.
 */
@Composable
private fun LifeMapView(
    points: List<LocationHistoryEntity>,
    selectedRange: LifeMapRange,
    gradientStopsArgb: List<Int>,
    haloEnabled: Boolean,
    haloColorArgb: Int,
    muted: Boolean,
    segmentChunkingCutoffDays: Long?,
    maxGradientChunksPerSegment: Int,
    minGradientChunkMeters: Double,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            // Redundant with pinch-zoom, and osmdroid's on-screen +/- buttons
            // anchor to the raw screen edge rather than respecting window
            // insets — on 3-button-nav devices they render partly hidden
            // behind the system navigation bar.
            setBuiltInZoomControls(false)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    // Recentre/re-zoom only when the user picks a different time range (or
    // on first load once points arrive) — not on every points update. Room's
    // Flow re-emits on any location_history write, including a background
    // capture landing while this screen is open or the outbox marking a row
    // synced; recentring on every one of those used to snap the map back
    // mid-interaction, which read as the map resetting itself whenever the
    // user zoomed.
    //
    // Fits the camera to the currently-visible points' own bounding box
    // (padded, see BOUNDING_BOX_PADDING_SCALE) rather than a fixed zoom
    // level — a fixed zoom centered on just the newest point made switching
    // to a narrower range (e.g. Last Day) look like nothing had changed
    // whenever recent points sit in the same area as older ones, which is
    // the common case for a route that mostly retraces the same streets.
    // Falls back to a fixed zoom centered on the single point when there's
    // only one (a bounding box over one point has zero area, nothing to fit
    // to).
    //
    // The zoomToBoundingBox()/setZoom()/setCenter() calls are deferred via
    // view.post() — calling them directly here can run before the MapView
    // has a valid (non-zero) layout size (e.g. right on first load, before
    // Room's Flow has had time to let the view settle), which computes the
    // geo-to-screen projection against a zero-size rect and silently lands
    // on the wrong spot — the same root cause fixed for
    // FuelStationMapScreen's map-confirm step, confirmed on-device there to
    // be off by a lot, not just a few pixels.
    // selectedRange and points here are LifeMapScreen's pointsState.range/
    // .points — always from the same LifeMapPointsState emission (see that
    // class's doc comment). Never wire this composable's selectedRange
    // param back to LifeMapViewModel.selectedRange directly: an earlier
    // version did, and the instant-updating dropdown-label StateFlow
    // reaching this effect one recomposition ahead of the matching points
    // (labelled range vs. still-old points) raced mapView.post() against
    // Room's coroutine dispatch with no ordering guarantee between them —
    // confirmed via logcat, points.size flips 174→1661 across two
    // AndroidView updates for one selection — which fit to whichever
    // snapshot happened to run last, correct or stale depending on timing.
    // With range and points now always paired, this effect only ever sees
    // valid combinations, so a plain "already fit this range" guard is safe.
    var lastFitRange by remember { mutableStateOf<LifeMapRange?>(null) }
    LaunchedEffect(selectedRange, points) {
        if (points.isNotEmpty() && selectedRange != lastFitRange) {
            mapView.post {
                if (points.size >= 2) {
                    val boundingBox = BoundingBox.fromGeoPoints(points.map { GeoPoint(it.latitude, it.longitude) })
                        .increaseByScale(BOUNDING_BOX_PADDING_SCALE)
                    mapView.zoomToBoundingBox(boundingBox, false)
                } else {
                    mapView.controller.setZoom(DEFAULT_ZOOM)
                    mapView.controller.setCenter(GeoPoint(points.last().latitude, points.last().longitude))
                }
                lastFitRange = selectedRange
            }
        }
    }

    AndroidView(
        factory = { mapView },
        // osmdroid's MapView otherwise doesn't reliably honor the bounds
        // Compose lays it out with — its own requestLayout() calls (on
        // zoom/pan) can leave it drawing past its assigned rectangle,
        // covering whatever sits above it (dropdown, top app bar). Force the
        // clip so the rendered tiles can never escape the space actually
        // allotted to this composable.
        modifier = modifier.clipToBounds(),
        update = { view ->
            view.overlays.clear()
            view.overlayManager.tilesOverlay.setColorFilter(if (muted) MUTED_TILE_FILTER else null)
            val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }
            val density = context.resources.displayMetrics.density

            // Optional contrasting casing: one continuous wide stroke in a
            // near-white / near-black colour, drawn first so the coloured
            // core segments sit on top of it. A single polyline over the
            // whole route rather than one per segment keeps the overlay
            // count at (segments + 1), not doubled.
            if (haloEnabled && points.size >= 2) {
                view.overlays.add(
                    Polyline(view).apply {
                        setPoints(geoPoints)
                        outlinePaint.color = haloColorArgb
                        outlinePaint.strokeWidth = 7f * density
                    },
                )
            }

            // No native multi-color polyline in osmdroid — approximate the
            // age gradient with short sub-segments, each coloured by its own
            // share of the total track *distance* (GitHub issue #64), not
            // point index or elapsed wall-clock time (a time-based fraction
            // collapsed to a near-solid colour across an entire short trip
            // whenever it sat far from the loaded set's other points on the
            // clock — confirmed on-device as two flat loops either side of a
            // long stationary gap, with no visible transition).
            //
            // Each real (point-to-point) segment — points is already
            // ascending, per LocationHistoryDao.observeSince's ORDER BY
            // capturedAt — is further split into minGradientChunkMeters-
            // sized pieces (capped at maxGradientChunksPerSegment) so a
            // single long segment (sparse fixes, a big capture interval, a
            // stationary-pause gap) still shows a smooth ramp across its own
            // length instead of one flat block — that stretch has no real
            // GPS samples in it either way, so this is a straight-line
            // interpolation between the two real endpoints, not new data.
            // Falls back to point-count if every point sits at the same spot
            // (zero total distance) so the fraction never divides by zero.
            //
            // Sub-chunking only applies up to segmentChunkingCutoffDays
            // (GitHub issue #93) — beyond it, one polyline per real segment
            // is drawn instead, since the fine-grained smoothing is barely
            // visible at the zoom level a long time range is typically
            // viewed at, while the overlay count keeps growing with it.
            // `null` cutoff days means TimeRange.ALL, which never disables
            // sub-chunking; `null` current-range days (the range itself is
            // ALL/unbounded) is always treated as beyond any finite cutoff.
            val currentRangeDurationDays = selectedRange.durationDays()
            val subChunkingEnabled = when {
                segmentChunkingCutoffDays == null -> true
                currentRangeDurationDays == null -> false
                else -> currentRangeDurationDays <= segmentChunkingCutoffDays
            }
            if (points.size >= 2) {
                val lastIndex = points.size - 1
                val segmentDistancesKm = (1 until points.size).map { i ->
                    LocationUtils.haversineKm(
                        points[i - 1].latitude, points[i - 1].longitude,
                        points[i].latitude, points[i].longitude,
                    )
                }
                val totalDistanceKm = segmentDistancesKm.sum()
                var cumulativeDistanceKm = 0.0
                for (i in 1 until points.size) {
                    val segmentKm = segmentDistancesKm[i - 1]
                    val segmentStartKm = cumulativeDistanceKm
                    cumulativeDistanceKm += segmentKm

                    val chunkCount = if (!subChunkingEnabled || totalDistanceKm <= 0.0) {
                        1
                    } else {
                        ceil((segmentKm * 1000.0) / minGradientChunkMeters).toInt()
                            .coerceIn(1, maxGradientChunksPerSegment)
                    }
                    for (chunk in 1..chunkCount) {
                        val tStart = (chunk - 1).toDouble() / chunkCount
                        val tEnd = chunk.toDouble() / chunkCount
                        val fraction = if (totalDistanceKm > 0.0) {
                            ((segmentStartKm + segmentKm * tEnd) / totalDistanceKm).toFloat()
                        } else {
                            i.toFloat() / lastIndex
                        }
                        view.overlays.add(
                            Polyline(view).apply {
                                setPoints(
                                    listOf(
                                        interpolateGeoPoint(geoPoints[i - 1], geoPoints[i], tStart),
                                        interpolateGeoPoint(geoPoints[i - 1], geoPoints[i], tEnd),
                                    ),
                                )
                                outlinePaint.color = blendGradientStops(gradientStopsArgb, fraction)
                                outlinePaint.strokeWidth = 3f * density
                            },
                        )
                    }
                }
            }
            view.invalidate()
        },
    )
}
