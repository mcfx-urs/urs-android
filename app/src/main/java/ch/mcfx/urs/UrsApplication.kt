package ch.mcfx.urs

import android.app.Application
import ch.mcfx.urs.data.FuelRepository
import ch.mcfx.urs.data.remote.UrsApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class UrsApplication : Application() {
    val container: AppContainer by lazy { AppContainer() }
}

// Manual dependency injection: one place that builds and owns the object
// graph. A DI framework (Hilt) can replace this later if it grows.
class AppContainer {

    private val json = Json { ignoreUnknownKeys = true }

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
}
