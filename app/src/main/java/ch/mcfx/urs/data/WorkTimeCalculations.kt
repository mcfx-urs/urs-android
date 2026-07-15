package ch.mcfx.urs.data

import ch.mcfx.urs.data.local.WorkTimeEntryWithBreaks
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun rangeHours(start: String, end: String): Float? {
    val t1 = runCatching { LocalTime.parse(start, TimeFormatter) }.getOrNull() ?: return null
    val t2 = runCatching { LocalTime.parse(end, TimeFormatter) }.getOrNull() ?: return null
    return java.time.Duration.between(t1, t2).toMinutes() / 60f
}

private fun WorkTimeEntryWithBreaks.dailyHoursWorked(): Float? =
    rangeHours(entry.workStart, entry.workEnd)?.let { workSpan ->
        breaks.fold(workSpan) { acc, b -> acc - (rangeHours(b.startTime, b.endTime) ?: 0f) }
    }

/**
 * Client-side mirror of urs-backend's `computeDailyTotals` — kept so a
 * not-yet-synced entry can show its total immediately, without waiting for
 * a server round-trip. Both sides must be changed together if the formula
 * ever changes.
 */
data class WorkTimeTotals(val dailyTotalHours: Float?, val overUndertimeHours: Float?)

fun WorkTimeEntryWithBreaks.computeTotals(userDefaultTargetHours: String?): WorkTimeTotals {
    val total = dailyHoursWorked() ?: return WorkTimeTotals(null, null)
    val target = entry.targetDailyHours.toFloatOrNull() ?: userDefaultTargetHours?.toFloatOrNull()
    val overUndertime = target?.let { total - it }
    return WorkTimeTotals(total, overUndertime)
}

data class MonthlySummary(
    val actualHours: Float,
    val targetHours: Float?,
    val overUndertimeHours: Float?,
    val earnings: Float?,
)

/**
 * Aggregates a calendar month's entries into hours worked, the expected
 * target for that month, the resulting over-/undertime, and earnings.
 *
 * [overrideTargetHours] (a manually-set value correcting for vacation,
 * holidays, or sick leave — none of which this app tracks as data) always
 * wins when present. Otherwise the target is derived from the employment
 * percentage: a full-time week is 5 days, so `employmentPercent/100 * 5 *
 * targetHoursPerDay` gives the weekly target, scaled to the month via
 * `daysInMonth / 7`. This is an average approximation (independent of
 * which specific weekdays are actually worked), not a literal weekday
 * count — matches urs-backend's own lack of a "which days" concept.
 */
fun computeMonthlySummary(
    entries: List<WorkTimeEntryWithBreaks>,
    year: Int,
    month: Int,
    employmentPercent: String?,
    targetHoursPerDay: String?,
    hourlyWage: String?,
    overrideTargetHours: String?,
): MonthlySummary {
    val monthPrefix = "%04d-%02d".format(year, month)
    val actualHours = entries
        .filter { it.entry.date.startsWith(monthPrefix) }
        .sumOf { (it.dailyHoursWorked() ?: 0f).toDouble() }
        .toFloat()

    val targetHours = overrideTargetHours?.toFloatOrNull() ?: run {
        val percent = employmentPercent?.toFloatOrNull() ?: return@run null
        val dailyTarget = targetHoursPerDay?.toFloatOrNull() ?: return@run null
        val daysInMonth = YearMonth.of(year, month).lengthOfMonth()
        (percent / 100f) * 5f * dailyTarget * (daysInMonth / 7f)
    }

    return MonthlySummary(
        actualHours = actualHours,
        targetHours = targetHours,
        overUndertimeHours = targetHours?.let { actualHours - it },
        earnings = hourlyWage?.toFloatOrNull()?.let { actualHours * it },
    )
}
