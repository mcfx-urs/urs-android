package ch.mcfx.urs.data

import ch.mcfx.urs.data.remote.GenerateImagePayload
import ch.mcfx.urs.data.remote.UrsApi

/**
 * Thin wrapper around the super-user-only generic image generator endpoint.
 * Returns the raw PNG bytes as received — nothing is cached locally and
 * nothing is persisted server-side (unlike [CatalogRepository.generateImage],
 * which feeds the catalog reuse/moderation pipeline). A non-2xx response
 * (e.g. 403 for a non-super-user, 502 on an upstream generation failure)
 * surfaces as a [retrofit2.HttpException] for the caller to map.
 */
class ImageGenRepository(private val api: UrsApi) {

    suspend fun generate(prompt: String, size: String, quality: String, background: String): ByteArray =
        api.generateImage(
            GenerateImagePayload(prompt = prompt, size = size, quality = quality, background = background),
        ).use { it.bytes() }
}
