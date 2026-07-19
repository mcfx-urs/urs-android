package ch.mcfx.urs.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// All values are strings because the backend serializes every DB column as a
// string. Typed cleanup is planned together with the Phase 2 sync work.

@Serializable
data class CarDto(
    @SerialName("car_id") val id: String,
    @SerialName("car_fuel_id") val fuelId: String,
    @SerialName("fuel_name") val fuelName: String,
    @SerialName("car_brand") val brand: String,
    @SerialName("car_model") val model: String,
    @SerialName("car_year") val year: String,
)

@Serializable
data class FillingStationDto(
    @SerialName("filling_station_id") val id: String,
    @SerialName("filling_station_name") val name: String,
    @SerialName("filling_station_counter") val counter: String,
    @SerialName("filling_station_address") val address: String,
    @SerialName("filling_station_latitude") val latitude: String,
    @SerialName("filling_station_longitude") val longitude: String,
)

@Serializable
data class FillDto(
    @SerialName("fill_id") val id: String,
    @SerialName("fill_date") val date: String,
    @SerialName("fill_car_id") val carId: String,
    @SerialName("fill_station_id") val stationId: String,
    @SerialName("fill_fuel_id") val fuelId: String,
    @SerialName("fill_price") val pricePerLiter: String,
    @SerialName("fill_amount") val liters: String,
    @SerialName("fill_odometer") val odometer: String,
    @SerialName("fill_is_full_tank") val isFullTank: String = "1",
    @SerialName("fill_currency_code") val currencyCode: String = "CHF",
    // Empty until the async FX-resolution job (backend-side) fills it in;
    // "" mirrors the backend's own nullable-column-as-empty-string convention.
    @SerialName("fill_amount_chf") val amountChf: String = "",
    @SerialName("fill_amount_chf_locked") val amountChfLocked: String = "0",
)

@Serializable
data class OdometerEntryDto(
    @SerialName("id") val id: String,
    @SerialName("date") val date: String,
    @SerialName("mileage") val mileage: String,
    @SerialName("car_id") val carId: String,
    @SerialName("driven") val driven: String,
)

@Serializable
data class FillingStationPayload(
    @SerialName("filling_station_name") val name: String,
    @SerialName("filling_station_address") val address: String = "",
    @SerialName("filling_station_latitude") val latitude: String = "",
    @SerialName("filling_station_longitude") val longitude: String = "",
)

@Serializable
data class FillPayload(
    @SerialName("fill_date") val date: String,
    @SerialName("fill_car_id") val carId: String,
    // Null/empty signals "create an ad-hoc station instead" — matches the
    // backend's own `FillStationID == ""` check — in which case
    // [stationLatitude]/[stationLongitude] are required.
    @SerialName("fill_station_id") val stationId: String? = null,
    @SerialName("fill_fuel_id") val fuelId: String,
    @SerialName("fill_price") val pricePerLiter: String,
    @SerialName("fill_amount") val liters: String,
    @SerialName("fill_odometer") val odometer: String,
    @SerialName("driven") val driven: String,
    @SerialName("filling_station_counter") val stationCounter: String,
    // Always sent explicitly rather than left to the backend's own default:
    // the backend echoes back whatever string was sent, not its resolved
    // default, so relying on omission here would desync local state from
    // what the create response actually reports.
    @SerialName("fill_currency_code") val currencyCode: String = "CHF",
    // Left at its default ("1", full) whenever the fill is a full tank —
    // the app's Json config omits fields at their default value
    // (encodeDefaults is unset), so "1" simply isn't sent, which the
    // backend already treats identically to an explicit "1" (its own
    // rollout-safety default). Only "0" (partial) is ever actually
    // serialized onto the wire.
    @SerialName("fill_is_full_tank") val isFullTank: String = "1",
    @SerialName("station_latitude") val stationLatitude: String? = null,
    @SerialName("station_longitude") val stationLongitude: String? = null,
)

@Serializable
data class CurrencyDto(
    @SerialName("currency_code") val code: String,
    @SerialName("currency_name") val name: String,
)

// catalog_product/catalog_category are the shared/public reference
// pool; inventory/list are independent, ownable, shareable containers built
// on top of it. Replaces the old InventoryCategoryDto/InventoryCategoryPayload
// (inventory_category is gone entirely) and the old InventoryProductDto/
// InventoryProductPayload shape (categoryId/name/recent-notes moved to or
// derived from catalog_product).

