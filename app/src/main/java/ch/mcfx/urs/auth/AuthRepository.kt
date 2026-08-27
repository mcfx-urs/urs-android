package ch.mcfx.urs.auth

import ch.mcfx.urs.data.DefaultVehicleStore
import ch.mcfx.urs.data.WorkSettingsStore
import ch.mcfx.urs.data.local.AppDatabase
import ch.mcfx.urs.data.remote.ChangePasswordPayload
import ch.mcfx.urs.data.remote.LoginPayload
import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.vpn.WireGuardManager
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
    private val wireGuardManager: WireGuardManager,
    private val workSettingsStore: WorkSettingsStore,
    private val defaultVehicleStore: DefaultVehicleStore,
) {
    suspend fun login(userName: String, password: String) {
        val tokens = api.login(LoginPayload(userName = userName, password = password))
        tokenStore.save(tokens.accessToken, tokens.refreshToken, userName = userName)
    }

    /**
     * Tears down any active VPN tunnel and wipes the local Room cache (plus
     * the per-user caches that live outside Room: WorkSettingsStore and
     * DefaultVehicleStore) before clearing the token store — otherwise a previous user's
     * still-running tunnel, or their synced rows (lists, inventories, ...),
     * simply stay in place and, since neither carried a user identity
     * before, would keep being used/shown verbatim by whoever logs in next
     * on this device. Deliberately calls [WireGuardManager.disconnect]
     * directly rather than [ch.mcfx.urs.vpn.NetworkGate.userDisconnect] —
     * the latter also flips a "user disabled" flag that would incorrectly
     * suppress the *next* user's own automatic reconnect too.
     */
    suspend fun logout() {
        wireGuardManager.disconnect()
        withContext(Dispatchers.IO) { database.clearAllTables() }
        workSettingsStore.clear()
        defaultVehicleStore.clear()
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
