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
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
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
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import kotlin.math.ceil
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

// Single-point fallback only — zoomToBoundingBox() is used whenever there's more than one point to fit.
private const val DEFAULT_ZOOM = 12.0

/** Extra margin around the fitted points' bounding box so the outermost points don't sit flush against the screen edge. */
private const val BOUNDING_BOX_PADDING_SCALE = 1.25f

/**
 * Below this length, a segment isn't split further — no point paying for
 * sub-polylines the eye can't tell apart. Bounds how far the gradient
 * subdivision (below) can drive total overlay count up for closely-spaced
 * points, which already need no help (see the comment above the render loop).
 */
private const val MIN_GRADIENT_CHUNK_METERS = 50.0

/**
 * Upper bound on how many pieces one real (point-to-point) segment can be
 * split into, regardless of its length. Without this, a single very long
 * gap (long capture interval, or a stationary pause) could alone balloon the
 * overlay count into the thousands for no real gain — that stretch has no
 * actual GPS samples in it either way, just a straight-line interpolation.
 */
private const val MAX_GRADIENT_CHUNKS_PER_SEGMENT = 30

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
                    UrsDropdownField(
                        label = stringResource(R.string.life_map_range_label),
                        options = TimeRange.entries,
                        selectedLabel = rangeLabels[selectedRange],
                        optionLabel = { rangeLabels[it] ?: it.name },
                        onSelect = viewModel::selectRange,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
    selectedRange: TimeRange,
    gradientStopsArgb: List<Int>,
    haloEnabled: Boolean,
    haloColorArgb: Int,
    muted: Boolean,
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
    var lastFitRange by remember { mutableStateOf<TimeRange?>(null) }
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
            // capturedAt — is further split into MIN_GRADIENT_CHUNK_METERS-
            // sized pieces (capped at MAX_GRADIENT_CHUNKS_PER_SEGMENT) so a
            // single long segment (sparse fixes, a big capture interval, a
            // stationary-pause gap) still shows a smooth ramp across its own
            // length instead of one flat block — that stretch has no real
            // GPS samples in it either way, so this is a straight-line
            // interpolation between the two real endpoints, not new data.
            // Falls back to point-count if every point sits at the same spot
            // (zero total distance) so the fraction never divides by zero.
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

                    val chunkCount = if (totalDistanceKm <= 0.0) {
                        1
                    } else {
                        ceil((segmentKm * 1000.0) / MIN_GRADIENT_CHUNK_METERS).toInt()
                            .coerceIn(1, MAX_GRADIENT_CHUNKS_PER_SEGMENT)
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
