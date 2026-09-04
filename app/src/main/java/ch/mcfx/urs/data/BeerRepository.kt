package ch.mcfx.urs.data

import ch.mcfx.urs.data.local.OutboxBeerLogCreatePayload
import ch.mcfx.urs.data.local.OutboxDao
import ch.mcfx.urs.data.local.OutboxMutationEntity
import ch.mcfx.urs.data.remote.BeerLogDateUpdatePayload
import ch.mcfx.urs.data.remote.BeerLogDto
import ch.mcfx.urs.data.remote.UrsApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BeerRepository(
    private val api: UrsApi,
    private val outboxDao: OutboxDao,
    private val json: Json,
) {

    suspend fun getEntries(): List<BeerLogDto> = emptyAsNull { api.getBeerLog() }

    // Offline-first: queued to the outbox and delivered by SyncManager, so a
    // beer logged from the watch relay or with no connectivity is never lost
    // and never depends on the VPN/home-network gate. There is no local Room
    // row for beer logs (the list still reads from the backend), so there is
    // nothing to reconcile — the queued row just POSTs on the next sync.
    suspend fun logBeer(amountMl: Int, date: String) {
        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_BEER_LOG,
                payloadJson = json.encodeToString(OutboxBeerLogCreatePayload(amountMl, date)),
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteEntry(id: String) {
        api.deleteBeerLog(id)
    }

    // Direct call, same as deleteEntry — an entry being edited already has a
    // server-side id, so unlike logBeer (which queues brand-new entries to
    // the outbox) there is nothing to reconcile against a local cache here.
    suspend fun updateEntryDate(id: String, date: String) {
        api.updateBeerLog(id, BeerLogDateUpdatePayload(date))
    }

    // The backend encodes empty result sets as JSON `null` instead of `[]`.
    private suspend fun <T> emptyAsNull(call: suspend () -> List<T>): List<T> =
        try {
            call()
        } catch (_: SerializationException) {
            emptyList()
        }
}
