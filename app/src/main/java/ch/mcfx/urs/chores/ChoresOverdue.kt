package ch.mcfx.urs.chores

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A type is overdue (GitHub issue #29) once it has an expected interval
 * and either has never been logged, or the last event is more than that
 * many days back. Types without an interval are never overdue.
 */
fun isChoreOverdue(expectedIntervalDays: Int?, lastDone: LocalDate?, today: LocalDate = LocalDate.now()): Boolean {
    val interval = expectedIntervalDays ?: return false
    if (interval <= 0) return false
    if (lastDone == null) return true
    return ChronoUnit.DAYS.between(lastDone, today) > interval
}
