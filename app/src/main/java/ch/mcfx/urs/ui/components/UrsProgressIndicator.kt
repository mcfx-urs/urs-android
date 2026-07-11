package ch.mcfx.urs.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.ui.theme.UrsTheme

/**
 * Indeterminate spinner — replacement for `material3.CircularProgressIndicator`.
 * Only an indeterminate mode exists here; nothing in this app shows a
 * determinate progress value.
 */
@Composable
fun UrsProgressIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    strokeWidth: Dp = 4.dp,
) {
    val colors = UrsTheme.colors
    val transition = rememberInfiniteTransition(label = "UrsProgressIndicatorRotation")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "UrsProgressIndicatorRotation",
    )

    Canvas(modifier = modifier.size(size).rotate(rotation)) {
        val strokePx = strokeWidth.toPx()
        drawArc(
            color = colors.accent,
            startAngle = 0f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
            // Inset by half the stroke width so the arc's own stroke isn't
            // clipped at the canvas edge.
            topLeft = Offset(strokePx / 2f, strokePx / 2f),
            size = Size(this.size.width - strokePx, this.size.height - strokePx),
        )
    }
}
