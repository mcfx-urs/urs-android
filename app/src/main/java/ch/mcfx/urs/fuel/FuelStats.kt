package ch.mcfx.urs.fuel

import ch.mcfx.urs.data.remote.FillDto
import java.time.LocalDate

// Consumption can only be computed across a *closed* full-to-full span —
// the liters added within a span equal the fuel consumed within it only
// because both ends started and ended full. A partial fill in the middle
// contributes its liters to the enclosing span but never anchors one on
// its own. Fills before the first full-tank fill, and a trailing run of
// partial fills after the last full-tank fill, produce no sample at all
// (truncated silently).
object FuelStats {

    // `date` is the span's *closing* full-tank fill's date — used both as
    // the sample's own timestamp and as the month a span is attributed to
    // in FuelStatsViewModel.monthlyStats() (a span crossing a month
    // boundary is attributed entirely to the month it closes in).
    data class ConsumptionSample(val date: LocalDate, val kmDriven: Float, val liters: Float) {
        val litersPer100Km: Float get() = liters / kmDriven * 100f
    }

    private data class ParsedFill(
        val vehicleId: String,
        val date: LocalDate,
        val odometer: Float,
        val liters: Float,
        val isFullTank: Boolean,
    )

    fun consumptionSamples(fills: List<FillDto>): List<ConsumptionSample> =
        fills.mapNotNull(::parse)
            .groupBy { it.vehicleId }
            .values
            .flatMap { vehicleFills -> buildSpans(vehicleFills.sortedBy { it.date }) }
            .filter { it.kmDriven > 0f }

    private fun buildSpans(sortedFills: List<ParsedFill>): List<ConsumptionSample> {
        val samples = mutableListOf<ConsumptionSample>()
        var anchorOdometer: Float? = null
        var spanLiters = 0f
        for (fill in sortedFills) {
            if (anchorOdometer == null) {
                if (fill.isFullTank) anchorOdometer = fill.odometer
                continue
            }
            spanLiters += fill.liters
            if (fill.isFullTank) {
                samples += ConsumptionSample(
                    date = fill.date,
                    kmDriven = fill.odometer - anchorOdometer,
                    liters = spanLiters,
                )
                anchorOdometer = fill.odometer
                spanLiters = 0f
            }
        }
        return samples
    }

    // Ratio-of-sums (total liters / total km across all closed spans), not
    // an average of per-span ratios — a naive average would let a short,
    // high-consumption span skew the result disproportionately relative to
    // how much fuel it actually used.
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
        return ParsedFill(fill.vehicleId, date, odometer, liters, isFullTank = fill.isFullTank != "0")
    }
}
