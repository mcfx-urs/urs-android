package ch.mcfx.urs.data

import ch.mcfx.urs.data.local.WorkTimeEntryWithBreaks
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun rangeHours(start: String, end: String): Float? {
    val t1 = runCatching { LocalTime.parse(start, TimeFormatter) }.getOrNull() ?: return null
    val t2 = runCatching { LocalTime.parse(end, TimeFormatter) }.getOrNull() ?: return null
    return java.time.Duration.between(t1, t2).toMinutes() / 60f
}

/**
 * Client-side mirror of urs-backend's `computeDailyTotals` — kept so a
 * not-yet-synced entry can show its total immediately, without waiting for
 * a server round-trip. Both sides must be changed together if the formula
 * ever changes.
 */
data class WorkTimeTotals(val dailyTotalHours: Float?, val overUndertimeHours: Float?)

fun WorkTimeEntryWithBreaks.computeTotals(userDefaultTargetHours: String?): WorkTimeTotals {
    val total = rangeHours(entry.workStart, entry.workEnd)?.let { workSpan ->
        breaks.fold(workSpan) { acc, b -> acc - (rangeHours(b.startTime, b.endTime) ?: 0f) }
    } ?: return WorkTimeTotals(null, null)

    val target = entry.targetDailyHours.toFloatOrNull() ?: userDefaultTargetHours?.toFloatOrNull()
    val overUndertime = target?.let { total - it }
    return WorkTimeTotals(total, overUndertime)
}
