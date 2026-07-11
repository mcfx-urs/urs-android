package ch.mcfx.urs.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface UrsApi {

    @GET("api/v1/car")
    suspend fun getCars(): List<CarDto>

    @GET("api/v1/get-filling-station")
    suspend fun getFillingStations(): List<FillingStationDto>

    @POST("api/v1/filling-station")
    suspend fun createFillingStation(@Body payload: FillingStationPayload): FillingStationDto

    @GET("api/v1/fill")
    suspend fun getFills(): List<FillDto>

    @GET("api/v1/odometer/{carId}")
    suspend fun getOdometer(@Path("carId") carId: String): List<OdometerEntryDto>

    @POST("api/v1/fill")
    suspend fun createFill(@Body payload: FillPayload): FillDto

    @GET("api/v1/currency")
    suspend fun getCurrencies(): List<CurrencyDto>

    @GET("api/v1/inventory-category")
    suspend fun getInventoryCategories(): List<InventoryCategoryDto>

    @POST("api/v1/inventory-category")
    suspend fun createInventoryCategory(@Body payload: InventoryCategoryPayload)

    @DELETE("api/v1/inventory-category/{id}")
    suspend fun deleteInventoryCategory(@Path("id") id: String)

    @GET("api/v1/inventory-product/{categoryId}")
    suspend fun getInventoryProducts(@Path("categoryId") categoryId: String): List<InventoryProductDto>

    @POST("api/v1/inventory-product")
    suspend fun createInventoryProduct(@Body payload: InventoryProductPayload)

    @PUT("api/v1/inventory-product/{id}")
    suspend fun updateInventoryProduct(@Path("id") id: String, @Body payload: InventoryProductPayload)

    @PUT("api/v1/inventory-product/{id}/settings")
    suspend fun updateInventoryProductSettings(@Path("id") id: String, @Body payload: InventoryProductSettingsPayload)

    @DELETE("api/v1/inventory-product/{id}")
    suspend fun deleteInventoryProduct(@Path("id") id: String)

    @GET("api/v1/beer-log")
    suspend fun getBeerLog(): List<BeerLogDto>

    @POST("api/v1/beer-log")
    suspend fun createBeerLog(@Body payload: BeerLogPayload)

    @DELETE("api/v1/beer-log/{id}")
    suspend fun deleteBeerLog(@Path("id") id: String)
}
