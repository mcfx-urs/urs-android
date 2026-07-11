package ch.mcfx.urs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The drawer panel's fixed width. Also doubles as the *fallback* used for the
 * closed-anchor offset before [AnchoredDraggableState.offset] initializes on
 * the very first frame (mirrors how `material3.DismissibleNavigationDrawer`
 * falls back to its own static `DrawerDefaults.MaximumDrawerWidth` rather
 * than an actual measurement, to avoid a one-frame "flash open" before
 * [onGloballyPositioned] below has a chance to report the real width).
 */
private val PanelWidth = 300.dp

/**
 * Side navigation drawer — replacement for `material3.ModalNavigationDrawer`.
 * A layered [Box]: [content] fills the whole area, the drawer panel floats
 * on top of it (positioned via [drawerState]'s `anchoredDraggableState`,
 * draggable horizontally), with a scrim in between that fades in as the
 * panel opens and intercepts taps to close it.
 *
 * Panel elevation reuses [UrsCard]'s exact shadow/border treatment (just
 * without its content padding or a fully-rounded shape — only the two
 * corners facing away from the screen edge are rounded) so it reads as the
 * same kind of "surface floating above background" as every other elevated
 * surface in the app.
 */
@Composable
fun UrsNavigationDrawer(
    drawerState: UrsDrawerState,
    modifier: Modifier = Modifier,
    drawerContent: @Composable ColumnScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = UrsTheme.colors
    val coroutineScope = rememberCoroutineScope()
    // Only the dark palette has a non-transparent border color (see Color.kt) — same check UrsCard uses.
    val darkTheme = colors.border.alpha > 0f
    val panelShape = RoundedCornerShape(topEnd = Radius.card, bottomEnd = Radius.card)

    val density = LocalDensity.current
    val fallbackWidthPx = remember(density) { with(density) { PanelWidth.toPx() } }

    val anchoredState = drawerState.anchoredDraggableState
    val rawOffset = anchoredState.offset
    val offsetPx = when {
        !rawOffset.isNaN() -> rawOffset
        drawerState.isClosed -> -fallbackWidthPx
        else -> 0f
    }
    val widthPx = if (anchoredState.anchors.size > 0) {
        -anchoredState.anchors.positionOf(UrsDrawerValue.Closed)
    } else {
        fallbackWidthPx
    }
    // Fraction of "how open" the drawer currently is, 0f (closed) .. 1f (open).
    val openFraction = if (widthPx > 0f) (1f + offsetPx / widthPx).coerceIn(0f, 1f) else 0f

    Box(modifier = modifier.fillMaxSize()) {
        content()

        // Only present once there's something to intercept — a fully closed
        // drawer must not steal taps meant for `content`.
        if (openFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f * openFraction))
                    .pointerInput(drawerState) {
                        detectTapGestures(onTap = { coroutineScope.launch { drawerState.close() } })
                    },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(PanelWidth)
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .anchoredDraggable(anchoredState, Orientation.Horizontal)
                .onGloballyPositioned { coordinates ->
                    val panelWidthPx = coordinates.size.width.toFloat()
                    if (panelWidthPx > 0f) {
                        anchoredState.updateAnchors(
                            DraggableAnchors {
                                UrsDrawerValue.Closed at -panelWidthPx
                                UrsDrawerValue.Open at 0f
                            },
                        )
                    }
                }
                .shadow(elevation = 10.dp, shape = panelShape)
                .clip(panelShape)
                .background(colors.surface)
                .then(
                    if (darkTheme) Modifier.border(1.dp, colors.border, panelShape) else Modifier,
                )
                // Edge-to-edge (see MainActivity's enableEdgeToEdge()) means nothing
                // paints behind the status bar automatically — push drawerContent
                // below it explicitly, same reasoning as UrsTopBar.
                .windowInsetsPadding(WindowInsets.statusBars),
            content = drawerContent,
        )
    }
}
