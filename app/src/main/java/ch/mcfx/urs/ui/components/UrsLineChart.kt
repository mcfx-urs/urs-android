package ch.mcfx.urs.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import java.util.Locale

/**
 * Minimal line chart — a plain Compose [Canvas] polyline over [values], no
 * charting library dependency (this app has none, and building/maintaining
 * one just for two small stat trends isn't worth it). Deliberately spare:
 * a line plus its min/max value labels, no gridlines/axis ticks/tooltips —
 * enough to see a trend at a glance, not a full analytics widget.
 */
@Composable
fun UrsLineChart(
    values: List<Float>,
    label: String,
    modifier: Modifier = Modifier,
    lineColor: Color = UrsTheme.colors.accent,
) {
    val colors = UrsTheme.colors
    Column(modifier = modifier) {
        UrsText(label, style = UrsTheme.typography.cardTitle, color = colors.accent)
        Spacer(Modifier.height(Spacing.xs))
        if (values.size < 2) {
            UrsText(text = "–", style = UrsTheme.typography.body, color = colors.onSurfaceMuted)
            return@Column
        }

        val maxValue = values.max()
        val minValue = values.min()
        val range = (maxValue - minValue).let { if (it == 0f) 1f else it }

        UrsText(
            String.format(Locale.US, "%.1f", maxValue),
            style = UrsTheme.typography.caption,
            color = colors.onSurfaceMuted,
        )
        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            val stepX = if (values.size > 1) size.width / (values.size - 1) else 0f
            val points = values.mapIndexed { index, value ->
                Offset(x = index * stepX, y = size.height - ((value - minValue) / range) * size.height)
            }
            for (i in 0 until points.size - 1) {
                drawLine(color = lineColor, start = points[i], end = points[i + 1], strokeWidth = 4f, cap = StrokeCap.Round)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            UrsText(
                String.format(Locale.US, "%.1f", minValue),
                style = UrsTheme.typography.caption,
                color = colors.onSurfaceMuted,
            )
        }
    }
}

/** One named, colored line in a [UrsMultiLineChart]. */
data class UrsChartSeries(val label: String, val values: List<Float>, val color: Color)

/**
 * Same minimal-Canvas approach as [UrsLineChart], but overlaying several
 * named series on one shared scale with a small color-coded legend below —
 * e.g. petrol vs. diesel price trends. Series aren't date-aligned to each
 * other: each is plotted by its own index position along x, not a shared
 * timeline, since reconciling different fuel types' own price-change dates
 * isn't worth the complexity for this simple a chart.
 */
@Composable
fun UrsMultiLineChart(series: List<UrsChartSeries>, title: String, modifier: Modifier = Modifier) {
    val colors = UrsTheme.colors
    Column(modifier = modifier) {
        UrsText(title, style = UrsTheme.typography.cardTitle, color = colors.accent)
        Spacer(Modifier.height(Spacing.xs))

        val plottable = series.filter { it.values.size >= 2 }
        if (plottable.isEmpty()) {
            UrsText(text = "–", style = UrsTheme.typography.body, color = colors.onSurfaceMuted)
            return@Column
        }

        val allValues = plottable.flatMap { it.values }
        val maxValue = allValues.max()
        val minValue = allValues.min()
        val range = (maxValue - minValue).let { if (it == 0f) 1f else it }

        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            plottable.forEach { s ->
                val stepX = size.width / (s.values.size - 1)
                val points = s.values.mapIndexed { index, value ->
                    Offset(x = index * stepX, y = size.height - ((value - minValue) / range) * size.height)
                }
                for (i in 0 until points.size - 1) {
                    drawLine(color = s.color, start = points[i], end = points[i + 1], strokeWidth = 4f, cap = StrokeCap.Round)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            plottable.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Box(Modifier.size(8.dp).background(s.color, CircleShape))
                    UrsText(s.label, style = UrsTheme.typography.caption, color = colors.onSurfaceMuted)
                }
            }
        }
    }
}
