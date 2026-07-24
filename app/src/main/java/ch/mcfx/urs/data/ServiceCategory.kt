package ch.mcfx.urs.data

import ch.mcfx.urs.R

/**
 * The fixed service/maintenance categories, identical for every
 * [VehicleType] — stored as [ch.mcfx.urs.data.local.VehicleServiceTagEntity.code]
 * alongside any number of free-form custom tags (which use the
 * [CUSTOM_CODE] code with the free-typed text in `label`).
 */
enum class ServiceCategory(val code: String, val labelRes: Int) {
    OIL_CHANGE("oil_change", R.string.service_category_oil_change),
    TIRES("tires", R.string.service_category_tires),
    BRAKES("brakes", R.string.service_category_brakes),
    INSPECTION("inspection", R.string.service_category_inspection),
    BATTERY("battery", R.string.service_category_battery),
    BODYWORK("bodywork", R.string.service_category_bodywork),
    FILTERS("filters", R.string.service_category_filters),
    OTHER("other", R.string.service_category_other);

    companion object {
        const val CUSTOM_CODE = "custom"
    }
}
