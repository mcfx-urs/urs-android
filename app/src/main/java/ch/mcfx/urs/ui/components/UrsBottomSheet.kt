package ch.mcfx.urs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class UrsSheetValue { Closed, Open }

/**
 * The sheet's fallback height used only for the handful of frames before the
 * panel has been measured (see [UrsNavigationDrawer]'s `PanelWidth` for the
 * same fallback-before-measurement reasoning, mirrored here) — never the
 * actual rendered size, which is `wrapContentHeight` based on [content].
 */
private val FallbackSheetHeight = 400.dp

/**
 * Bottom sheet overlay — replacement for `material3.ModalBottomSheet`. Same
 * layered-[Box]-with-scrim-and-`AnchoredDraggableState` technique as
 * [UrsNavigationDrawer], just anchored to the bottom of the screen and
 * dragged vertically instead of horizontally, and sized to its own content
 * (`wrapContentHeight`) rather than the drawer's fixed panel width.
 *
 * There is no "closed but present" state to model here — call sites only
 * place this composable in the tree while it should be visible (`if
 * (showX) { UrsBottomSheet(...) { ... } }`), so it animates itself open as
 * soon as it enters composition (once its height is known — see
 * [FallbackSheetHeight]), and calls [onDismissRequest] once it has fully
 * animated/dragged back closed, whether that was triggered by dragging down
 * past the default anchor threshold (roughly half of its own height) or by
 * tapping the scrim.
 */
@Composable
fun UrsBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = UrsTheme.colors
    val coroutineScope = rememberCoroutineScope()
    // Only the dark palette has a non-transparent border color (see Color.kt) — same check UrsCard uses.
    val darkTheme = colors.border.alpha > 0f
    val shape = RoundedCornerShape(topStart = Radius.card, topEnd = Radius.card)

    val density = LocalDensity.current
    val fallbackHeightPx = remember(density) { with(density) { FallbackSheetHeight.toPx() } }

    val anchoredState = remember { AnchoredDraggableState(initialValue = UrsSheetValue.Closed) }
    var anchorsReady by remember { mutableStateOf(false) }
    var hasOpened by remember { mutableStateOf(false) }

    // Only start the open animation once the panel has actually been
    // measured and its anchors are known — animating towards an anchor that
    // doesn't exist yet would be a no-op at best.
    LaunchedEffect(anchorsReady) {
        if (anchorsReady) anchoredState.animateTo(UrsSheetValue.Open)
    }

    // Fires onDismissRequest exactly once, only after the sheet has actually
    // been open — guards against the initial (pre-animation) Closed value
    // triggering an immediate, spurious dismiss.
    LaunchedEffect(anchoredState) {
        snapshotFlow { anchoredState.currentValue }.collect { value ->
            when (value) {
                UrsSheetValue.Open -> hasOpened = true
                UrsSheetValue.Closed -> if (hasOpened) onDismissRequest()
            }
        }
    }

    val rawOffset = anchoredState.offset
    val offsetPx = if (!rawOffset.isNaN()) rawOffset else fallbackHeightPx
    val closedOffsetPx = if (anchoredState.anchors.size > 0) {
        anchoredState.anchors.positionOf(UrsSheetValue.Closed)
    } else {
        fallbackHeightPx
    }
    // Fraction of "how open" the sheet currently is, 0f (closed) .. 1f (open).
    val openFraction = if (closedOffsetPx > 0f) (1f - offsetPx / closedOffsetPx).coerceIn(0f, 1f) else 0f

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // The sheet must never grow tall enough to push its own top past the
        // status bar — without this cap, tall content (long forms, or a
        // shorter form pushed taller by the IME's own bottom padding below)
        // just overflows above y=0 behind the status bar instead of
        // scrolling, since wrapContentHeight()+BottomCenter alone has no
        // upper bound. Reserving exactly the status bar's own height keeps
        // this in sync with the device's actual inset rather than a guessed
        // constant.
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val maxSheetHeight = maxHeight - statusBarTop

        // Only present once there's something to intercept — a fully closed
        // sheet must not steal taps meant for whatever is behind it.
        if (openFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f * openFraction))
                    .pointerInput(anchoredState) {
                        detectTapGestures(
                            onTap = { coroutineScope.launch { anchoredState.animateTo(UrsSheetValue.Closed) } },
                        )
                    },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .wrapContentHeight()
                .heightIn(max = maxSheetHeight)
                .offset { IntOffset(0, offsetPx.roundToInt()) }
                .anchoredDraggable(anchoredState, Orientation.Vertical)
                .onGloballyPositioned { coordinates ->
                    val panelHeightPx = coordinates.size.height.toFloat()
                    if (panelHeightPx > 0f) {
                        anchoredState.updateAnchors(
                            DraggableAnchors {
                                UrsSheetValue.Closed at panelHeightPx
                                UrsSheetValue.Open at 0f
                            },
                        )
                        anchorsReady = true
                    }
                }
                .shadow(elevation = 10.dp, shape = shape)
                .clip(shape)
                .background(colors.surface)
                .then(
                    if (darkTheme) Modifier.border(1.dp, colors.border, shape) else Modifier,
                )
                // Edge-to-edge (see MainActivity's enableEdgeToEdge()) means nothing
                // paints behind the navigation bar automatically — pad the sheet's
                // own content below it explicitly, same reasoning as UrsTopBar.
                // Unioned with the IME inset (rather than a separate imePadding())
                // so the two never stack additively while the keyboard covers the
                // navigation bar — without this, a focused text field's sheet
                // content (results list, form fields, buttons) ends up rendered
                // behind the on-screen keyboard instead of shrinking to fit above it.
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(bottom = Spacing.l),
        ) {
            DragHandle()
            content()
        }
    }
}

/** Small centered rounded bar hinting that the sheet above it is draggable. */
@Composable
private fun ColumnScope.DragHandle() {
    Box(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(vertical = Spacing.s)
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(UrsTheme.colors.onSurfaceMuted.copy(alpha = 0.4f)),
    )
}
