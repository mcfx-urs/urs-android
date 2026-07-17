package ch.mcfx.urs.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface UrsApi {

    @POST("api/v1/login")
    suspend fun login(@Body payload: LoginPayload): TokenResponseDto

    @POST("api/v1/refresh")
    suspend fun refresh(@Body payload: RefreshPayload): TokenResponseDto

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

    @GET("api/v1/inventory")
    suspend fun getInventories(): List<InventoryDto>

    @POST("api/v1/inventory")
    suspend fun createInventory(@Body payload: InventoryPayload): InventoryCreateResponseDto

    @PUT("api/v1/inventory/{id}")
    suspend fun updateInventory(@Path("id") id: String, @Body payload: InventoryPayload)

    @DELETE("api/v1/inventory/{id}")
    suspend fun deleteInventory(@Path("id") id: String)

    @POST("api/v1/inventory/{id}/share")
    suspend fun shareInventory(@Path("id") id: String, @Body payload: InventorySharePayload)

    @GET("api/v1/inventory/{id}/share")
    suspend fun getInventoryShares(@Path("id") id: String): List<InventoryShareDto>

    @DELETE("api/v1/inventory/{id}/share/{userId}")
    suspend fun deleteInventoryShare(@Path("id") id: String, @Path("userId") userId: String)

    @POST("api/v1/inventory-product")
    suspend fun createInventoryProduct(@Body payload: InventoryProductCreatePayload): InventoryProductCreateResponseDto

    @GET("api/v1/inventory-product/{inventoryId}")
    suspend fun getInventoryProducts(@Path("inventoryId") inventoryId: String): List<InventoryProductDto>

    @PUT("api/v1/inventory-product/{id}")
    suspend fun updateInventoryProduct(@Path("id") id: String, @Body payload: InventoryProductQuantityPayload)

    @PUT("api/v1/inventory-product/{id}/settings")
    suspend fun updateInventoryProductSettings(@Path("id") id: String, @Body payload: InventoryProductSettingsPayload)

    @DELETE("api/v1/inventory-product/{id}")
    suspend fun deleteInventoryProduct(@Path("id") id: String)

    @GET("api/v1/catalog-product")
    suspend fun getCatalogProducts(): List<CatalogProductDto>

    @POST("api/v1/catalog-product")
    suspend fun createCatalogProduct(@Body payload: NewCatalogProductPayload): NewCatalogProductResponseDto

    // 204 (untracked in any inventory the caller can access) vs 200 needs to
    // be told apart explicitly — wrapped in Response<...> rather than a bare
    // suspend return, since a bare return would otherwise fail trying to
    // decode an empty 204 body as JSON. See CatalogRepository.quantityOnHand.
    @GET("api/v1/catalog-product/{catalogProductId}/quantity-on-hand")
    suspend fun getQuantityOnHand(@Path("catalogProductId") catalogProductId: String): Response<QuantityOnHandDto>

    @GET("api/v1/catalog-category")
    suspend fun getCatalogCategories(): List<CatalogCategoryDto>

    @POST("api/v1/catalog-category")
    suspend fun createCatalogCategory(@Body payload: NewCatalogCategoryPayload): NewCatalogCategoryResponseDto

    @POST("api/v1/list")
    suspend fun createList(@Body payload: ListPayload): ListDto

    @GET("api/v1/list")
    suspend fun getLists(): List<ListDto>

    @PUT("api/v1/list/{id}")
    suspend fun updateList(@Path("id") id: String, @Body payload: ListPayload)

    @DELETE("api/v1/list/{id}")
    suspend fun deleteList(@Path("id") id: String)

    @POST("api/v1/list/{id}/share")
    suspend fun shareList(@Path("id") id: String, @Body payload: ListSharePayload)

    @GET("api/v1/list/{id}/share")
    suspend fun getListShares(@Path("id") id: String): List<ListShareDto>

    @DELETE("api/v1/list/{id}/share/{userId}")
    suspend fun deleteListShare(@Path("id") id: String, @Path("userId") userId: String)

    @POST("api/v1/list-item")
    suspend fun createListItem(@Body payload: ListItemPayload): ListItemDto

    @GET("api/v1/list/{listId}/list-item")
    suspend fun getListItems(@Path("listId") listId: String): List<ListItemDto>

    @PUT("api/v1/list-item/{id}")
    suspend fun updateListItem(@Path("id") id: String, @Body payload: ListItemUpdatePayload)

    @DELETE("api/v1/list-item/{id}")
    suspend fun deleteListItem(@Path("id") id: String)

    @GET("api/v1/recently-used-product")
    suspend fun getRecentlyUsedProducts(): List<RecentlyUsedProductDto>

    @GET("api/v1/getuser")
    suspend fun getUsers(): List<UserDto>

    @PUT("api/v1/user/{id}/default-daily-target-hours")
    suspend fun updateUserDefaultDailyTargetHours(@Path("id") id: String, @Body payload: UserDefaultDailyTargetHoursPayload)

    @PUT("api/v1/user/{id}/employment-percent")
    suspend fun updateUserEmploymentPercent(@Path("id") id: String, @Body payload: UserEmploymentPercentPayload)

    @PUT("api/v1/user/{id}/hourly-wage")
    suspend fun updateUserHourlyWage(@Path("id") id: String, @Body payload: UserHourlyWagePayload)

    @GET("api/v1/work-time-month-override/{userId}")
    suspend fun getWorkTimeMonthOverrides(@Path("userId") userId: String): List<WorkTimeMonthOverrideDto>

    @PUT("api/v1/work-time-month-override/{userId}/{year}/{month}")
    suspend fun updateWorkTimeMonthOverride(
        @Path("userId") userId: String,
        @Path("year") year: String,
        @Path("month") month: String,
        @Body payload: WorkTimeMonthOverridePayload,
    )

    @DELETE("api/v1/work-time-month-override/{userId}/{year}/{month}")
    suspend fun deleteWorkTimeMonthOverride(
        @Path("userId") userId: String,
        @Path("year") year: String,
        @Path("month") month: String,
    )

    @POST("api/v1/work-time-entry")
    suspend fun createWorkTimeEntry(@Body payload: WorkTimeEntryPayload): WorkTimeEntryDto

    @GET("api/v1/work-time-entry/{userId}/{limit}/{order}")
    suspend fun getWorkTimeEntries(
        @Path("userId") userId: String,
        @Path("limit") limit: String,
        @Path("order") order: String,
    ): List<WorkTimeEntryDto>

    @PUT("api/v1/work-time-entry/{id}")
    suspend fun updateWorkTimeEntry(@Path("id") id: String, @Body payload: WorkTimeEntryPayload): WorkTimeEntryDto

    @DELETE("api/v1/work-time-entry/{id}")
    suspend fun deleteWorkTimeEntry(@Path("id") id: String)

    @GET("api/v1/beer-log")
    suspend fun getBeerLog(): List<BeerLogDto>

    @POST("api/v1/beer-log")
    suspend fun createBeerLog(@Body payload: BeerLogPayload)

    @DELETE("api/v1/beer-log/{id}")
    suspend fun deleteBeerLog(@Path("id") id: String)
}
