package ch.mcfx.urs.auth

import ch.mcfx.urs.data.remote.RefreshPayload
import ch.mcfx.urs.data.remote.TokenResponseDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

private val PUBLIC_PATHS = setOf(
    "/",
    "/api/v1/joke",
    "/api/v1/login",
    "/api/v1/refresh",
    "/api/v1/set-initial-password",
)

/**
 * Handles a `401` on any protected route by attempting exactly one silent
 * refresh-and-retry "401-triggered refresh-and-retry" design) —
 * OkHttp calls this automatically whenever a response comes back 401.
 *
 * Runs synchronously on OkHttp's own dispatcher thread, not the caller's
 * coroutine — that's why it makes a raw, un-intercepted [refreshClient]
 * call here instead of going through [AuthRepository]/Retrofit's
 * suspend-based [ch.mcfx.urs.data.remote.UrsApi.refresh]. [refreshClient]
 * must not carry this authenticator (or [AuthInterceptor]) itself, or a
 * failing refresh call would recurse into this same method.
 *
 * Synchronized on [tokenStore] so that if several requests 401 around the
 * same time, only the first one actually calls `/api/v1/refresh` — the
 * backend's refresh tokens are single-use/rotating, so two
 * concurrent refresh attempts with the same token would make the second
 * one look like token reuse/theft and revoke every session. Everyone else
 * just waits for the lock and then reuses whatever token the first caller
 * already obtained.
 */
class AuthAuthenticator(
    private val tokenStore: AuthTokenStore,
    private val refreshClient: OkHttpClient,
    baseUrl: String,
) : Authenticator {

    private val json = Json { ignoreUnknownKeys = true }
    private val refreshUrl = baseUrl.toHttpUrl().newBuilder()
        .addPathSegments("api/v1/refresh")
        .build()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath in PUBLIC_PATHS) return null
        if (responseCount(response) >= 2) return null // already retried once — give up, don't loop forever

        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")

        synchronized(tokenStore) {
            val currentToken = tokenStore.accessToken
            if (currentToken != null && currentToken != failedToken) {
                // Another request already refreshed while we were waiting for the lock.
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            val refreshToken = tokenStore.refreshToken
            val newTokens = if (refreshToken != null) performRefresh(refreshToken) else null
            if (newTokens == null) {
                tokenStore.clear()
                return null
            }

            tokenStore.save(newTokens.accessToken, newTokens.refreshToken)
            return response.request.newBuilder()
                .header("Authorization", "Bearer ${newTokens.accessToken}")
                .build()
        }
    }

    private fun performRefresh(refreshToken: String): TokenResponseDto? {
        return try {
            val body = json.encodeToString(RefreshPayload(refreshToken))
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(refreshUrl).post(body).build()
            refreshClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val text = resp.body?.string() ?: return null
                json.decodeFromString(TokenResponseDto.serializer(), text)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