@Serializable
data class CatalogCategoryDto(
    @SerialName("catalog_category_id") val id: String,
    @SerialName("catalog_category_source") val source: String = "",
    @SerialName("catalog_category_source_category_id") val sourceCategoryId: String = "",
    @SerialName("catalog_category_name") val name: String,
    @SerialName("catalog_category_catalog_image_id") val catalogImageId: String = "",
)

@Serializable
data class NewCatalogCategoryPayload(
    @SerialName("catalog_category_name") val name: String,
)

@Serializable
data class NewCatalogCategoryResponseDto(
    @SerialName("catalog_category_id") val id: String,
    @SerialName("catalog_category_name") val name: String,
)

// Empty string = not set, mirrors the backend's COALESCE(..., '') for these
// columns.
@Serializable
data class CatalogProductDto(
    @SerialName("catalog_product_id") val id: String,
    @SerialName("catalog_product_source") val source: String = "",
    @SerialName("catalog_product_source_product_id") val sourceProductId: String = "",
    @SerialName("catalog_product_category_name") val categoryName: String,
    @SerialName("catalog_product_catalog_category_id") val catalogCategoryId: String = "",
    @SerialName("catalog_product_name") val name: String,
    @SerialName("catalog_product_search_terms") val searchTerms: String = "",
    @SerialName("catalog_product_brands") val brands: String = "",
    @SerialName("catalog_product_popularity_index") val popularityIndex: String = "",
    @SerialName("catalog_product_catalog_image_id") val catalogImageId: String = "",
    // Most-recently-used notes for this product's list items, newest first
    // (1 = most recent) — global per catalog product (, moved off the
    // old per-household inventory_product), shifted 1→2→3 server-side each
    // time a distinct note is used again. Surfaced as tap-to-fill chips in
    // AddProductScreen's note step.
    @SerialName("catalog_product_recent_note_1") val recentNote1: String = "",
    @SerialName("catalog_product_recent_note_2") val recentNote2: String = "",
    @SerialName("catalog_product_recent_note_3") val recentNote3: String = "",
)

@Serializable
data class NewCatalogProductPayload(
    @SerialName("catalog_product_name") val name: String,
    // "" = uncategorized, same nullable-column-as-empty-string convention as CatalogProductDto.
    @SerialName("catalog_product_catalog_category_id") val catalogCategoryId: String = "",
)

@Serializable
data class NewCatalogProductResponseDto(
    @SerialName("catalog_product_id") val id: String,
    @SerialName("catalog_product_name") val name: String,
    @SerialName("catalog_product_catalog_category_id") val catalogCategoryId: String = "",
)

// 204 No Content (untracked anywhere the caller can access) maps to this
// simply not being decoded at all — see UrsApi.getQuantityOnHand's own
// Response<...> wrapping.
@Serializable
data class QuantityOnHandDto(
    @SerialName("quantity_on_hand") val quantity: String,
)

@Serializable
data class InventoryDto(
    @SerialName("inventory_id") val id: String,
    @SerialName("inventory_name") val name: String,
    @SerialName("inventory_owner_user_id") val ownerUserId: String = "",
)

@Serializable
data class InventoryPayload(
    @SerialName("inventory_name") val name: String,
)

@Serializable
data class InventoryCreateResponseDto(
    @SerialName("inventory_id") val id: String,
    @SerialName("inventory_name") val name: String,
)

@Serializable
data class InventoryShareDto(
    @SerialName("inventory_share_id") val id: String,
    @SerialName("inventory_share_inventory_id") val inventoryId: String,
    @SerialName("inventory_share_user_id") val userId: String,
)

@Serializable
data class InventorySharePayload(
    @SerialName("inventory_share_user_id") val userId: String,
)

@Serializable
data class InventoryProductDto(
    @SerialName("inventory_product_id") val id: String,
    @SerialName("inventory_product_inventory_id") val inventoryId: String,
    @SerialName("inventory_product_catalog_product_id") val catalogProductId: String,
    @SerialName("inventory_product_quantity") val quantity: String = "",
    @SerialName("inventory_product_first_threshold") val firstThreshold: String = "",
    @SerialName("inventory_product_second_threshold") val secondThreshold: String = "",
    @SerialName("inventory_product_reminder_threshold") val reminderThreshold: String = "",
    @SerialName("inventory_product_reminder_hour") val reminderHour: String = "",
    @SerialName("inventory_product_reminder_minute") val reminderMinute: String = "",
)

