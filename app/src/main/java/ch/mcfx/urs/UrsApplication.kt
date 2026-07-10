package ch.mcfx.urs

import android.app.Application
import android.content.Context
import ch.mcfx.urs.data.FuelRepository
import ch.mcfx.urs.data.InventoryRepository
import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.vpn.NetworkGate
import ch.mcfx.urs.vpn.VpnConfigRepository
import ch.mcfx.urs.vpn.WifiSsidReader
import ch.mcfx.urs.vpn.WireGuardManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class UrsApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Process-lifetime Wi-Fi listener so a live network change (e.g.
        // arriving home while the app is already open) is reacted to
        // immediately, not just at the next cold start.
        container.networkGate.startObserving(container.applicationScope)
    }
}

// Manual dependency injection: one place that builds and owns the object
// graph. A DI framework (Hilt) can replace this later if it grows.
class AppContainer(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val vpnConfigRepository = VpnConfigRepository(context)
    val wireGuardManager = WireGuardManager(context, vpnConfigRepository)
    val networkGate = NetworkGate(context, WifiSsidReader(context), vpnConfigRepository, wireGuardManager)

    private val httpClient = OkHttpClient.Builder()
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
                )
            }
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(httpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val fuelRepository = FuelRepository(retrofit.create(UrsApi::class.java))
    val inventoryRepository = InventoryRepository(retrofit.create(UrsApi::class.java))
}
