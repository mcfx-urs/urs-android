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
    @SerialName("fill_station_id") val stationId: String,
    @SerialName("fill_fuel_id") val fuelId: String,
    @SerialName("fill_price") val pricePerLiter: String,
    @SerialName("fill_amount") val liters: String,
    @SerialName("fill_odometer") val odometer: String,
    @SerialName("driven") val driven: String,
    @SerialName("filling_station_counter") val stationCounter: String,
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
