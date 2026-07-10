package ch.mcfx.urs.data

import ch.mcfx.urs.data.remote.CarDto
import ch.mcfx.urs.data.remote.FillDto
import ch.mcfx.urs.data.remote.FillPayload
import ch.mcfx.urs.data.remote.FillingStationDto
import ch.mcfx.urs.data.remote.FillingStationPayload
import ch.mcfx.urs.data.remote.UrsApi
import kotlinx.serialization.SerializationException

class FuelRepository(private val api: UrsApi) {

    suspend fun getCars(): List<CarDto> = api.getCars()

    suspend fun getStations(): List<FillingStationDto> = emptyAsNull { api.getFillingStations() }

    suspend fun createStation(name: String, address: String = "") {
        api.createFillingStation(FillingStationPayload(name = name, address = address))
    }

    suspend fun getFills(): List<FillDto> =
        emptyAsNull { api.getFills() }.sortedByDescending { it.date }

    suspend fun getLastOdometer(carId: String): String? =
        emptyAsNull { api.getOdometer(carId) }
            .maxByOrNull { it.mileage.toFloatOrNull() ?: 0f }
            ?.mileage

    // The payload mirrors what the web frontend sends (see urs-frontend
    // pages/fuel/fill.vue): the client computes "driven" and the incremented
    // station counter, and the date carries a hardcoded midnight time.
    suspend fun createFill(
        car: CarDto,
        station: FillingStationDto,
        date: String,
        odometer: String,
        pricePerLiter: String,
        liters: String,
        lastOdometer: String?,
    ) {
        val driven = lastOdometer
            ?.let { formatKm((odometer.toFloatOrNull() ?: 0f) - (it.toFloatOrNull() ?: 0f)) }
            ?: "0"

        api.createFill(
            FillPayload(
                date = "$date 00:00:00",
                carId = car.id,
                stationId = station.id,
                fuelId = car.fuelId,
                pricePerLiter = pricePerLiter,
                liters = liters,
                odometer = odometer,
                driven = driven,
                stationCounter = ((station.counter.toIntOrNull() ?: 0) + 1).toString(),
            )
        )
    }

    // The backend encodes empty result sets as JSON `null` instead of `[]`.
    private suspend fun <T> emptyAsNull(call: suspend () -> List<T>): List<T> =
        try {
            call()
        } catch (_: SerializationException) {
            emptyList()
        }

    private fun formatKm(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else value.toString()
}
