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
    @SerialName("station_latitude") val stationLatitude: String? = null,
    @SerialName("station_longitude") val stationLongitude: String? = null,
)

@Serializable
data class CurrencyDto(
    @SerialName("currency_code") val code: String,
    @SerialName("currency_name") val name: String,
)

@Serializable
data class InventoryCategoryDto(
    @SerialName("inventory_category_id") val id: String,
    @SerialName("inventory_category_name") val name: String,
)

@Serializable
data class InventoryProductDto(
    @SerialName("inventory_product_id") val id: String,
    @SerialName("inventory_product_category_id") val categoryId: String,
    @SerialName("inventory_product_name") val name: String,
    @SerialName("inventory_product_quantity") val quantity: String,
    // Empty string = not set for every field below (mirrors the backend's
    // nullable-column-as-empty-string convention, e.g. user_height).
    @SerialName("inventory_product_first_threshold") val firstThreshold: String = "",
    @SerialName("inventory_product_second_threshold") val secondThreshold: String = "",
    @SerialName("inventory_product_reminder_threshold") val reminderThreshold: String = "",
    @SerialName("inventory_product_reminder_hour") val reminderHour: String = "",
    @SerialName("inventory_product_reminder_minute") val reminderMinute: String = "",
)

@Serializable
data class InventoryCategoryPayload(
    @SerialName("inventory_category_name") val name: String,
)

@Serializable
data class InventoryProductPayload(
    @SerialName("inventory_product_category_id") val categoryId: String,
    @SerialName("inventory_product_name") val name: String,
    @SerialName("inventory_product_quantity") val quantity: String,
)

// Separate from InventoryProductPayload so updating thresholds/reminder can
// never accidentally touch category/name/quantity — mirrors the backend's
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
