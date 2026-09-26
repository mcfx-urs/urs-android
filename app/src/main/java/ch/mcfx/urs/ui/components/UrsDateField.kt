package ch.mcfx.urs.ui.components

import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
    /**
     * Bump to a new value from outside (e.g. an incrementing counter) to open
     * this field's dialog programmatically — e.g. chaining straight into the
     * next field once its predecessor was just confirmed (see the Life Map
     * custom-range sheet, GitHub issue #76). `0`, the default, never opens
     * anything on its own; only a *change* does, so this is safe to leave
     * wired permanently rather than resetting it after each use.
     */
    openSignal: Int = 0,
) {
    val colors = UrsTheme.colors
    var showDialog by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Radius.row)
    val hasValue = value.isNotEmpty()

    LaunchedEffect(openSignal) {
        if (openSignal != 0 && enabled) showDialog = true
    }

    Column(modifier = modifier) {
        // Always rendered, regardless of value — same reasoning as
        // UrsTextField's own label (see its doc comment): a label that only
        // appears once there's a value changes this field's height,
        // desyncing it from a same-row neighbor in a different fill state
        // (e.g. an empty UrsTimeField next to a filled UrsDateField).
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
        // Material3's DatePicker derives its calendar model's locale (and
        // with it, first-day-of-week) from LocalConfiguration.current, not
        // from this app's own display-language override (GitHub issue #98)
        // — setAppLanguage() sets a bare, region-less language tag via
        // LocaleManager, which WeekFields.of(locale) then defaults to Sunday
        // for. Resources.getSystem() is untouched by any per-app override
        // and always reflects the device's real region, so the fix is to
        // provide a Configuration copy — everything else (density, screen
        // size, ...) carried over unchanged — with only its locale swapped
        // to that real one, scoped to just this dialog's own composition.
        val currentConfiguration = LocalConfiguration.current
        val systemLocale = Resources.getSystem().configuration.locales[0]
        val pickerConfiguration = remember(currentConfiguration, systemLocale) {
            Configuration(currentConfiguration).apply { setLocales(LocaleList(systemLocale)) }
        }
        CompositionLocalProvider(LocalConfiguration provides pickerConfiguration) {
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
}

private fun String.toEpochMillisOrNull(): Long? = try {
    LocalDate.parse(this).toEpochDay() * MILLIS_PER_DAY
} catch (_: DateTimeParseException) {
    null
}
