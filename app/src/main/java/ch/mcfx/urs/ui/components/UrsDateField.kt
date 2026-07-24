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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import java.time.LocalDate
import java.time.format.DateTimeParseException
import androidx.compose.material3.rememberDatePickerState

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Bordered, tappable date field — visually matches [UrsDropdownField]/
 * [UrsTextField] (same border/label-above-once-there's-a-value treatment),
 * but opens a material3 [DatePickerDialog] on tap instead of taking direct
 * keyboard input or a custom option list.
 *
 * The one deliberate exception to this app's "no material3, every widget is
 * a bespoke Urs* replacement" rule (see [UrsTextField]'s own doc comment) —
 * a correct/accessible calendar-grid picker isn't worth reimplementing from
 * scratch. [DatePickerDialog]'s own built-in mode-toggle icon already covers
 * "an editable date field the user doesn't have to guess the format for, but
 * can also just type into" directly: tapping it swaps the calendar grid for
 * a plain `MM/DD/YYYY`-shaped text field with built-in validation, no
 * separate fallback needed. Reskinned via [DatePickerDefaults.colors] to fit
 * this app's palette rather than pulling in a whole parallel material3 theme.
 *
 * [value]/[onValueChange] use the same ISO `yyyy-MM-dd` string convention as
 * every other date field in this app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrsDateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    /** `false` disables opening the dialog at all — e.g. a date the backend won't let this edit move. */
    enabled: Boolean = true,
) {
    val colors = UrsTheme.colors
    var showDialog by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Radius.row)
    val hasValue = value.isNotEmpty()

    Column(modifier = modifier) {
        if (hasValue) {
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
                .border(1.dp, colors.onSurfaceMuted.copy(alpha = 0.3f), shape)
                .then(if (enabled) Modifier.clickable { showDialog = true } else Modifier)
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
                    imageVector = Icons.Filled.DateRange,
                    contentDescription = null,
                    tint = colors.onSurfaceMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }

    if (showDialog) {
        val state = rememberDatePickerState(initialSelectedDateMillis = value.toEpochMillisOrNull())
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onValueChange(LocalDate.ofEpochDay(millis / MILLIS_PER_DAY).toString())
                    }
                    showDialog = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
            colors = DatePickerDefaults.colors(
                containerColor = colors.surface,
                titleContentColor = colors.onSurfaceMuted,
                headlineContentColor = colors.onSurface,
                weekdayContentColor = colors.onSurfaceMuted,
                dayContentColor = colors.onSurface,
                selectedDayContentColor = colors.onAccent,
                selectedDayContainerColor = colors.accent,
                todayContentColor = colors.accent,
                todayDateBorderColor = colors.accent,
                yearContentColor = colors.onSurface,
                currentYearContentColor = colors.accent,
                selectedYearContentColor = colors.onAccent,
                selectedYearContainerColor = colors.accent,
            ),
        ) {
            DatePicker(state = state)
        }
    }
}

private fun String.toEpochMillisOrNull(): Long? = try {
    LocalDate.parse(this).toEpochDay() * MILLIS_PER_DAY
} catch (_: DateTimeParseException) {
    null
}
