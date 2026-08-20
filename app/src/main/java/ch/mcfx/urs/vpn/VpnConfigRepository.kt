package ch.mcfx.urs.vpn

import android.content.Context
import ch.mcfx.urs.auth.AuthTokenStore
import ch.mcfx.urs.security.KeystoreCipher

private const val PREFS_NAME_PREFIX = "vpn_prefs_"
private const val KEY_WG_CONFIG_ENCRYPTED = "wireguard_config_text_enc"
private const val KEY_HOME_SSIDS = "home_wifi_ssids"
private const val KEY_EXCLUSIVE_MODE = "vpn_exclusive_mode"
private const val KEYSTORE_ALIAS = "urs_vpn_config_key"

/**
 * Holds the WireGuard client config (a real private key granting home-network
 * access) and the set of home Wi-Fi SSIDs the user switches between. The
 * config text is encrypted at rest via KeystoreCipher (Android Keystore-backed
 * AES-256-GCM) — it must never be bundled, hardcoded, or committed anywhere
 * in this repo; a user provisions it once at runtime (Settings) from their
 * own WireGuard server. SSIDs aren't secret, so they're stored plainly.
 *
 * The config is assigned to one app user, not the device — each logged-in
 * [AuthTokenStore.currentUserId] gets its own separate `SharedPreferences`
 * file (see [prefs]), so switching the logged-in app user on one device
 * never exposes, nor silently reuses, a different user's WireGuard key. A
 * user who hasn't provisioned their own config simply has none (same as a
 * fresh install), not the previous user's.
 */
class VpnConfigRepository(private val context: Context, private val authTokenStore: AuthTokenStore) {

    private val prefs
        get() = context.getSharedPreferences(PREFS_NAME_PREFIX + authTokenStore.currentUserId.orEmpty(), Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher(KEYSTORE_ALIAS)

    fun getConfigText(): String? =
        prefs.getString(KEY_WG_CONFIG_ENCRYPTED, null)?.let { cipher.decrypt(it) }

    fun setConfigText(config: String) {
        prefs.edit().putString(KEY_WG_CONFIG_ENCRYPTED, cipher.encrypt(config)).apply()
    }

    fun clearConfig() {
        prefs.edit().remove(KEY_WG_CONFIG_ENCRYPTED).apply()
    }

    fun getHomeSsids(): Set<String> = prefs.getStringSet(KEY_HOME_SSIDS, emptySet()) ?: emptySet()

    fun addHomeSsid(ssid: String) {
        val updated = getHomeSsids().toMutableSet().apply { add(ssid) }
        prefs.edit().putStringSet(KEY_HOME_SSIDS, updated).apply()
    }

    fun removeHomeSsid(ssid: String) {
        val updated = getHomeSsids().toMutableSet().apply { remove(ssid) }
        prefs.edit().putStringSet(KEY_HOME_SSIDS, updated).apply()
    }

    fun isExclusiveModeEnabled(): Boolean = prefs.getBoolean(KEY_EXCLUSIVE_MODE, false)

    fun setExclusiveModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EXCLUSIVE_MODE, enabled).apply()
    }
}
