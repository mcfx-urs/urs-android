package ch.mcfx.urs.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private fun colorToHueSat(argb: Int): Pair<Float, Float> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    return hsv[0] to hsv[1]
}

/**
 * Centered modal for picking one vivid colour by hue (angle) and saturation
 * (distance from centre) on a wheel — value is fixed at full, since the only
 * caller (the Life Map track gradient) always wants bright colours. Same
 * centered-overlay treatment as [UrsErrorDialog].
 */
@Composable
fun UrsColorWheelDialog(
    initialColor: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
) {
    val (initHue, initSat) = remember(initialColor) { colorToHueSat(initialColor) }
    var hue by remember(initialColor) { mutableFloatStateOf(initHue) }
    var sat by remember(initialColor) { mutableFloatStateOf(initSat) }
    val current = Color.hsv(hue, sat.coerceIn(0f, 1f), 1f)

    fun updateFrom(pos: Offset, size: Size) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val dx = pos.x - cx
        val dy = pos.y - cy
        val r = min(cx, cy)
        hue = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f
        sat = if (r <= 0f) 0f else (hypot(dx, dy) / r).coerceIn(0f, 1f)
    }

    Dialog(onDismissRequest = onDismiss) {
        UrsCard(modifier = Modifier.fillMaxWidth()) {
            if (title != null) {
                UrsText(text = title, style = UrsTheme.typography.cardTitle)
                Spacer(Modifier.height(Spacing.m))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f)
                    .pointerInput(Unit) { detectTapGestures { updateFrom(it, size.toSize()) } }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ -> updateFrom(change.position, size.toSize()) }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val radius = min(size.width, size.height) / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color.Red, Color.Yellow, Color.Green,
                                Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
                            ),
                            center = center,
                        ),
                        radius = radius,
                        center = center,
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White, Color.Transparent),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                    val ang = Math.toRadians(hue.toDouble())
                    val sel = Offset(
                        center.x + (cos(ang) * sat * radius).toFloat(),
                        center.y + (sin(ang) * sat * radius).toFloat(),
                    )
                    drawCircle(Color.White, radius = 9.dp.toPx(), center = sel)
                    drawCircle(Color.Black, radius = 9.dp.toPx(), center = sel, style = Stroke(width = 2.dp.toPx()))
                }
            }

            Spacer(Modifier.height(Spacing.m))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(current)
                        .border(1.dp, UrsTheme.colors.onSurfaceMuted, CircleShape),
                )
                UrsText(String.format("#%06X", current.toArgb() and 0xFFFFFF), style = UrsTheme.typography.body)
            }

            Spacer(Modifier.height(Spacing.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                UrsOutlinedButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                UrsButton(
                    text = stringResource(R.string.save),
                    onClick = { onConfirm(current.toArgb()) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
