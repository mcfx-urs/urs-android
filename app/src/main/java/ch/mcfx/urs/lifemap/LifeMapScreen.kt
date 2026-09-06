package ch.mcfx.urs.lifemap

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsFilterChip
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
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
    // Halo colour tracks the active theme — the dark palette is the only one
    // with a non-transparent border colour (same check UrsCard/UrsBottomSheet use).
    val haloColorArgb = if (UrsTheme.colors.border.alpha > 0f) 0xFF0A0A0A.toInt() else 0xFFF7F7F7.toInt()

    val rangeLabels = mapOf(
        TimeRange.LAST_DAY to stringResource(R.string.life_map_range_last_day),
        TimeRange.LAST_WEEK to stringResource(R.string.life_map_range_last_week),
        TimeRange.LAST_MONTH to stringResource(R.string.life_map_range_last_month),
        TimeRange.LAST_3_MONTHS to stringResource(R.string.life_map_range_last_3_months),
        TimeRange.LAST_6_MONTHS to stringResource(R.string.life_map_range_last_6_months),
        TimeRange.LAST_YEAR to stringResource(R.string.life_map_range_last_year),
        TimeRange.ALL to stringResource(R.string.life_map_range_all),
    )

    // Full-bleed map with the range picker floating on top, rather than a
    // Column splitting layout space between the two: osmdroid's MapView
    // calls requestLayout() on its own on every zoom/pan, and when it shared
    // a weighted Column slot with the dropdown, that self-triggered relayout
    // let the MapView grow past its allocated share and cover the field
    // above it. A fillMaxSize map has no sibling slot to grow into, and the
    // dropdown is composed after it in the same Box so it always paints on
    // top.
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

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            UrsCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                UrsDropdownField(
                    label = stringResource(R.string.life_map_range_label),
                    options = TimeRange.entries,
                    selectedLabel = rangeLabels[selectedRange],
                    optionLabel = { rangeLabels[it] ?: it.name },
                    onSelect = viewModel::selectRange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                UrsFilterChip(
                    label = stringResource(R.string.life_map_map_style_standard),
                    selected = !mutedMap,
                    onClick = { viewModel.setMutedMap(false) },
                )
                UrsFilterChip(
                    label = stringResource(R.string.life_map_map_style_muted),
                    selected = mutedMap,
                    onClick = { viewModel.setMutedMap(true) },
                )
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
            // age gradient with one short segment per consecutive point
            // pair, each coloured by that segment's index among the
            // currently-loaded points (points is already ascending, per
            // LocationHistoryDao.observeSince's ORDER BY capturedAt), not by
            // elapsed wall-clock time. A time-based fraction collapsed to a
            // near-solid colour across an entire short trip whenever it sat
            // far from the loaded set's other points on the clock (e.g. two
            // separate trips either side of a long stationary gap, where
            // capture briefly pauses — confirmed on-device as two flat
            // loops with no visible transition) — linear-by-point-count
            // instead means every segment gets an equal share of the
            // gradient regardless of how much real time passed since the
            // previous fix.
            if (points.size >= 2) {
                val lastIndex = points.size - 1
                for (i in 1 until points.size) {
                    val fraction = i.toFloat() / lastIndex
                    view.overlays.add(
                        Polyline(view).apply {
                            setPoints(listOf(geoPoints[i - 1], geoPoints[i]))
                            outlinePaint.color = blendGradientStops(gradientStopsArgb, fraction)
                            outlinePaint.strokeWidth = 3f * density
                        },
                    )
                }
            }
            view.invalidate()
        },
    )
}
