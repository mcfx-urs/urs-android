package ch.mcfx.urs.data.sync

import java.util.concurrent.atomic.AtomicLong
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer

/**
 * Counts the response body bytes received on the wire (compressed size if the
 * response is gzipped) while a pull step is active. Pull steps run one after
 * another, but a request made by a screen at the same time is counted too, so
 * the figure is an upper bound for the step.
 */
class PullByteCounter {
    @Volatile
    private var active = false
    private val bytes = AtomicLong(0)

    fun start() {
        bytes.set(0)
        active = true
    }

    /** Stops counting and returns the bytes received since [start]. */
    fun stop(): Long {
        active = false
        return bytes.get()
    }

    /** Network interceptor: wraps response bodies so every byte read is counted. */
    val interceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        wrap(response)
    }

    private fun wrap(response: Response): Response {
        val body = response.body
        if (!active) return response
        val counting = object : ForwardingSource(body.source()) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                val read = super.read(sink, byteCount)
                if (read > 0 && active) bytes.addAndGet(read)
                return read
            }
        }
        return response.newBuilder()
            .body(counting.buffer().asResponseBody(body.contentType(), body.contentLength()))
            .build()
    }
}
