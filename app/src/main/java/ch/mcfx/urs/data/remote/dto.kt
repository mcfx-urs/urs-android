package ch.mcfx.urs.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// All values are strings because the backend serializes every DB column as a
// string. Typed cleanup is planned together with the Phase 2 sync work.

@Serializable
data class VehicleDto(
    @SerialName("vehicle_id") val id: String,
    @SerialName("vehicle_fuel_id") val fuelId: String,
    @SerialName("fuel_name") val fuelName: String,
    @SerialName("vehicle_brand") val brand: String,
    @SerialName("vehicle_model") val model: String,
    @SerialName("vehicle_year") val year: String,
    @SerialName("vehicle_engine_code") val engineCode: String = "",
    @SerialName("vehicle_type") val vehicleType: String = "car",
    @SerialName("vehicle_color") val color: String = "",
    @SerialName("vehicle_vin") val vin: String = "",
    @SerialName("vehicle_registration_number") val registrationNumber: String = "",
    @SerialName("vehicle_type_approval_number") val typeApprovalNumber: String = "",
    @SerialName("vehicle_displacement_ccm") val displacementCcm: String = "",
    @SerialName("vehicle_power_kw") val powerKw: String = "",
    @SerialName("vehicle_power_ps") val powerPs: String = "",
    @SerialName("vehicle_weight_kg") val weightKg: String = "",
    @SerialName("vehicle_first_registration_date") val firstRegistrationDate: String = "",
    @SerialName("vehicle_last_mfk_date") val lastMfkDate: String = "",
)

// Used for both create (POST) and update (PUT) — the backend's Vehicle
// request DTO takes the same shape for both (see web.Vehicle in
// urs-backend), unlike Fill which has a separate FillUpdatePayload.
@Serializable
data class VehiclePayload(
    @SerialName("vehicle_fuel_id") val fuelId: String,
    @SerialName("vehicle_brand") val brand: String,
    @SerialName("vehicle_model") val model: String,
    @SerialName("vehicle_year") val year: String,
    @SerialName("vehicle_engine_code") val engineCode: String = "",
    @SerialName("vehicle_type") val vehicleType: String = "car",
    @SerialName("vehicle_color") val color: String = "",
    @SerialName("vehicle_vin") val vin: String = "",
    @SerialName("vehicle_registration_number") val registrationNumber: String = "",
    @SerialName("vehicle_type_approval_number") val typeApprovalNumber: String = "",
    @SerialName("vehicle_displacement_ccm") val displacementCcm: String = "",
    @SerialName("vehicle_power_kw") val powerKw: String = "",
    @SerialName("vehicle_power_ps") val powerPs: String = "",
    @SerialName("vehicle_weight_kg") val weightKg: String = "",
    @SerialName("vehicle_first_registration_date") val firstRegistrationDate: String = "",
    @SerialName("vehicle_last_mfk_date") val lastMfkDate: String = "",
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
    @SerialName("fill_vehicle_id") val vehicleId: String,
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
    @SerialName("vehicle_id") val vehicleId: String,
    @SerialName("driven") val driven: String,
)

@Serializable
data class FillingStationPayload(
    @SerialName("filling_station_name") val name: String,
    @SerialName("filling_station_address") val address: String = "",
    @SerialName("filling_station_latitude") val latitude: String = "",
    @SerialName("filling_station_longitude") val longitude: String = "",
)

// A computed geocoding result, not a DB-mirroring column — real Double,
// unlike this file's usual all-String convention (see the top-of-file
// comment).
@Serializable
data class GeocodeResultDto(
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
)

