package ch.mcfx.urs.data

import ch.mcfx.urs.data.remote.BeerLogDto
import ch.mcfx.urs.data.remote.BeerLogPayload
import ch.mcfx.urs.data.remote.UrsApi
import kotlinx.serialization.SerializationException

class BeerRepository(private val api: UrsApi) {

    suspend fun getEntries(): List<BeerLogDto> = emptyAsNull { api.getBeerLog() }

    suspend fun logBeer(amountMl: Int, date: String) {
        api.createBeerLog(BeerLogPayload(amountMl = amountMl.toString(), date = date))
    }

    suspend fun deleteEntry(id: String) {
        api.deleteBeerLog(id)
    }

    // The backend encodes empty result sets as JSON `null` instead of `[]`.
    private suspend fun <T> emptyAsNull(call: suspend () -> List<T>): List<T> =
        try {
            call()
        } catch (_: SerializationException) {
            emptyList()
        }
}
