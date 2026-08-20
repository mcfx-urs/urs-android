package ch.mcfx.urs.auth

import ch.mcfx.urs.data.local.AppDatabase
import ch.mcfx.urs.data.remote.ChangePasswordPayload
import ch.mcfx.urs.data.remote.LoginPayload
import ch.mcfx.urs.data.remote.UrsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thin wrapper around [UrsApi.login] — the counterpart refresh path lives in
 * [AuthAuthenticator] instead, since that one has to run synchronously off
 * OkHttp's own dispatcher (see its doc comment), not through this
 * suspend-based repository.
 */
class AuthRepository(
    private val api: UrsApi,
    private val tokenStore: AuthTokenStore,
    private val database: AppDatabase,
) {
    suspend fun login(userName: String, password: String) {
        val tokens = api.login(LoginPayload(userName = userName, password = password))
        tokenStore.save(tokens.accessToken, tokens.refreshToken, userName = userName)
    }

    /**
     * Wipes the local Room cache before clearing the token store — otherwise
     * a previous user's synced rows (lists, inventories, ...) simply stay on
     * disk and, since none of them carry a user identity, would be shown
     * again verbatim to whoever logs in next on this device.
     */
    suspend fun logout() {
        withContext(Dispatchers.IO) { database.clearAllTables() }
        tokenStore.clear()
    }

    /**
     * The backend revokes every other outstanding refresh token for this
     * user on a successful change, then issues a fresh pair for the
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