// The create route's response echoes only the fields the backend's own
// InventoryProduct request struct carries (id, inventoryId, catalogProductId,
// quantity — no threshold/reminder fields, those are only ever set via the
// dedicated settings route below) — kept as its own type rather than reusing
// InventoryProductDto so a missing threshold field here is never mistaken
// for "explicitly cleared".
@Serializable
data class InventoryProductCreateResponseDto(
    @SerialName("inventory_product_id") val id: String,
    @SerialName("inventory_product_inventory_id") val inventoryId: String,
    @SerialName("inventory_product_catalog_product_id") val catalogProductId: String,
    @SerialName("inventory_product_quantity") val quantity: String = "",
)

@Serializable
data class InventoryProductCreatePayload(
    @SerialName("inventory_product_inventory_id") val inventoryId: String,
    @SerialName("inventory_product_catalog_product_id") val catalogProductId: String,
    @SerialName("inventory_product_quantity") val quantity: String = "",
)

// PUT /inventory-product/{id} — quantity is the only field that can change
// post-creation (the catalog product link and inventory are both
// fixed at creation time, no more re-categorization).
@Serializable
data class InventoryProductQuantityPayload(
    @SerialName("inventory_product_quantity") val quantity: String,
)

// Separate from InventoryProductQuantityPayload so updating thresholds/
// reminder can never accidentally touch quantity — mirrors the backend's
// dedicated PUT /api/v1/inventory-product/{id}/settings route.
@Serializable
data class InventoryProductSettingsPayload(
    @SerialName("inventory_product_first_threshold") val firstThreshold: String,
    @SerialName("inventory_product_second_threshold") val secondThreshold: String,
    @SerialName("inventory_product_reminder_threshold") val reminderThreshold: String,
    @SerialName("inventory_product_reminder_hour") val reminderHour: String,
    @SerialName("inventory_product_reminder_minute") val reminderMinute: String,
)

@Serializable
data class ListDto(
    @SerialName("list_id") val id: String,
    @SerialName("list_name") val name: String,
    @SerialName("list_owner_user_id") val ownerUserId: String = "",
)

@Serializable
data class ListPayload(
    @SerialName("list_name") val name: String,
)

@Serializable
data class ListShareDto(
    @SerialName("list_share_id") val id: String,
    @SerialName("list_share_list_id") val listId: String,
    @SerialName("list_share_user_id") val userId: String,
)

@Serializable
data class ListSharePayload(
    @SerialName("list_share_user_id") val userId: String,
)

// Covers both the plain create-response shape (list_item_id/list_item_list_id/
// list_item_catalog_product_id/list_item_note only — see web.ListItem in
// urs-backend) and the denormalized GET-list shape (adds the joined
// catalog_product/catalog_category names — see data.ListItem) with one type:
// the joined fields simply stay at their default on a create response.
// `list_item_checked` is gone entirely — adding/removing an item
// from a list is the only state transition now.
@Serializable
data class ListItemDto(
    @SerialName("list_item_id") val id: String,
    @SerialName("list_item_list_id") val listId: String,
    @SerialName("list_item_catalog_product_id") val catalogProductId: String,
    @SerialName("catalog_product_name") val productName: String = "",
    @SerialName("catalog_product_catalog_category_id") val categoryId: String = "",
    @SerialName("catalog_category_name") val categoryName: String = "",
    @SerialName("list_item_note") val note: String = "",
)

@Serializable
data class ListItemPayload(
    @SerialName("list_item_list_id") val listId: String,
    @SerialName("list_item_catalog_product_id") val catalogProductId: String,
    @SerialName("list_item_note") val note: String = "",
)

// Separate from ListItemPayload so an update can never accidentally touch
// listId/catalogProductId — mirrors InventoryProductSettingsPayload's split
// from InventoryProductQuantityPayload, and matches the backend's own PUT
// route, which only ever reads list_item_note from the body.
@Serializable
data class ListItemUpdatePayload(
    @SerialName("list_item_note") val note: String = "",
)

@Serializable
data class RecentlyUsedProductDto(
    @SerialName("catalog_product_id") val catalogProductId: String,
    @SerialName("last_used_at") val lastUsedAt: String,
)

