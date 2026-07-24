package ch.mcfx.urs.vehicle

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Days until the next MFK (Swiss periodic vehicle inspection, due every 2
 * years) is due, computed from the last inspection date — negative once
 * overdue. No due-date is stored server-side; this is a pure client-side
 * derivation, recomputed on every display rather than cached.
 */
fun daysUntilNextMfk(lastMfkDate: String): Long? {
    val date = try {
        LocalDate.parse(lastMfkDate)
    } catch (_: DateTimeParseException) {
        return null
    }
    return ChronoUnit.DAYS.between(LocalDate.now(), date.plusYears(2))
}
