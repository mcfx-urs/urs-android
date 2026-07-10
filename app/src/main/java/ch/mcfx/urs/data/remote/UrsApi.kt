package ch.mcfx.urs.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface UrsApi {

    @GET("api/v1/car")
    suspend fun getCars(): List<CarDto>

    @GET("api/v1/get-filling-station")
    suspend fun getFillingStations(): List<FillingStationDto>

    @POST("api/v1/filling-station")
    suspend fun createFillingStation(@Body payload: FillingStationPayload)

    @GET("api/v1/fill")
    suspend fun getFills(): List<FillDto>

    @GET("api/v1/odometer/{carId}")
    suspend fun getOdometer(@Path("carId") carId: String): List<OdometerEntryDto>

    @POST("api/v1/fill")
    suspend fun createFill(@Body payload: FillPayload)
}
