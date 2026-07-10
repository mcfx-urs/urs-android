package ch.mcfx.urs.vpn

import android.content.Context

private const val PREFS_NAME = "vpn_prefs"
private const val KEY_WG_CONFIG_ENCRYPTED = "wireguard_config_text_enc"
private const val KEY_HOME_SSIDS = "home_wifi_ssids"

// Holds the WireGuard client config (a real private key granting home-network
// access) and the set of home Wi-Fi SSIDs the user switches between. The
// config text is encrypted at rest via KeystoreCipher (Android Keystore-backed
// AES-256-GCM) — it must never be bundled, hardcoded, or committed anywhere
// in this repo; the user provisions it once at runtime (Settings) from their
// own WireGuard server. SSIDs aren't secret, so they're stored plainly.
class VpnConfigRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getConfigText(): String? =
        prefs.getString(KEY_WG_CONFIG_ENCRYPTED, null)?.let { KeystoreCipher.decrypt(it) }

    fun setConfigText(config: String) {
        prefs.edit().putString(KEY_WG_CONFIG_ENCRYPTED, KeystoreCipher.encrypt(config)).apply()
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
}