@Serializable
data class FillPayload(
    @SerialName("fill_date") val date: String,
    @SerialName("fill_vehicle_id") val vehicleId: String,
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

// Separate from FillPayload (no driven/stationCounter/GPS fields) — mirrors
// ListItemUpdatePayload's split from ListItemPayload. The backend's
// `PUT /api/v1/fill/{id}` route ignores driven/filling_station_counter
// entirely and has no ad-hoc-station-creation branch, so fill_station_id is
// always a known station here.
@Serializable
data class FillUpdatePayload(
    @SerialName("fill_date") val date: String,
    @SerialName("fill_vehicle_id") val vehicleId: String,
    @SerialName("fill_station_id") val stationId: String,
    @SerialName("fill_fuel_id") val fuelId: String,
    @SerialName("fill_price") val pricePerLiter: String,
    @SerialName("fill_amount") val liters: String,
    @SerialName("fill_odometer") val odometer: String,
    @SerialName("fill_currency_code") val currencyCode: String = "CHF",
    @SerialName("fill_is_full_tank") val isFullTank: String = "1",
)

@Serializable
data class FuelDto(
    @SerialName("fuel_id") val id: String,
    @SerialName("fuel_name") val name: String,
)

// Records a price observed at a known station, independent of a fill-up
// (e.g. seen while driving past without stopping). Rejected server-side for
// an unknown or ad-hoc (SOURCE_GPS_AUTO) station — same reusable-station
// requirement as the auto-record-on-fill-up path.
@Serializable
data class FuelPricePayload(
    val date: String,
    val price: String,
    @SerialName("fuel_type_id") val fuelTypeId: String,
    @SerialName("station_id") val stationId: String,
)

@Serializable
data class FuelPriceDto(
    val id: String,
    val date: String,
    val price: String,
    @SerialName("fuel_type_id") val fuelTypeId: String,
    @SerialName("station_id") val stationId: String,
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

// Rename/re-link a manually-created category (source='manual' only,
// enforced server-side) — "" clears the image link, same nullable-as-empty
// convention as the rest of this file.
@Serializable
data class CatalogCategoryUpdatePayload(
    @SerialName("catalog_category_name") val name: String,
    @SerialName("catalog_category_catalog_image_id") val catalogImageId: String = "",
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
    // (1 = most recent) — global per catalog product, moved off the
    // old per-household inventory_product, shifted 1→2→3 server-side each
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
    // true rejects with 409 instead of silently reusing an existing row
    // (any source) with the same name — Product Management's "New
    // product" form sets this; the shopping-list/inventory quick-add flow
    // leaves it false to keep its intentional find-or-create dedup
    // behavior (see urs-backend's CreateCatalogProductStrict).
    @SerialName("require_new") val requireNew: Boolean = false,
)

@Serializable
data class NewCatalogProductResponseDto(
    @SerialName("catalog_product_id") val id: String,
    @SerialName("catalog_product_name") val name: String,
    @SerialName("catalog_product_catalog_category_id") val catalogCategoryId: String = "",
)

// Rename/re-link a manually-created product (source='manual' only,
// enforced server-side) — "" clears a link, same nullable-as-empty
// convention as the rest of this file.
@Serializable
data class CatalogProductUpdatePayload(
    @SerialName("catalog_product_name") val name: String,
    @SerialName("catalog_product_catalog_category_id") val catalogCategoryId: String = "",
    @SerialName("catalog_product_catalog_image_id") val catalogImageId: String = "",
)

// One entry from the reusable-image picker (GET /api/v1/catalog-image) —
// also reused for the admin pending-review list and the response of
// generateCatalogImage, which only ever fill a subset of these
// fields; the rest fall back to their defaults.
@Serializable
data class CatalogImageDto(
    @SerialName("catalog_image_id") val id: String,
    @SerialName("catalog_image_source_image_name") val sourceImageName: String = "",
    @SerialName("catalog_image_extension") val extension: String = "",
    // "approved" or "pending_review" — always "approved" for rows
    // from the reuse picker, meaningful for the admin pending list.
    @SerialName("catalog_image_status") val status: String = "",
    @SerialName("catalog_image_linked_names") val linkedNames: String = "",
)

// Request body for generateCatalogImage — description is the
// optional free-text field from the product form; the backend falls back
// to productName as the prompt subject when it's blank.
@Serializable
data class GenerateCatalogImagePayload(
    @SerialName("product_name") val productName: String,
    @SerialName("description") val description: String = "",
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
// post-creation — the catalog product link and inventory are both
// fixed at creation time, no more re-categorization.
@Serializable
data class InventoryProductQuantityPayload(
    @SerialName("inventory_product_quantity") val quantity: String,
    // Basis timestamp for the backend's last-write-wins guard: the update
    // lands only if the row wasn't changed since this moment, else 409.
    @SerialName("inventory_product_updated_at") val updatedAt: String = "",
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
    // Only sent (and only read by the backend) on PUT: basis timestamp for
    // the last-write-wins guard. Null on create.
    @SerialName("list_updated_at") val updatedAt: String? = null,
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
    @SerialName("list_item_quantity") val quantity: Int? = null,
    @SerialName("list_item_on_sale") val onSale: Boolean = false,
)

@Serializable
data class ListItemPayload(
    @SerialName("list_item_list_id") val listId: String,
    @SerialName("list_item_catalog_product_id") val catalogProductId: String,
    @SerialName("list_item_note") val note: String = "",
    @SerialName("list_item_quantity") val quantity: Int? = null,
    @SerialName("list_item_on_sale") val onSale: Boolean = false,
)

// Separate from ListItemPayload so an update can never accidentally touch
// listId/catalogProductId — mirrors InventoryProductSettingsPayload's split
// from InventoryProductQuantityPayload, and matches the backend's own PUT
// route, which reads list_item_note/list_item_quantity/list_item_on_sale
// from the body.
@Serializable
data class ListItemUpdatePayload(
    @SerialName("list_item_note") val note: String = "",
    @SerialName("list_item_quantity") val quantity: Int? = null,
    @SerialName("list_item_on_sale") val onSale: Boolean = false,
    // Basis timestamp for the backend's last-write-wins guard.
    @SerialName("list_item_updated_at") val updatedAt: String = "",
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
    // "1"/"0", same string-typed-boolean convention as fill_is_full_tank —
    // the almost-always-paid 15-minute morning break, added on top of the
    // work span rather than logged as a break (see WorkTimeEntryEntity).
    @SerialName("work_time_entry_paid_break") val paidBreak: String = "1",
    // "1"/"0" — CHF 18.- meal allowance owed for the day, see
    // WorkTimeCalculations.computeWage.
    @SerialName("work_time_entry_meal_allowance") val mealAllowance: String = "0",
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
    // Always sent explicitly (no default) — unlike fill_is_full_tank this
    // field's "on" state is the common case, so relying on
    // encodeDefaults-omission would send nothing for most entries and the
    // backend's Go zero value (false) would silently disagree with it.
    @SerialName("work_time_entry_paid_break") val paidBreak: String,
    @SerialName("work_time_entry_meal_allowance") val mealAllowance: String,
    @SerialName("breaks") val breaks: List<WorkTimeBreakPayload> = emptyList(),
)

// Only the fields the settings screen's default-daily-target-hours field
// needs — GET /api/v1/getuser returns the full user row, but nothing else in
// this app reads a user profile yet.
@Serializable
data class UserDto(
    @SerialName("user_id") val id: String,
    // Only used for display so far (UrsShareSheet's member picker) —
    // every other field below predates that and is read straight off the
    // full GET /getuser response, which also always includes this.
    @SerialName("user_name") val userName: String = "",
    @SerialName("user_default_daily_target_hours") val defaultDailyTargetHours: String = "",
    @SerialName("user_employment_percent") val employmentPercent: String = "",
    @SerialName("user_hourly_wage") val hourlyWage: String = "",
    @SerialName("user_vacation_pay_surcharge_percent") val vacationPaySurchargePercent: String = "",
    @SerialName("user_holiday_surcharge_percent") val holidaySurchargePercent: String = "",
    @SerialName("user_thirteenth_month_surcharge_percent") val thirteenthMonthSurchargePercent: String = "",
    @SerialName("user_ahv_iv_eo_deduction_percent") val ahvIvEoDeductionPercent: String = "",
    @SerialName("user_alv_deduction_percent") val alvDeductionPercent: String = "",
    @SerialName("user_suva_nbu_deduction_percent") val suvaNbuDeductionPercent: String = "",
    @SerialName("user_ktg_deduction_percent") val ktgDeductionPercent: String = "",
    @SerialName("user_bvg_deduction_amount") val bvgDeductionAmount: String = "",
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
data class UserWageRulesPayload(
    @SerialName("user_vacation_pay_surcharge_percent") val vacationPaySurchargePercent: String,
    @SerialName("user_holiday_surcharge_percent") val holidaySurchargePercent: String,
    @SerialName("user_thirteenth_month_surcharge_percent") val thirteenthMonthSurchargePercent: String,
    @SerialName("user_ahv_iv_eo_deduction_percent") val ahvIvEoDeductionPercent: String,
    @SerialName("user_alv_deduction_percent") val alvDeductionPercent: String,
    @SerialName("user_suva_nbu_deduction_percent") val suvaNbuDeductionPercent: String,
    @SerialName("user_ktg_deduction_percent") val ktgDeductionPercent: String,
    @SerialName("user_bvg_deduction_amount") val bvgDeductionAmount: String,
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

// Matches urs-backend's LocationHistory struct (src/web/locationHistory.go):
// every value is a string, same DB-column-as-a-string convention as every
// other DTO in this file. capturedAt uses "yyyy-MM-dd HH:mm:ss" (device
// local time, no timezone), matching BeerStats.DATE_FORMAT's convention for
// a combined date+time value sent to this backend — the backend parses it
// with Go's "2006-01-02 15:04:05" layout (see SelectLocationHistory).
@Serializable
data class LocationHistoryPayload(
    @SerialName("location_history_latitude") val latitude: String,
    @SerialName("location_history_longitude") val longitude: String,
    @SerialName("location_history_accuracy_m") val accuracyMeters: String = "",
    @SerialName("location_history_captured_at") val capturedAt: String,
)

// Kept to just the server-assigned id, the only field SyncManager actually
// reconciles against locally (see SyncManager.replayCreateLocationHistory).
@Serializable
data class LocationHistoryResponse(
    @SerialName("location_history_id") val id: String,
)

// GET /api/v1/location-history's response shape — same string-typed fields
// as LocationHistoryPayload, plus the server-assigned id (see
// LocationHistoryRepository.toEntity).
@Serializable
data class LocationHistoryDto(
    @SerialName("location_history_id") val id: String,
    @SerialName("location_history_latitude") val latitude: String,
    @SerialName("location_history_longitude") val longitude: String,
    @SerialName("location_history_accuracy_m") val accuracyMeters: String = "",
    @SerialName("location_history_captured_at") val capturedAt: String,
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

@Serializable
data class ChangePasswordPayload(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String,
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

// Same shape for request and response — the backend's VehicleServiceTag
// (urs-backend, src/web/vehicleService.go) deliberately uses these full
// field names on both sides, not a shorter code/label pair.
@Serializable
data class VehicleServiceTagDto(
    @SerialName("vehicle_service_tag_code") val code: String,
    @SerialName("vehicle_service_tag_label") val label: String = "",
)

@Serializable
data class VehicleServiceDto(
    @SerialName("vehicle_service_id") val id: String,
    @SerialName("vehicle_service_vehicle_id") val vehicleId: String,
    @SerialName("vehicle_service_date") val date: String,
    @SerialName("vehicle_service_odometer") val odometer: String,
    @SerialName("vehicle_service_provider") val provider: String = "",
    @SerialName("vehicle_service_is_diy") val isDiy: String = "0",
    @SerialName("vehicle_service_notes") val notes: String = "",
    @SerialName("vehicle_service_cost_amount") val costAmount: String,
    @SerialName("vehicle_service_currency_code") val currencyCode: String = "CHF",
    @SerialName("vehicle_service_cost_amount_chf") val costAmountChf: String = "",
    @SerialName("vehicle_service_cost_amount_chf_locked") val costAmountChfLocked: String = "0",
    val tags: List<VehicleServiceTagDto> = emptyList(),
)

// Used for both create (POST) and update (PUT) — same shape on the
// backend (web.VehicleServiceRequest).
@Serializable
data class VehicleServicePayload(
    @SerialName("vehicle_service_vehicle_id") val vehicleId: String,
    @SerialName("vehicle_service_date") val date: String,
    @SerialName("vehicle_service_odometer") val odometer: String,
    @SerialName("vehicle_service_provider") val provider: String = "",
    @SerialName("vehicle_service_is_diy") val isDiy: String = "0",
    @SerialName("vehicle_service_notes") val notes: String = "",
    @SerialName("vehicle_service_cost_amount") val costAmount: String,
    @SerialName("vehicle_service_currency_code") val currencyCode: String = "CHF",
    val tags: List<VehicleServiceTagDto> = emptyList(),
)

@Serializable
data class BakePlanStepDto(
    @SerialName("bake_plan_step_id") val id: String,
    @SerialName("bake_plan_step_index") val index: String,
    @SerialName("bake_plan_step_label") val label: String,
    @SerialName("bake_plan_step_planned_at") val plannedAt: String,
    @SerialName("bake_plan_step_snoozed_at") val snoozedAt: String = "",
    @SerialName("bake_plan_step_done_at") val doneAt: String = "",
)

@Serializable
data class BakePlanDto(
    @SerialName("bake_plan_id") val id: String,
    @SerialName("bake_plan_user_id") val userId: String = "",
    @SerialName("bake_plan_template_key") val templateKey: String,
    @SerialName("bake_plan_anchor_at") val anchorAt: String,
    @SerialName("bake_plan_status") val status: String,
    @SerialName("bake_plan_completed_at") val completedAt: String = "",
    val steps: List<BakePlanStepDto> = emptyList(),
)

@Serializable
data class BakePlanStepCreatePayload(
    val index: String,
    val label: String,
    @SerialName("planned_at") val plannedAt: String,
)

@Serializable
data class BakePlanCreatePayload(
    @SerialName("template_key") val templateKey: String,
    @SerialName("anchor_at") val anchorAt: String,
    val steps: List<BakePlanStepCreatePayload>,
)

// Both fields optional — a client sends whichever it wants to change
// (done, snoozed_at, or both) in one request, matching the backend's single
// PATCH .../steps/{stepId} endpoint (web.BakePlanStepPatchRequest).
@Serializable
data class BakePlanStepPatchPayload(
    val done: Boolean? = null,
    @SerialName("snoozed_at") val snoozedAt: String? = null,
)

@Serializable
data class NoteDto(
    @SerialName("note_id") val id: String,
    @SerialName("note_user_id") val userId: String = "",
    @SerialName("note_title") val title: String,
    @SerialName("note_content") val content: String = "",
    @SerialName("note_reminder_at") val reminderAt: String = "",
    @SerialName("note_status") val status: String,
    @SerialName("note_completed_at") val completedAt: String = "",
    val tags: List<String> = emptyList(),
)

// Shared by POST /api/v1/note (create) and PUT /api/v1/note/{id} (update) —
// same body shape both ways (web.NoteCreateRequest).
@Serializable
data class NoteCreatePayload(
    val title: String,
    val content: String,
    @SerialName("reminder_at") val reminderAt: String? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
data class NoteStatusPatchPayload(
    val status: String,
)
