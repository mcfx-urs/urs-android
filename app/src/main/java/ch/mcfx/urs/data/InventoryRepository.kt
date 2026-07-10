package ch.mcfx.urs.data

import ch.mcfx.urs.data.remote.InventoryCategoryDto
import ch.mcfx.urs.data.remote.InventoryCategoryPayload
import ch.mcfx.urs.data.remote.InventoryProductDto
import ch.mcfx.urs.data.remote.InventoryProductPayload
import ch.mcfx.urs.data.remote.InventoryProductSettingsPayload
import ch.mcfx.urs.data.remote.UrsApi
import kotlinx.serialization.SerializationException

class InventoryRepository(private val api: UrsApi) {

    suspend fun getCategories(): List<InventoryCategoryDto> =
        emptyAsNull { api.getInventoryCategories() }.sortedBy { it.name.alphabeticSortKey() }

    suspend fun createCategory(name: String) {
        api.createInventoryCategory(InventoryCategoryPayload(name = name))
    }

    suspend fun deleteCategory(id: String) {
        api.deleteInventoryCategory(id)
    }

    suspend fun getProducts(categoryId: String): List<InventoryProductDto> =
        emptyAsNull { api.getInventoryProducts(categoryId) }.sortedBy { it.name.alphabeticSortKey() }

    suspend fun createProduct(categoryId: String, name: String) {
        api.createInventoryProduct(InventoryProductPayload(categoryId = categoryId, name = name, quantity = "0"))
    }

    suspend fun updateProductQuantity(product: InventoryProductDto, newQuantity: Int) {
        api.updateInventoryProduct(
            product.id,
            InventoryProductPayload(categoryId = product.categoryId, name = product.name, quantity = newQuantity.toString()),
        )
    }

    suspend fun deleteProduct(id: String) {
        api.deleteInventoryProduct(id)
    }

    // Thresholds/reminder are deliberately a separate call from
    // updateProductQuantity (different backend route entirely) so neither
    // ever risks clobbering the other's fields.
    suspend fun updateProductSettings(
        productId: String,
        firstThreshold: Int?,
        secondThreshold: Int?,
        reminderThreshold: Int?,
        reminderHour: Int?,
        reminderMinute: Int?,
    ) {
        api.updateInventoryProductSettings(
            productId,
            InventoryProductSettingsPayload(
                firstThreshold = firstThreshold?.toString().orEmpty(),
                secondThreshold = secondThreshold?.toString().orEmpty(),
                reminderThreshold = reminderThreshold?.toString().orEmpty(),
                reminderHour = reminderHour?.toString().orEmpty(),
                reminderMinute = reminderMinute?.toString().orEmpty(),
            ),
        )
    }

    // Used by ReminderScheduler's conditional-fire check: looks up a single
    // product's current quantity by re-fetching its category's product list
    // (no dedicated "get product by id" backend route exists, and adding one
    // just for this would be solving a problem the existing endpoint already
    // covers).
    suspend fun getProductQuantity(categoryId: String, productId: String): Int? =
        getProducts(categoryId).find { it.id == productId }?.quantity?.toIntOrNull()

    // The backend encodes empty result sets as JSON `null` instead of `[]`.
    private suspend fun <T> emptyAsNull(call: suspend () -> List<T>): List<T> =
        try {
            call()
        } catch (_: SerializationException) {
            emptyList()
        }
}

// The backend sorts alphabetically on the raw name, which would clump any
// emoji-prefixed name together by codepoint instead of alphabetizing by the
// letter that follows it (e.g. "🍖Kitchen" next to other emoji, not next to
// other K's) — some categories are expected to have an emoji prefix, some
// not, so sorting needs to skip past any leading non-letter/non-digit
// codepoints (emoji or otherwise) before comparing.
private fun String.alphabeticSortKey(): String {
    var charIndex = 0
    val codePoints = codePoints().toArray()
    for (codePoint in codePoints) {
        if (Character.isLetterOrDigit(codePoint)) {
            return substring(charIndex).lowercase()
        }
        charIndex += Character.charCount(codePoint)
    }
    return lowercase()
}
