package ch.mcfx.urs.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface UrsApi {

    @POST("api/v1/login")
    suspend fun login(@Body payload: LoginPayload): TokenResponseDto

    @POST("api/v1/refresh")
    suspend fun refresh(@Body payload: RefreshPayload): TokenResponseDto

    @PUT("api/v1/change-password")
    suspend fun changePassword(@Body payload: ChangePasswordPayload): TokenResponseDto

    @GET("api/v1/vehicle")
    suspend fun getVehicles(): List<VehicleDto>

    // The backend's response body just echoes the request DTO back (no
    // assigned vehicle_id — see postVehicle in urs-backend), so this isn't
    // decoded as a VehicleDto; the repository re-fetches the full list via
    // getVehicles() afterwards to learn the new vehicle's id.
    @POST("api/v1/vehicle")
    suspend fun createVehicle(@Body payload: VehiclePayload)

    @PUT("api/v1/vehicle/{id}")
    suspend fun updateVehicle(@Path("id") id: String, @Body payload: VehiclePayload)

    @DELETE("api/v1/vehicle/{id}")
    suspend fun deleteVehicle(@Path("id") id: String)

    @POST("api/v1/vehicle-service")
    suspend fun createVehicleService(@Body payload: VehicleServicePayload): VehicleServiceDto

    @GET("api/v1/vehicle-service")
    suspend fun getVehicleServices(): List<VehicleServiceDto>

    @PUT("api/v1/vehicle-service/{id}")
    suspend fun updateVehicleService(@Path("id") id: String, @Body payload: VehicleServicePayload): VehicleServiceDto

    @DELETE("api/v1/vehicle-service/{id}")
    suspend fun deleteVehicleService(@Path("id") id: String)

    @GET("api/v1/get-filling-station")
    suspend fun getFillingStations(): List<FillingStationDto>

    // Super-user-gated server-side — lists the ad-hoc (gps_auto) stations
    // awaiting review, which getFillingStations() deliberately excludes.
    @GET("api/v1/admin/filling-station/pending")
    suspend fun getPendingFillingStations(): List<FillingStationDto>

    @POST("api/v1/filling-station")
    suspend fun createFillingStation(@Body payload: FillingStationPayload): FillingStationDto

    // Super-user-gated server-side — the backend returns 403 for
    // anyone else, this call surfaces that as a normal HTTP exception.
    @PUT("api/v1/filling-station/{id}")
    suspend fun updateFillingStation(@Path("id") id: String, @Body payload: FillingStationPayload)

    @GET("api/v1/geocode")
    suspend fun geocode(@Query("address") address: String): GeocodeResultDto

    @GET("api/v1/fill")
    suspend fun getFills(): List<FillDto>

    @GET("api/v1/odometer/{vehicleId}")
    suspend fun getOdometer(@Path("vehicleId") vehicleId: String): List<OdometerEntryDto>

    @POST("api/v1/fill")
    suspend fun createFill(@Body payload: FillPayload): FillDto

    @PUT("api/v1/fill/{id}")
    suspend fun updateFill(@Path("id") id: String, @Body payload: FillUpdatePayload)

    @DELETE("api/v1/fill/{id}")
    suspend fun deleteFill(@Path("id") id: String)

    @GET("api/v1/get-fuel")
    suspend fun getFuelTypes(): List<FuelDto>

    @POST("api/v1/fuel-price")
    suspend fun createFuelPrice(@Body payload: FuelPricePayload): FuelPriceDto

    @GET("api/v1/fuel-price/{fuelId}")
    suspend fun getFuelPrices(@Path("fuelId") fuelId: String): List<FuelPriceDto>

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

    @PUT("api/v1/catalog-product/{id}")
    suspend fun updateCatalogProduct(@Path("id") id: String, @Body payload: CatalogProductUpdatePayload)

    @DELETE("api/v1/catalog-product/{id}")
    suspend fun deleteCatalogProduct(@Path("id") id: String)

    @GET("api/v1/catalog-category")
    suspend fun getCatalogCategories(): List<CatalogCategoryDto>

    @POST("api/v1/catalog-category")
    suspend fun createCatalogCategory(@Body payload: NewCatalogCategoryPayload): NewCatalogCategoryResponseDto

    @PUT("api/v1/catalog-category/{id}")
    suspend fun updateCatalogCategory(@Path("id") id: String, @Body payload: CatalogCategoryUpdatePayload)

    @DELETE("api/v1/catalog-category/{id}")
    suspend fun deleteCatalogCategory(@Path("id") id: String)

    @GET("api/v1/catalog-image")
    suspend fun getCatalogImages(): List<CatalogImageDto>

    @POST("api/v1/catalog-image/generate")
    suspend fun generateCatalogImage(@Body payload: GenerateCatalogImagePayload): CatalogImageDto

    // Super-user-gated server-side. Returns the raw PNG bytes — nothing is
    // persisted on the server, no catalog-image row is created.
    @POST("api/v1/admin/image/generate")
    suspend fun generateImage(@Body payload: GenerateImagePayload): ResponseBody

    // Super-user-gated server-side — same shape as
    // updateFillingStation/restartServer, the backend returns 403 for
    // anyone else.
    @GET("api/v1/admin/catalog-image/pending")
    suspend fun getPendingCatalogImages(): List<CatalogImageDto>

    @POST("api/v1/admin/catalog-image/{id}/approve")
    suspend fun approveCatalogImage(@Path("id") id: String)

    @POST("api/v1/admin/catalog-image/{id}/reject")
    suspend fun rejectCatalogImage(@Path("id") id: String)

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
    suspend fun getRecentlyUsedProducts(@Query("list_id") listId: String): List<RecentlyUsedProductDto>

    // Scoped server-side to the caller's own row (see urs-backend#3) — a
    // single object, not a list.
    @GET("api/v1/getuser")
    suspend fun getUser(): UserDto

    // Name-only projection of every user, for the household-member-picker
    // use case (UrsShareSheet) — never the caller-only sensitive fields
    // getUser returns.
    @GET("api/v1/household-users")
    suspend fun getHouseholdUsers(): List<HouseholdUserDto>

    @PUT("api/v1/user/default-vehicle")
    suspend fun updateUserDefaultVehicle(@Body payload: UserDefaultVehiclePayload)

    @PUT("api/v1/user/{id}/default-daily-target-hours")
    suspend fun updateUserDefaultDailyTargetHours(@Path("id") id: String, @Body payload: UserDefaultDailyTargetHoursPayload)

    @PUT("api/v1/user/{id}/employment-percent")
    suspend fun updateUserEmploymentPercent(@Path("id") id: String, @Body payload: UserEmploymentPercentPayload)

    @PUT("api/v1/user/{id}/hourly-wage")
    suspend fun updateUserHourlyWage(@Path("id") id: String, @Body payload: UserHourlyWagePayload)

    @PUT("api/v1/user/{id}/wage-rules")
    suspend fun updateUserWageRules(@Path("id") id: String, @Body payload: UserWageRulesPayload)

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

    @PUT("api/v1/beer-log/{id}")
    suspend fun updateBeerLog(@Path("id") id: String, @Body payload: BeerLogDateUpdatePayload)

    @DELETE("api/v1/beer-log/{id}")
    suspend fun deleteBeerLog(@Path("id") id: String)

    @POST("api/v1/location-history")
    suspend fun createLocationHistory(@Body payload: LocationHistoryPayload): LocationHistoryResponse

    // from/limit/order are required (not defaulted) call-site args, not
    // optional query params — the endpoint's own default limit is only 50,
    // and its hard cap is 10000 (`sanitizeLimit` in `urs-backend`), too small
    // to ever be "the whole history" on its own for an always-on periodic
    // capture — see LocationHistoryRepository's paging loop, which is the
    // only caller and decides all three explicitly for that reason.
    @GET("api/v1/location-history")
    suspend fun getLocationHistory(
        @Query("from") from: String,
        @Query("limit") limit: String,
        @Query("order") order: String,
    ): List<LocationHistoryDto>

    @POST("api/v1/baking/plans")
    suspend fun createBakePlan(@Body payload: BakePlanCreatePayload): BakePlanDto

    @GET("api/v1/baking/plans")
    suspend fun getBakePlans(@Query("status") status: String? = null): List<BakePlanDto>

    @PATCH("api/v1/baking/plans/{id}")
    suspend fun cancelBakePlan(@Path("id") id: String)

    @PATCH("api/v1/baking/plans/{id}/steps/{stepId}")
    suspend fun updateBakePlanStep(@Path("id") id: String, @Path("stepId") stepId: String, @Body payload: BakePlanStepPatchPayload)

    @POST("api/v1/note")
    suspend fun createNote(@Body payload: NoteCreatePayload): NoteDto

    @GET("api/v1/note")
    suspend fun getNotes(@Query("status") status: String? = null): List<NoteDto>

    @PUT("api/v1/note/{id}")
    suspend fun updateNote(@Path("id") id: String, @Body payload: NoteCreatePayload): NoteDto

    @PATCH("api/v1/note/{id}/status")
    suspend fun updateNoteStatus(@Path("id") id: String, @Body payload: NoteStatusPatchPayload)

    @DELETE("api/v1/note/{id}")
    suspend fun deleteNote(@Path("id") id: String)

    @POST("api/v1/tracker-type")
    suspend fun createTrackerType(@Body payload: TrackerTypePayload): TrackerTypeDto

    @GET("api/v1/tracker-type")
    suspend fun getTrackerTypes(): List<TrackerTypeDto>

    @PUT("api/v1/tracker-type/{id}")
    suspend fun updateTrackerType(@Path("id") id: String, @Body payload: TrackerTypePayload)

    @DELETE("api/v1/tracker-type/{id}")
    suspend fun archiveTrackerType(@Path("id") id: String)

    @PUT("api/v1/tracker-type/{id}/reactivate")
    suspend fun reactivateTrackerType(@Path("id") id: String)

    @PUT("api/v1/tracker-type/{id}/exported")
    suspend fun markTrackerTypeExported(@Path("id") id: String)

    @POST("api/v1/tracker-event")
    suspend fun createTrackerEvent(@Body payload: TrackerEventPayload): TrackerEventDto

    @GET("api/v1/tracker-event")
    suspend fun getTrackerEvents(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): List<TrackerEventDto>

    @PUT("api/v1/tracker-event/{id}")
    suspend fun updateTrackerEvent(@Path("id") id: String, @Body payload: TrackerEventPayload)

    @DELETE("api/v1/tracker-event/{id}")
    suspend fun deleteTrackerEvent(@Path("id") id: String)

    @POST("api/v1/journal-domain")
    suspend fun createTrackerDomain(@Body payload: TrackerDomainPayload): TrackerDomainDto

    @GET("api/v1/journal-domain")
    suspend fun getTrackerDomains(): List<TrackerDomainDto>

    @PUT("api/v1/journal-domain/{id}")
    suspend fun updateTrackerDomain(@Path("id") id: String, @Body payload: TrackerDomainPayload)

    @DELETE("api/v1/journal-domain/{id}")
    suspend fun deleteTrackerDomain(@Path("id") id: String)

    @POST("api/v1/kanban/board")
    suspend fun createKanbanBoard(@Body payload: KanbanBoardCreatePayload): KanbanBoardDto

    @GET("api/v1/kanban/board")
    suspend fun getKanbanBoards(): List<KanbanBoardDto>

    @GET("api/v1/kanban/board/{id}")
    suspend fun getKanbanBoard(@Path("id") id: String): KanbanBoardDetailDto

    @PUT("api/v1/kanban/board/{id}")
    suspend fun renameKanbanBoard(@Path("id") id: String, @Body payload: KanbanBoardRenamePayload)

    @DELETE("api/v1/kanban/board/{id}")
    suspend fun deleteKanbanBoard(@Path("id") id: String)

    @POST("api/v1/kanban/column")
    suspend fun createKanbanColumn(@Body payload: KanbanColumnCreatePayload): KanbanColumnDto

    @PUT("api/v1/kanban/column/{id}")
    suspend fun renameKanbanColumn(@Path("id") id: String, @Body payload: KanbanColumnRenamePayload)

    @PUT("api/v1/kanban/column/{id}/move")
    suspend fun moveKanbanColumn(@Path("id") id: String, @Body payload: KanbanColumnMovePayload)

    @DELETE("api/v1/kanban/column/{id}")
    suspend fun deleteKanbanColumn(@Path("id") id: String)

    @POST("api/v1/kanban/card")
    suspend fun createKanbanCard(@Body payload: KanbanCardCreatePayload): KanbanCardDto

    @PUT("api/v1/kanban/card/{id}")
    suspend fun updateKanbanCard(@Path("id") id: String, @Body payload: KanbanCardUpdatePayload): KanbanCardDto

    @PUT("api/v1/kanban/card/{id}/move")
    suspend fun moveKanbanCard(@Path("id") id: String, @Body payload: KanbanCardMovePayload)

    @DELETE("api/v1/kanban/card/{id}")
    suspend fun deleteKanbanCard(@Path("id") id: String)

    @POST("api/v1/kanban/checklist-item")
    suspend fun createKanbanChecklistItem(@Body payload: KanbanChecklistItemCreatePayload): KanbanChecklistItemDto

    @PUT("api/v1/kanban/checklist-item/{id}")
    suspend fun updateKanbanChecklistItem(@Path("id") id: String, @Body payload: KanbanChecklistItemUpdatePayload)

    // Shared Notes/Kanban tag pool (mcfx-urs/urs-backend#11) — the user's
    // full tag catalog, for autocomplete that isn't limited to tags already
    // attached to already-fetched notes/cards.
    @GET("api/v1/tags")
    suspend fun getTags(): List<TagDto>

    @DELETE("api/v1/kanban/checklist-item/{id}")
    suspend fun deleteKanbanChecklistItem(@Path("id") id: String)

    @POST("api/v1/admin/restart")
    suspend fun restartServer()

    @GET("api/v1/admin/log-level")
    suspend fun getLogLevel(): LogLevelDto

    @PATCH("api/v1/admin/log-level")
    suspend fun setLogLevel(@Body payload: LogLevelDto)

    // The bare root route (misc.Cow() banner, plain text, no auth) — used
    // purely as a liveness probe to detect when the backend has come back
    // up after restartServer(). "." resolves to the base URL itself.
    @GET(".")
    suspend fun ping()
}
