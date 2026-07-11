package ch.mcfx.urs.data.sync

import ch.mcfx.urs.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Answers "can the backend actually be reached right now" — distinct from
 * [ch.mcfx.urs.vpn.NetworkGate], which only decides whether the WireGuard
 * tunnel needs to come up first (via home-Wi-Fi-SSID detection), not
 * whether the backend itself currently responds. A plain, unauthenticated
 * `GET /` against the server root is enough to confirm that; the response
 * there is plain cowsay text, not JSON, so this uses a bare [OkHttpClient]
 * rather than the app's kotlinx.serialization-configured Retrofit instance.
 */
class ReachabilityChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    suspend fun isReachable(): Boolean = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(BuildConfig.BASE_URL).build()).execute().use { it.isSuccessful }
        } catch (_: IOException) {
            false
        }
    }
}
