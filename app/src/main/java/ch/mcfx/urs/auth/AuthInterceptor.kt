package ch.mcfx.urs.auth

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches `Authorization: Bearer <access token>` to every outgoing
 * request that has one stored. Harmless to attach it to the public routes
 * too (`/`, `/api/v1/joke`, `/api/v1/login`, `/api/v1/refresh`,
 * `/api/v1/set-initial-password`) — the backend's auth middleware never
 * runs on those, so the header is simply ignored there.
 *
 * Refreshing an expired token is [AuthAuthenticator]'s job, not this
 * interceptor's — this one only ever attaches whatever is currently
 * stored, it never blocks on a network call.
 */
class AuthInterceptor(private val tokenStore: AuthTokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenStore.accessToken ?: return chain.proceed(request)
        return chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $token").build(),
        )
    }
}
