package ch.mcfx.urs.beer

import ch.mcfx.urs.data.remote.BeerLogDto
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object BeerStats {

    // Commonly cited average bathtub capacity is ~150-200L filled, with
    // real usage running 10-20% below that (water displacement, not
    // filling to the overflow drain) — 150L is a reasonable round number
    // for a fun-fact conversion, not a precise figure; adjust if it ends
    // up reading oddly once there's real data.
    private const val BATHTUB_LITERS = 150.0

    val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    data class Bucket(val label: String, val count: Int)

    sealed class SinceLast {
        data object JustNow : SinceLast()
        data class Minutes(val n: Long) : SinceLast()
        data class Hours(val n: Long) : SinceLast()
        data class Days(val n: Long) : SinceLast()
    }

    fun parseDateTime(raw: String): LocalDateTime? = runCatching { LocalDateTime.parse(raw, DATE_FORMAT) }.getOrNull()

    fun dailyCounts(entries: List<BeerLogDto>, today: LocalDate = LocalDate.now(), days: Int = 30): List<Bucket> {
        val countsByDay = entries.groupingBy { parseDateTime(it.date)?.toLocalDate() }.eachCount()
        val start = today.minusDays((days - 1).toLong())
        return (0 until days).map { offset ->
            val day = start.plusDays(offset.toLong())
            Bucket(label = day.dayOfMonth.toString(), count = countsByDay[day] ?: 0)
        }
    }

    fun monthlyCounts(entries: List<BeerLogDto>, today: LocalDate = LocalDate.now(), months: Int = 12): List<Bucket> {
        val countsByMonth = entries.groupingBy { parseDateTime(it.date)?.let { d -> YearMonth.from(d) } }.eachCount()
        val start = YearMonth.from(today).minusMonths((months - 1).toLong())
        return (0 until months).map { offset ->
            val month = start.plusMonths(offset.toLong())
            Bucket(label = month.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, count = countsByMonth[month] ?: 0)
        }
    }

    // Null if there's no logged entry at all — HomeScreen's subtitle simply
    // omits itself in that case, same convention as fuelAvgConsumptionL100Km.
    // Below one calendar day old, elapsed time is computed from the full
    // timestamp (not just the date) so a beer logged minutes ago doesn't
    // read as "0 days ago".
    fun sinceLast(entries: List<BeerLogDto>, now: LocalDateTime = LocalDateTime.now()): SinceLast? {
        val last = entries.mapNotNull { parseDateTime(it.date) }.maxOrNull() ?: return null
        val dayDiff = ChronoUnit.DAYS.between(last.toLocalDate(), now.toLocalDate())
        if (dayDiff > 0) return SinceLast.Days(dayDiff)
        val minutes = ChronoUnit.MINUTES.between(last, now)
        return when {
            minutes < 10 -> SinceLast.JustNow
            minutes < 60 -> SinceLast.Minutes(minutes)
            else -> SinceLast.Hours(ChronoUnit.HOURS.between(last, now))
        }
    }

    fun totalLitersThisYear(entries: List<BeerLogDto>, year: Int = LocalDate.now().year): Double =
        entries
            .filter { parseDateTime(it.date)?.year == year }
            .sumOf { (it.amountMl.toIntOrNull() ?: 0) }
            .div(1000.0)

    fun bathtubs(liters: Double): Double = liters / BATHTUB_LITERS
}
