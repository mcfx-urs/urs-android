package ch.mcfx.urs.auth

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

private val json = Json { ignoreUnknownKeys = true }

/**
 * Reads the `sub` claim (the backend's `user_id`) out of a JWT access
 * token's payload, without verifying its signature — the token was already
 * issued by (and every real request is re-validated by) the backend, so
 * this is purely a client-side convenience to avoid a second round-trip or
 * a separate field on the login response, not a security boundary. A JWT
 * payload is base64url-encoded JSON, not encrypted, so reading it requires
 * no secret.
 */
object JwtDecoder {

    fun subject(token: String): String? {
        val parts = token.split(".")
        if (parts.size != 3) return null
        return try {
            val payloadJson = String(
                Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
                Charsets.UTF_8,
            )
            json.parseToJsonElement(payloadJson).jsonObjectOrNull()
                ?.get("sub")?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }
}

private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull() =
    this as? kotlinx.serialization.json.JsonObject
