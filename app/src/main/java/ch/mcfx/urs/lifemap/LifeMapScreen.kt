package ch.mcfx.urs.lifemap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.local.LocationHistoryEntity
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private const val DEFAULT_ZOOM = 12.0

@Composable
fun LifeMapScreen(viewModel: LifeMapViewModel = viewModel(factory = LifeMapViewModel.Factory)) {
    val selectedRange by viewModel.selectedRange.collectAsStateWithLifecycle()
    val points by viewModel.points.collectAsStateWithLifecycle()

    val rangeLabels = mapOf(
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
            points = points,
            selectedRange = selectedRange,
            startLabel = stringResource(R.string.life_map_marker_start),
            endLabel = stringResource(R.string.life_map_marker_end),
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

        UrsCard(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(Spacing.l),
            contentPadding = PaddingValues(0.dp),
        ) {
            UrsDropdownField(
                label = stringResource(R.string.life_map_range_label),
                options = TimeRange.entries,
                selectedLabel = rangeLabels[selectedRange],
                optionLabel = { rangeLabels[it] ?: it.name },
                onSelect = viewModel::selectRange,
                modifier = Modifier.fillMaxWidth(),
            )
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
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply { setMultiTouchControls(true) }
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
    // synced; recentring on every one of those used to snap the map back to
    // DEFAULT_ZOOM mid-interaction, which read as the map resetting itself
    // whenever the user zoomed.
    var lastFitRange by remember { mutableStateOf<TimeRange?>(null) }
    LaunchedEffect(selectedRange, points) {
        if (points.isNotEmpty() && selectedRange != lastFitRange) {
            mapView.controller.setCenter(GeoPoint(points.last().latitude, points.last().longitude))
            mapView.controller.setZoom(DEFAULT_ZOOM)
            lastFitRange = selectedRange
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
            val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }

            if (geoPoints.size >= 2) {
                view.overlays.add(Polyline(view).apply { setPoints(geoPoints) })
            }
            geoPoints.firstOrNull()?.let { start ->
                view.overlays.add(
                    Marker(view).apply {
                        position = start
                        title = startLabel
                    },
                )
            }
            geoPoints.lastOrNull()?.takeIf { geoPoints.size > 1 }?.let { end ->
                view.overlays.add(
                    Marker(view).apply {
                        position = end
                        title = endLabel
                    },
                )
            }
            view.invalidate()
        },
    )
}
