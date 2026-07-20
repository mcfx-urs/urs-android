package ch.mcfx.urs.lifemap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.local.LocationHistoryEntity
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

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsDropdownField(
            label = stringResource(R.string.life_map_range_label),
            options = TimeRange.entries,
            selectedLabel = rangeLabels[selectedRange],
            optionLabel = { rangeLabels[it] ?: it.name },
            onSelect = viewModel::selectRange,
            modifier = Modifier.fillMaxWidth(),
        )

        if (points.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                UrsText(
                    stringResource(R.string.life_map_empty),
                    style = UrsTheme.typography.body,
                    color = UrsTheme.colors.onSurfaceMuted,
                )
            }
        } else {
            LifeMapView(
                points = points,
                startLabel = stringResource(R.string.life_map_marker_start),
                endLabel = stringResource(R.string.life_map_marker_end),
                modifier = Modifier.weight(1f).fillMaxWidth(),
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

    AndroidView(
        factory = { mapView },
        modifier = modifier,
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

            geoPoints.lastOrNull()?.let { view.controller.setCenter(it) }
            view.controller.setZoom(DEFAULT_ZOOM)
            view.invalidate()
        },
    )
}
