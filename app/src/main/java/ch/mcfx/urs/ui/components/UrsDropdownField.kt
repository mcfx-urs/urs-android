package ch.mcfx.urs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * Read-only, tappable selector field — replacement for `material3
 * .ExposedDropdownMenuBox` + `OutlinedTextField(readOnly = true)` +
 * `ExposedDropdownMenu` + `DropdownMenuItem` combined. Visually matches
 * [UrsTextField] (same border/label-above-once-there's-a-value treatment),
 * but is itself clickable instead of accepting keyboard input: tapping the
 * field toggles a [Popup]-based option list, tapping an option selects it
 * and closes the popup, tapping anywhere outside closes it without a
 * selection (the [Popup] default's `dismissOnClickOutside` covers that last
 * part for free).
 */
@Composable
fun <T> UrsDropdownField(
    label: String,
    options: List<T>,
    selectedLabel: String?,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UrsTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val labelAbove = expanded || selectedLabel != null
    val borderColor = if (expanded) colors.accent else colors.onSurfaceMuted.copy(alpha = 0.3f)
    val shape = RoundedCornerShape(Radius.row)

    val density = LocalDensity.current
    var fieldWidthPx by remember { mutableIntStateOf(0) }
    val fieldWidthDp = with(density) { fieldWidthPx.toDp() }

    Column(modifier = modifier) {
        if (labelAbove) {
            UrsText(
                text = label,
                style = UrsTheme.typography.caption,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(start = Spacing.m, bottom = Spacing.xs),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { fieldWidthPx = it.size.width }
                .border(1.dp, borderColor, shape)
                .clickable { expanded = !expanded }
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (labelAbove && selectedLabel != null) {
                    UrsText(
                        text = selectedLabel,
                        style = UrsTheme.typography.body,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    UrsText(
                        text = label,
                        style = UrsTheme.typography.body,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier.weight(1f),
                    )
                }
                UrsIcon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.onSurfaceMuted,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (expanded) {
                DropdownOptionsPopup(
                    width = fieldWidthDp,
                    onDismissRequest = { expanded = false },
                ) {
                    options.forEach { option ->
                        UrsText(
                            text = optionLabel(option),
                            style = UrsTheme.typography.body,
                            color = colors.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(option)
                                    expanded = false
                                }
                                .padding(horizontal = Spacing.l, vertical = Spacing.m),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The popup's own elevated surface, matching [UrsCard]'s exact shadow/
 * border/background treatment (radius: [Radius.row], to read as "one more
 * row-like surface" rather than a bare list floating in space) but built
 * directly rather than via [UrsCard], since [UrsCard] takes a fixed
 * [androidx.compose.foundation.layout.PaddingValues] rather than the
 * per-row padding each option here needs for its own tap target.
 *
 * Capped to a fraction of the screen height and made vertically scrollable —
 * without this, an option list longer than the available space (e.g. the
 * bundled currency list) would overflow past the screen edge with no way to
 * reach the remaining entries.
 */
@Composable
private fun DropdownOptionsPopup(
    width: Dp,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = UrsTheme.colors
    val darkTheme = colors.border.alpha > 0f
    val shape = RoundedCornerShape(Radius.row)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.4f).dp

    Popup(
        popupPositionProvider = rememberDropdownPositionProvider(),
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .width(width)
                .heightIn(max = maxHeight)
                .shadow(
                    elevation = 10.dp,
                    shape = shape,
                    ambientColor = colors.shadowColor.copy(alpha = colors.shadowAlpha),
                    spotColor = colors.shadowColor.copy(alpha = colors.shadowAlpha),
                )
                .clip(shape)
                .background(colors.surface)
                .then(if (darkTheme) Modifier.border(1.dp, colors.border, shape) else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(vertical = Spacing.xs),
            content = content,
        )
    }
}

/**
 * [PopupPositionProvider] anchoring the option list directly below the
 * field, flipping above it instead when there isn't enough room below.
 *
 * `windowSize` as reported to [PopupPositionProvider.calculatePosition]
 * spans the full display, status bar and navigation bar included — this app
 * draws edge-to-edge (`MainActivity`'s `enableEdgeToEdge()`), so nothing
 * clips the popup out of that region automatically the way a non-edge-to-
 * edge app's window would. Without accounting for that, a field near the
 * top or bottom of the screen could end up opening a popup that's partly
 * hidden under the status bar or the gesture/nav bar. [topInsetPx]/
 * [bottomInsetPx] carve that space back out: they bound both the "is there
 * room below" decision and a final clamp, so the popup always lands fully
 * inside the safe area regardless of where its field sits.
 */
private class DropdownPositionProvider(
    private val topInsetPx: Int,
    private val bottomInsetPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val safeTop = topInsetPx
        val safeBottom = windowSize.height - bottomInsetPx
        val spaceBelow = safeBottom - anchorBounds.bottom
        val spaceAbove = anchorBounds.top - safeTop

        val y = if (popupContentSize.height <= spaceBelow || spaceBelow >= spaceAbove) {
            anchorBounds.bottom
        } else {
            anchorBounds.top - popupContentSize.height
        }
        val maxY = (safeBottom - popupContentSize.height).coerceAtLeast(safeTop)

        return IntOffset(x = anchorBounds.left, y = y.coerceIn(safeTop, maxY))
    }
}

@Composable
private fun rememberDropdownPositionProvider(): PopupPositionProvider {
    val density: Density = LocalDensity.current
    val topInsetPx = WindowInsets.statusBars.getTop(density)
    val bottomInsetPx = WindowInsets.navigationBars.getBottom(density)
    return remember(topInsetPx, bottomInsetPx) { DropdownPositionProvider(topInsetPx, bottomInsetPx) }
}
