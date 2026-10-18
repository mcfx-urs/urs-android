package ch.mcfx.urs.journal

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val CommitFraction = 0.3f
private const val CommitVelocityPxPerSec = 1200f
private const val SettleMillis = 180

/**
 * Horizontal swipe between neighbouring pages (months, days) that follows the
 * finger: while dragging, the neighbour slides in next to the current page and
 * stays there as long as the finger is held; on release the page either
 * commits (past [CommitFraction] of the width, or a fast fling) or springs back.
 *
 * [key] is the externally owned current page; a change from elsewhere (arrow
 * buttons, picker) replaces the page without animation. [step] returns the
 * page `delta` steps away, [onSettled] reports a committed swipe.
 */
@Composable
internal fun <T> JournalSwipePager(
    key: T,
    step: (T, Int) -> T,
    onSettled: (T) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    var settled by remember { mutableStateOf(key) }
    LaunchedEffect(key) { settled = key }

    var dragX by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .onSizeChanged { width = it.width.toFloat() }
            .clipToBounds()
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> dragX += delta },
                onDragStarted = { settleJob?.cancel() },
                onDragStopped = { velocity ->
                    val target = when {
                        width <= 0f -> 0f
                        dragX > width * CommitFraction || velocity > CommitVelocityPxPerSec -> width
                        dragX < -width * CommitFraction || velocity < -CommitVelocityPxPerSec -> -width
                        else -> 0f
                    }
                    settleJob = scope.launch {
                        animate(dragX, target, animationSpec = tween(SettleMillis)) { value, _ -> dragX = value }
                        if (target != 0f) {
                            val next = step(settled, if (target > 0f) -1 else 1)
                            settled = next
                            dragX = 0f
                            onSettled(next)
                        }
                    }
                },
            ),
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = dragX }) { content(settled) }
        if (dragX > 0f) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = dragX - width }) { content(step(settled, -1)) }
        } else if (dragX < 0f) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = dragX + width }) { content(step(settled, 1)) }
        }
    }
}
