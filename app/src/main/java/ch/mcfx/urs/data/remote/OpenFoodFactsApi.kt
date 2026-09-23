package ch.mcfx.urs.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Open Food Facts (mcfx-urs/urs-android#91) — called directly from this app,
 * never proxied through urs-backend (see
 * urs/BARCODE-SCAN-INVENTORY-FINDINGS.md's settled reasoning: the backend is
 * only VPN-reachable, a proxy hop would make this feature depend on that for
 * no benefit). Public, unauthenticated API — its own Retrofit/OkHttp client
 * in [ch.mcfx.urs.UrsApplication], no auth interceptor.
 */
interface OpenFoodFactsApi {
    // `fields` keeps the response small — the unfiltered response is huge,
    // mostly irrelevant metadata.
    @GET("api/v2/product/{barcode}.json")
    suspend fun getProduct(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = "status,product_name,product_name_de,brands,quantity",
    ): OffProductResponse
}

@Serializable
data class OffProductResponse(
    // 1 = found, 0 = not found — the reliable match/no-match signal, not
    // just whether `product` is present/non-empty.
    val status: Int = 0,
    val product: OffProduct? = null,
)

@Serializable
data class OffProduct(
    @SerialName("product_name") val productName: String = "",
    // Prefer this over productName when non-empty — OFF entries are often
    // only in French/English even for Swiss products.
    @SerialName("product_name_de") val productNameDe: String = "",
    val brands: String = "",
    val quantity: String = "",
)
