package ch.mcfx.urs.data.sync

import android.util.Log
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/** Result of one domain's `refreshFromBackend()`: empty [causes] means the pull succeeded. */
class PullOutcome private constructor(val causes: List<String>) {
    val ok: Boolean get() = causes.isEmpty()

    operator fun plus(other: PullOutcome): PullOutcome =
        if (ok && other.ok) OK else PullOutcome((causes + other.causes).distinct())

    companion object {
        val OK = PullOutcome(emptyList())

        fun failed(cause: String) = PullOutcome(listOf(cause))
    }
}

/** Short, language-neutral failure cause: timeout, HTTP code, I/O, parse, or the exception type. */
fun describePullFailure(e: Throwable): String = when (e) {
    is SocketTimeoutException -> "timeout"
    is HttpException -> "HTTP ${e.code()}"
    is SerializationException -> "parse"
    is IOException -> "I/O"
    else -> e::class.java.simpleName
}

/** Runs [block] best-effort: a failure is logged and returned as a cause, never thrown (cancellation excepted). */
suspend fun pullCatching(tag: String, block: suspend () -> Unit): PullOutcome =
    try {
        block()
        PullOutcome.OK
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(tag, "refreshFromBackend failed", e)
        PullOutcome.failed(describePullFailure(e))
    }
