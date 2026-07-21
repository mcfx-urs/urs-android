package ch.mcfx.urs.auth

import android.content.Context
import ch.mcfx.urs.security.KeystoreCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "auth_prefs"
private const val KEY_ACCESS_TOKEN = "access_token_enc"
private const val KEY_REFRESH_TOKEN = "refresh_token_enc"
private const val KEY_USER_NAME = "user_name"
private const val KEYSTORE_ALIAS = "urs_auth_token_key"

/**
 * Holds the current access/refresh token pair, encrypted at rest via
 * [KeystoreCipher] (Android Keystore-backed AES-256-GCM) — same pattern as
 * [ch.mcfx.urs.vpn.VpnConfigRepository]'s WireGuard config, its own key
 * alias so one secret's key can't decrypt the other's ciphertext.
 *
 * [isLoggedIn] is the single source of truth [AppNavigation][ch.mcfx.urs.navigation.AppNavigation]
 * watches to decide whether to show the login screen or the app — it's
 * driven off whether a refresh token is present, not the (much
 * shorter-lived) access token, since the whole point of the refresh flow
 * is that an expired access token alone shouldn't force a login screen.
 * [AuthAuthenticator] clears both tokens (flipping this back to `false`)
 * whenever a refresh attempt itself fails.
 */
class AuthTokenStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher(KEYSTORE_ALIAS)

    @Volatile
    var accessToken: String? = prefs.getString(KEY_ACCESS_TOKEN, null)?.let { cipher.decrypt(it) }
        private set

    @Volatile
    var refreshToken: String? = prefs.getString(KEY_REFRESH_TOKEN, null)?.let { cipher.decrypt(it) }
        private set

    private val _isLoggedIn = MutableStateFlow(refreshToken != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    /** The authenticated caller's own `user_id`, decoded from the current access token. */
    val currentUserId: String?
        get() = accessToken?.let { JwtDecoder.subject(it) }

    /** Whether the current access token carries the super-user flag (see ). */
    val isSuperUser: Boolean
        get() = accessToken?.let { JwtDecoder.isSuperUser(it) } ?: false

    /**
     * The username last used to log in — not sensitive (unlike the tokens
     * above), stored in plain prefs purely for display (e.g. "Logged in as
     * pmo" in Settings). Not re-derived from the token itself since the
     * JWT's `sub` claim is the numeric `user_id`, not the username.
     */
    var userName: String? = prefs.getString(KEY_USER_NAME, null)
        private set

    @Synchronized
    fun save(accessToken: String, refreshToken: String, userName: String? = this.userName) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        this.userName = userName
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, cipher.encrypt(accessToken))
            .putString(KEY_REFRESH_TOKEN, cipher.encrypt(refreshToken))
            .putString(KEY_USER_NAME, userName)
            .apply()
        _isLoggedIn.value = true
    }

    @Synchronized
    fun clear() {
        accessToken = null
        refreshToken = null
        userName = null
        prefs.edit().remove(KEY_ACCESS_TOKEN).remove(KEY_REFRESH_TOKEN).remove(KEY_USER_NAME).apply()
        _isLoggedIn.value = false
    }
}
