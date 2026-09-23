package ch.mcfx.urs.fuel

import ch.mcfx.urs.data.local.FillEntity

/**
 * A fuel container's current computed stock/weighted-average price per
 * liter, derived fresh from its own purchase fills and every transfer fill
 * that names it as source — never stored server-side or cached, same
 * "no backend aggregation" approach [FuelStats] already uses for
 * consumption statistics. See mcfx-urs/urs-backend#9/mcfx-urs/urs-android#87.
 */
object ContainerStock {

    data class State(val liters: Float, val averagePricePerLiter: Float)

    /**
     * Walks every fill either bought into [containerId] (vehicleId ==
     * containerId) or poured out of it (sourceVehicleId == containerId), in
     * chronological order. A purchase folds its price into the running
     * weighted average and grows stock; a withdrawal only shrinks stock,
     * the average is unaffected by it. Negative stock from measurement
     * rounding is allowed, not clamped — matches the backend's own
     * deliberate choice not to validate this.
     */
    fun current(containerId: String, fills: List<FillEntity>): State {
        val relevant = fills
            .filter { it.vehicleId == containerId || it.sourceVehicleId == containerId }
            .sortedBy { it.date }

        var stock = 0f
        var averagePrice = 0f
        for (fill in relevant) {
            val liters = fill.liters.toFloatOrNull() ?: continue
            if (fill.vehicleId == containerId) {
                val price = fill.pricePerLiter.toFloatOrNull() ?: continue
                val newStock = stock + liters
                averagePrice = if (newStock > 0f) (stock * averagePrice + liters * price) / newStock else price
                stock = newStock
            } else {
                stock -= liters
            }
        }
        return State(stock, averagePrice)
    }
}
