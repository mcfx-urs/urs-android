package ch.mcfx.urs.data

import ch.mcfx.urs.data.remote.GenerateImagePayload
import ch.mcfx.urs.data.remote.UrsApi
import retrofit2.HttpException

/** A generated PNG plus the cost the backend reported for it, if any. */
class GeneratedImage(val png: ByteArray, val costUsd: Double?)

/**
 * Thin wrapper around the super-user-only generic image generator endpoint.
 * Returns the raw PNG bytes as received — nothing is cached locally and
 * nothing is persisted server-side (unlike [CatalogRepository.generateImage],
 * which feeds the catalog reuse/moderation pipeline). A non-2xx response
 * (e.g. 403 for a non-super-user, 502 on an upstream generation failure)
 * surfaces as a [retrofit2.HttpException] for the caller to map.
 */
class ImageGenRepository(private val api: UrsApi) {

    suspend fun generate(prompt: String, size: String, quality: String, background: String): GeneratedImage {
        val response = api.generateImage(
            GenerateImagePayload(prompt = prompt, size = size, quality = quality, background = background),
        )
        val body = response.body()
        if (!response.isSuccessful || body == null) throw HttpException(response)
        val png = body.use { it.bytes() }
        return GeneratedImage(png, response.headers()["X-Image-Cost-USD"]?.toDoubleOrNull())
    }
}
