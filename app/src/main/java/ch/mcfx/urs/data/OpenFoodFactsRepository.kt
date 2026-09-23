package ch.mcfx.urs.data

import ch.mcfx.urs.data.remote.OpenFoodFactsApi
import kotlinx.coroutines.CancellationException

/**
 * Thin wrapper over [OpenFoodFactsApi] — the barcode-scan "no local catalog
 * match" fallback (mcfx-urs/urs-android#91), shared by Inventory's and
 * Shopping List's add-product flows.
 */
class OpenFoodFactsRepository(private val api: OpenFoodFactsApi) {

    data class Result(val name: String, val brands: String)

    /** Returns `null` on no match (status != 1), a blank resolved name, or any failure (offline etc.) — all fall through to fully-manual entry alike. */
    suspend fun lookup(barcode: String): Result? {
        val response = try {
            api.getProduct(barcode)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
        if (response.status != 1) return null
        val product = response.product ?: return null
        val name = product.productNameDe.ifBlank { product.productName }
        if (name.isBlank()) return null
        return Result(name = name, brands = product.brands)
    }
}
