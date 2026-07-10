package ch.mcfx.urs.fuel

import ch.mcfx.urs.data.remote.FillDto
import java.time.LocalDate

// Consumption can only be computed between two consecutive fill-ups of the
// same car (liters used to cover the distance since the previous fill), so a
// car's earliest recorded fill never yields a sample on its own.
object FuelStats {

    data class ConsumptionSample(val date: LocalDate, val kmDriven: Float, val liters: Float) {
        val litersPer100Km: Float get() = liters / kmDriven * 100f
    }

    private data class ParsedFill(val carId: String, val date: LocalDate, val odometer: Float, val liters: Float)

    fun consumptionSamples(fills: List<FillDto>): List<ConsumptionSample> =
        fills.mapNotNull(::parse)
            .groupBy { it.carId }
            .values
            .flatMap { carFills ->
                carFills.sortedBy { it.date }.zipWithNext { previous, current ->
                    ConsumptionSample(
                        date = current.date,
                        kmDriven = current.odometer - previous.odometer,
                        liters = current.liters,
                    )
                }
            }
            .filter { it.kmDriven > 0f }

    // Ratio-of-sums (total liters / total km driven across all qualifying
    // fill-pairs), not an average of per-fill ratios — a naive average would
    // let a handful of short, high-consumption fill-to-fill gaps skew the
    // result disproportionately relative to how much fuel they actually used.
    fun averageConsumptionL100Km(fills: List<FillDto>, since: LocalDate? = null): Float? {
        val samples = consumptionSamples(fills).filter { since == null || !it.date.isBefore(since) }
        if (samples.isEmpty()) return null
        val totalKm = samples.sumOf { it.kmDriven.toDouble() }
        val totalLiters = samples.sumOf { it.liters.toDouble() }
        return if (totalKm > 0.0) (totalLiters / totalKm * 100.0).toFloat() else null
    }

    fun parseDate(rawFillDate: String): LocalDate? =
        rawFillDate.substringBefore(' ').let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun parse(fill: FillDto): ParsedFill? {
        val date = parseDate(fill.date) ?: return null
        val odometer = fill.odometer.toFloatOrNull() ?: return null
        val liters = fill.liters.toFloatOrNull() ?: return null
        return ParsedFill(fill.carId, date, odometer, liters)
    }
}
