package ch.mcfx.urs.data.local

import kotlinx.serialization.Serializable

// There is no local Room row for beer logs (the list still reads straight
// from the backend), so nothing is reconciled at replay time — the queued
// row just POSTs and is dropped on success.
@Serializable
data class OutboxBeerLogCreatePayload(
    val amountMl: Int,
    val date: String,
)
