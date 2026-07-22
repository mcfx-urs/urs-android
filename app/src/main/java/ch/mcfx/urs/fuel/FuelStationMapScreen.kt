package ch.mcfx.urs.fuel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay

// Closer than LifeMapScreen's DEFAULT_ZOOM (12.0, a route-overview level) —
// this screen is about confirming one specific street-level position.
private const val DEFAULT_ZOOM = 17.0

// Fallback center when there's neither a geocode result nor an ambient
// device location to seed from (manual-fallback path, no location fix
// available yet) — central Switzerland, a reasonable default for this app.
private val FALLBACK_CENTER = GeoPoint(46.8182, 8.2275)
private const val FALLBACK_ZOOM = 7.0

/**
 * Full-screen "confirm position" step: a draggable pin the user can nudge
 * (or tap-place from scratch, for the no-geocode-match fallback) before
 * writing it back into the station form. Reached either after a successful
 * address search (pin pre-set to the geocoded result) or the manual
 * fallback (no pin until the user taps).
 */
@Composable
fun FuelStationMapScreen(
    viewModel: StationsViewModel,
    onConfirm: () -> Unit,
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()

    val initialPin = remember {
        val lat = formState.latitude?.toDoubleOrNull()
        val lon = formState.longitude?.toDoubleOrNull()
        if (lat != null && lon != null) GeoPoint(lat, lon) else null
    }
    var pinPosition by remember { mutableStateOf(initialPin) }

    Box(modifier = Modifier.fillMaxSize()) {
        StationMapView(
            initialPin = initialPin,
            fallbackCenter = viewModel.locationProvider.currentLocation.value
                ?.let { GeoPoint(it.latitude, it.longitude) },
            pinPosition = pinPosition,
            onPinPositionChange = { pinPosition = it },
            modifier = Modifier.fillMaxSize(),
        )

        UrsCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // The app draws edge-to-edge, so without this the card (and
                // its Confirm button) ends up positioned partly inside the
                // system navigation bar's reserved touch region — same fix
                // as UrsFab's own windowInsetsPadding(WindowInsets.navigationBars).
                .windowInsetsPadding(WindowInsets.navigationBars)
                .fillMaxWidth()
                .padding(Spacing.l),
        ) {
            UrsText(
                stringResource(R.string.station_map_instructions),
                style = UrsTheme.typography.body,
                color = UrsTheme.colors.onSurfaceMuted,
            )
            UrsButton(
                text = stringResource(R.string.station_map_confirm),
                onClick = {
                    pinPosition?.let { viewModel.setPositionFromMap(it.latitude, it.longitude) }
                    onConfirm()
                },
                enabled = pinPosition != null,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
            )
        }
    }
}

@Composable
private fun StationMapView(
    initialPin: GeoPoint?,
    fallbackCenter: GeoPoint?,
    pinPosition: GeoPoint?,
    onPinPositionChange: (GeoPoint) -> Unit,
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

    // Center once — the geocoded pin if there is one, else the ambient
    // device location, else a hardcoded fallback. Unlike LifeMapScreen this
    // never re-centers afterward; the user is expected to pan/zoom freely
    // while placing the pin.
    //
    // Must wait for the MapView to actually have valid (non-zero) width/
    // height: calling setZoom()/setCenter() any earlier computes the
    // geo-to-screen projection against a still-zero rect, silently landing
    // on a wrong (and, depending on the zoom delta involved, potentially far
    // off — confirmed on-device to land a Swiss address in Germany, not a
    // minor pixel offset) center. osmdroid's own addOnFirstLayoutListener/
    // isLayoutOccurred was tried first and did not reliably fix this when
    // the MapView is hosted inside Compose's AndroidView (its layout timing
    // relative to Compose's own measure/layout passes isn't guaranteed the
    // same way as in a plain XML-inflated Activity) — View.post() is the
    // standard, library-independent way to defer past whatever layout pass
    // is currently in flight, so it's used here instead.
    val hasCentered = remember { booleanArrayOf(false) }

    AndroidView(
        factory = { mapView },
        // Same reasoning as LifeMapScreen: osmdroid's own requestLayout()
        // calls can otherwise paint outside its Compose-assigned bounds.
        modifier = modifier.clipToBounds(),
        update = { view ->
            if (!hasCentered[0]) {
                hasCentered[0] = true
                val center = initialPin ?: fallbackCenter ?: FALLBACK_CENTER
                val zoom = if (initialPin != null || fallbackCenter != null) DEFAULT_ZOOM else FALLBACK_ZOOM
                view.post {
                    view.controller.setZoom(zoom)
                    view.controller.setCenter(center)
                }
            }

            view.overlays.clear()

            // Added first so it sits underneath the marker overlay below —
            // otherwise it would swallow the marker's own drag gestures.
            view.overlays.add(
                MapEventsOverlay(
                    object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            onPinPositionChange(p)
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint): Boolean = false
                    },
                ),
            )

            pinPosition?.let { position ->
                view.overlays.add(
                    Marker(view).apply {
                        this.position = position
                        isDraggable = true
                        setOnMarkerDragListener(
                            object : Marker.OnMarkerDragListener {
                                override fun onMarkerDrag(marker: Marker) = Unit
                                override fun onMarkerDragEnd(marker: Marker) = onPinPositionChange(marker.position)
                                override fun onMarkerDragStart(marker: Marker) = Unit
                            },
                        )
                    },
                )
            }

            view.invalidate()
        },
    )
}