@Serializable
data class WorkTimeBreakDto(
    @SerialName("work_time_break_id") val id: String = "",
    @SerialName("work_time_break_start_time") val startTime: String,
    @SerialName("work_time_break_end_time") val endTime: String,
)

@Serializable
data class WorkTimeEntryDto(
    @SerialName("work_time_entry_id") val id: String,
    @SerialName("work_time_entry_user_id") val userId: String,
    @SerialName("work_time_entry_date") val date: String,
    @SerialName("work_time_entry_work_start") val workStart: String,
    @SerialName("work_time_entry_work_end") val workEnd: String,
    // Empty string = no per-day override — mirrors the backend's
    // nullable-column-as-empty-string convention.
    @SerialName("work_time_entry_target_daily_hours") val targetDailyHours: String = "",
    @SerialName("breaks") val breaks: List<WorkTimeBreakDto> = emptyList(),
    // Computed server-side (see urs-backend's computeDailyTotals) — the
    // client never reimplements this formula.
    @SerialName("daily_total_hours") val dailyTotalHours: String = "",
    @SerialName("over_undertime_hours") val overUndertimeHours: String = "",
)

@Serializable
data class WorkTimeBreakPayload(
    @SerialName("work_time_break_start_time") val startTime: String,
    @SerialName("work_time_break_end_time") val endTime: String,
)

@Serializable
data class WorkTimeEntryPayload(
    @SerialName("work_time_entry_user_id") val userId: String,
    @SerialName("work_time_entry_date") val date: String,
    @SerialName("work_time_entry_work_start") val workStart: String,
    @SerialName("work_time_entry_work_end") val workEnd: String,
    @SerialName("work_time_entry_target_daily_hours") val targetDailyHours: String = "",
    @SerialName("breaks") val breaks: List<WorkTimeBreakPayload> = emptyList(),
)

// Only the fields the settings screen's default-daily-target-hours field
// needs — GET /api/v1/getuser returns the full user row, but nothing else in
// this app reads a user profile yet.
@Serializable
data class UserDto(
    @SerialName("user_id") val id: String,
    // Only used for display so far (UrsShareSheet's member picker, ) —
    // every other field below predates that and is read straight off the
    // full GET /getuser response, which also always includes this.
    @SerialName("user_name") val userName: String = "",
    @SerialName("user_default_daily_target_hours") val defaultDailyTargetHours: String = "",
    @SerialName("user_employment_percent") val employmentPercent: String = "",
    @SerialName("user_hourly_wage") val hourlyWage: String = "",
)

@Serializable
data class UserDefaultDailyTargetHoursPayload(
    @SerialName("user_default_daily_target_hours") val defaultDailyTargetHours: String,
)

@Serializable
data class UserEmploymentPercentPayload(
    @SerialName("user_employment_percent") val employmentPercent: String,
)

@Serializable
data class UserHourlyWagePayload(
    @SerialName("user_hourly_wage") val hourlyWage: String,
)

@Serializable
data class WorkTimeMonthOverrideDto(
    @SerialName("work_time_month_override_id") val id: String,
    @SerialName("work_time_month_override_user_id") val userId: String,
    @SerialName("work_time_month_override_year") val year: String,
    @SerialName("work_time_month_override_month") val month: String,
    @SerialName("work_time_month_override_days_worked") val daysWorked: String,
)

@Serializable
data class WorkTimeMonthOverridePayload(
    @SerialName("work_time_month_override_days_worked") val daysWorked: String,
)

@Serializable
data class BeerLogDto(
    @SerialName("beer_log_id") val id: String,
    @SerialName("beer_log_amount_ml") val amountMl: String,
    @SerialName("beer_log_date") val date: String,
)

@Serializable
data class BeerLogPayload(
    @SerialName("beer_log_amount_ml") val amountMl: String,
    @SerialName("beer_log_date") val date: String,
)

@Serializable
data class LoginPayload(
    @SerialName("user_name") val userName: String,
    @SerialName("password") val password: String,
)

@Serializable
data class RefreshPayload(
    @SerialName("refresh_token") val refreshToken: String,
)

// expires_in is a genuine JSON number on the backend (computed seconds, not
// a DB column) — unlike every other DTO in this file, which is a string
// because it mirrors a DB column the backend always serializes as one.
@Serializable
data class TokenResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Int,
)
