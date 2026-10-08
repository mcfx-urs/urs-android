package ch.mcfx.urs.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Error body of the backend: `{"error": "<message>"}`, plus the complete
 * list of `clones` when a template cannot be deleted because products still
 * inherit from it.
 */
@Serializable
data class ApiErrorBody(
    val error: String = "",
    val clones: List<ApiCloneRef> = emptyList(),
)

@Serializable
data class ApiCloneRef(
    @SerialName("catalog_product_id") val id: String = "",
    @SerialName("catalog_product_name") val name: String = "",
)

private val errorJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/** The decoded backend error body of this response, or null if it has none. */
fun HttpException.apiError(): ApiErrorBody? =
    try {
        response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }?.let { errorJson.decodeFromString<ApiErrorBody>(it) }
    } catch (_: Exception) {
        null
    }
