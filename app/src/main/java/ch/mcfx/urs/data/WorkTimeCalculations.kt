package ch.mcfx.urs.data

import ch.mcfx.urs.data.local.WorkTimeEntryWithBreaks
import java.time.DayOfWeek
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

/** Number of Monday–Friday calendar days in a given month — the exact "how many days could theoretically be worked" count. */
fun possibleWeekdaysInMonth(year: Int, month: Int): Int {
    val yearMonth = YearMonth.of(year, month)
    return (1..yearMonth.lengthOfMonth()).count { day ->
        val dayOfWeek = yearMonth.atDay(day).dayOfWeek
        dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY
    }
}

data class MonthlySummary(
    val actualHours: Float,
    val overUndertimeHours: Float?,
    val earnings: Float?,
    /** Null while [year]/[month] (see [computeMonthlySummary]) is the current, still-active month. */
    val percentOfContractSoll: Float?,
)

/**
 * Aggregates a calendar month's entries into two deliberately independent
 * measures:
 *
 * - **Plus/Minus** (`overUndertimeHours`) is self-referential: its Soll is
 *   `daysWorked * targetHoursPerDay`, where `daysWorked` is either the
 *   manual [overrideDaysWorked] or a raw count of entries that month.
 *   Answers "on the days I logged, did I hit my daily target?" — always
 *   meaningful, including for the currently active month, since it never
 *   expects hours for days that haven't happened yet (no "always behind
 *   mid-month" distortion).
 * - **percentOfContractSoll** answers a different question — "of my full
 *   month's contractual hour obligation, what fraction did I actually
 *   work?" — using the employment percentage against every weekday in the
 *   month (`possibleWeekdaysInMonth(year, month) * employmentPercent/100 *
 *   targetHoursPerDay`), entirely independent of daysWorked/the override.
 *   Only meaningful once a month has fully ended (its weekday count is
 *   otherwise the full month's, so it would always read as "behind" for
 *   the active month purely because the month isn't over yet) — callers
 *   pass [isCurrentMonth] and get `null` back for the active month.
 */
fun computeMonthlySummary(
    entries: List<WorkTimeEntryWithBreaks>,
    year: Int,
    month: Int,
    employmentPercent: String?,
    targetHoursPerDay: String?,
    hourlyWage: String?,
    overrideDaysWorked: String?,
    isCurrentMonth: Boolean,
): MonthlySummary {
    val monthPrefix = "%04d-%02d".format(year, month)
    val monthEntries = entries.filter { it.entry.date.startsWith(monthPrefix) }
    val actualHours = monthEntries.sumOf { (it.dailyHoursWorked() ?: 0f).toDouble() }.toFloat()

    val dailyTarget = targetHoursPerDay?.toFloatOrNull()
    val daysWorked = overrideDaysWorked?.toFloatOrNull() ?: monthEntries.size.toFloat()
    val plusMinusSoll = dailyTarget?.let { daysWorked * it }

    val percentOfContractSoll = if (isCurrentMonth) {
        null
    } else {
        val percent = employmentPercent?.toFloatOrNull()
        val contractSollHours = if (percent != null && dailyTarget != null) {
            possibleWeekdaysInMonth(year, month) * (percent / 100f) * dailyTarget
        } else {
            null
        }
        contractSollHours?.takeIf { it != 0f }?.let { actualHours / it * 100f }
    }

    return MonthlySummary(
        actualHours = actualHours,
        overUndertimeHours = plusMinusSoll?.let { actualHours - it },
        earnings = hourlyWage?.toFloatOrNull()?.let { actualHours * it },
        percentOfContractSoll = percentOfContractSoll,
    )
}
