package ch.mcfx.urs.auth

import ch.mcfx.urs.data.remote.ChangePasswordPayload
import ch.mcfx.urs.data.remote.LoginPayload
import ch.mcfx.urs.data.remote.UrsApi

/**
 * Thin wrapper around [UrsApi.login] — the counterpart refresh path lives in
 * [AuthAuthenticator] instead, since that one has to run synchronously off
 * OkHttp's own dispatcher (see its doc comment), not through this
 * suspend-based repository.
 */
class AuthRepository(
    private val api: UrsApi,
    private val tokenStore: AuthTokenStore,
) {
    suspend fun login(userName: String, password: String) {
        val tokens = api.login(LoginPayload(userName = userName, password = password))
        tokenStore.save(tokens.accessToken, tokens.refreshToken, userName = userName)
    }

    fun logout() {
        tokenStore.clear()
    }

    /**
     * the backend revokes every other outstanding refresh token for
     * this user on a successful change, then issues a fresh pair for the
     * caller — stored here so this device stays logged in while every other
     * session is forced back to the login screen on its next refresh.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val tokens = api.changePassword(
            ChangePasswordPayload(currentPassword = currentPassword, newPassword = newPassword),
        )
        tokenStore.save(tokens.accessToken, tokens.refreshToken)
    }
}
