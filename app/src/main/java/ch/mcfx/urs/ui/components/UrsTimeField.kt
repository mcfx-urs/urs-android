package ch.mcfx.urs.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing
import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * Bordered, tappable time-of-day field — same visual treatment as
 * [UrsDateField], opening a material3 [TimePicker] dialog on tap. Paired
 * with [UrsDateField] wherever a full instant is needed (e.g. a baking
 * plan's anchor time, ) rather than a combined date+time widget —
 * `material3` has no ready-made date+time picker, and splitting the two
 * keeps each field's own dialog simple. Same deliberate one-off exception
 * to this app's "no material3, every widget is a bespoke Urs* replacement"
 * rule as [UrsDateField] — a correct/accessible time-of-day picker isn't
 * worth reimplementing from scratch either.
 *
 * [value]/[onValueChange] use a plain 24h `HH:mm` string.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrsTimeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = UrsTheme.colors
    var showDialog by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Radius.row)
    val hasValue = value.isNotEmpty()

    Column(modifier = modifier) {
        // Always rendered, regardless of value — same reasoning as
        // UrsTextField's own label (see its doc comment): a label that only
        // appears once there's a value changes this field's height,
        // desyncing it from a same-row neighbor in a different fill state
        // (e.g. this field empty next to a filled UrsDateField).
        UrsText(
            text = label,
            style = UrsTheme.typography.caption,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(start = Spacing.m, bottom = Spacing.xs),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.onSurfaceMuted.copy(alpha = 0.3f), shape)
                .clickable { showDialog = true }
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrsText(
                    text = value.ifEmpty { label },
                    style = UrsTheme.typography.body,
                    color = if (hasValue) colors.onSurface else colors.onSurfaceMuted,
                    modifier = Modifier.weight(1f),
                )
                UrsIcon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = colors.onSurfaceMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }

    if (showDialog) {
        val initial = value.toLocalTimeOrNull() ?: LocalTime.now()
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(LocalTime.of(state.hour, state.minute).toString().take(5))
                    showDialog = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
            text = {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        containerColor = colors.surface,
                        clockDialColor = colors.background,
                        clockDialSelectedContentColor = colors.onAccent,
                        clockDialUnselectedContentColor = colors.onSurface,
                        selectorColor = colors.accent,
                        periodSelectorSelectedContainerColor = colors.accent,
                        periodSelectorUnselectedContainerColor = colors.surface,
                        periodSelectorSelectedContentColor = colors.onAccent,
                        periodSelectorUnselectedContentColor = colors.onSurface,
                        timeSelectorSelectedContainerColor = colors.accent,
                        timeSelectorUnselectedContainerColor = colors.background,
                        timeSelectorSelectedContentColor = colors.onAccent,
                        timeSelectorUnselectedContentColor = colors.onSurface,
                    ),
                )
            },
        )
    }
}

private fun String.toLocalTimeOrNull(): LocalTime? = try {
    LocalTime.parse(this)
} catch (_: DateTimeParseException) {
    null
}
