package ch.mcfx.urs.watchrelay

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import ch.mcfx.urs.MainActivity
import ch.mcfx.urs.R
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.beer.BeerStats
import ch.mcfx.urs.notifications.NotificationChannels
import fi.iki.elonen.NanoHTTPD
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val RELAY_PORT = 8787
private const val BEER_FILL_PATH = "/api/watch/beer-fill"
private const val CHORE_EVENT_PATH = "/api/watch/chore-event"
private const val CHORE_TYPES_PATH = "/api/watch/chore-types"
private const val AUDIO_NOTE_CHUNK_PATH = "/api/watch/audio-note-chunk"
private const val TOKEN_HEADER = "x-relay-token"
// The watch has no direct network access and the Zepp companion service
// cannot read a transferred file's bytes, so the watch reads its own
// recording back and uploads it base64-encoded over the relay instead.
private const val AUDIO_ENCODING_HEADER = "x-audio-encoding"
private const val UPLOAD_ID_HEADER = "x-upload-id"
private const val CHUNK_INDEX_HEADER = "x-chunk-index"
private const val CHUNK_COUNT_HEADER = "x-chunk-count"
// Recording start time (watch clock, epoch millis) — the watch names its
// file from this same value at recording start, so it survives the relay
// as the note's real timestamp instead of upload time.
private const val RECORDED_AT_HEADER = "x-recorded-at"
private const val VOLUME_PARAM = "volume"
private const val TYPE_ID_PARAM = "typeId"
// A chunk's base64 body is ~32 KB (the watch slices 24 KB raw per chunk) —
// this is a per-request cap, not the note length limit.
private const val AUDIO_NOTE_CHUNK_MAX_BYTES = 64 * 1024
private const val MAX_CHUNK_COUNT = 1024
private const val CHUNK_UPLOAD_TTL_MILLIS = 10 * 60 * 1000L
private const val NOTIFICATION_ID = 1

/**
 * Foreground service hosting a loopback-only HTTP server so the `urs-zepp`
 * watch app's Bluetooth-relayed requests (via its Zepp App side-service,
 * which does have real network access unlike the watch itself) can trigger
 * a backend write without the watch or the Zepp App ever needing
 * urs-backend's WireGuard tunnel or JWT session. Each endpoint hands off
 * to the same offline-first repository the in-app UI uses: the write is
 * queued locally and delivered by `SyncManager` when connectivity allows,
 * so the relay never blocks on — or fails because of — network/VPN state.
 * The first persistent [Service] in this codebase — opt-in via Settings,
 * matching [ch.mcfx.urs.location.LocationHistorySettingsStore]'s toggle
 * pattern.
 */
class WatchRelayService : Service() {

    private var server: NanoHTTPD? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        val container = (application as UrsApplication).container
        server = RelayHttpServer(
            onBeerFill = { volumeMl ->
                container.beerRepository.logBeer(volumeMl, LocalDateTime.now().format(BeerStats.DATE_FORMAT))
            },
            onChoreEvent = { typeId ->
                container.choreRepository.logEvent(
                    typeId = typeId,
                    occurredOn = LocalDate.now().toString(),
                    occurredAt = null,
                    note = null,
                    source = "watch",
                )
            },
            onListChoreTypes = {
                container.choreRepository.listActiveTypesForWatch()
                    .map { (typeId, name) -> WatchChoreType(typeId = typeId, name = name) }
            },
            // Convert the watch recorder's raw output to a standard Ogg-Opus
            // file, store it, and record the row. A conversion failure throws,
            // which the server turns into a non-2xx so the watch retries.
            onAudioNote = { bytes, recordedAtMillis ->
                val note = container.audioNoteRepository.saveFromWatch(bytes, recordedAtMillis)
                android.util.Log.i(
                    "WatchRelay",
                    "audio note saved: ${note.filePath} (${bytes.size} watch bytes, ${note.durationMs} ms)",
                )
            },
        ).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithNotification() {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, NotificationChannels.WATCH_RELAY)
            .setSmallIcon(R.drawable.ic_notification_bear)
            .setContentTitle(getString(R.string.watch_relay_notification_title))
            .setContentText(getString(R.string.watch_relay_notification_body))
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, WatchRelayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchRelayService::class.java))
        }
    }
}

/** One chore type as the watch menu needs it — see [CHORE_TYPES_PATH]. */
@Serializable
private data class WatchChoreType(val typeId: String, val name: String)

/**
 * Bound to `127.0.0.1` only — reachable from any app on the same phone (not
 * just the Zepp App), not from the network. [TOKEN_HEADER] is the only
 * guard against another local app blind-posting fake fills, see
 * [WATCH_RELAY_TOKEN]'s doc comment for why that's proportionate here.
 * [NanoHTTPD.serve] runs synchronously on one of NanoHTTPD's own worker
 * threads (never the main thread), so blocking it with [runBlocking] to
 * call the suspend repository/gate functions is safe and keeps the request
 * genuinely synchronous — the watch app's `httpRequest` call is waiting on
 * exactly this response.
 */
private class RelayHttpServer(
    private val onBeerFill: suspend (volumeMl: Int) -> Unit,
    private val onChoreEvent: suspend (typeId: String) -> Unit,
    private val onListChoreTypes: suspend () -> List<WatchChoreType>,
    private val onAudioNote: suspend (bytes: ByteArray, recordedAtMillis: Long?) -> Unit,
) : NanoHTTPD("127.0.0.1", RELAY_PORT) {

    private val postPaths = setOf(BEER_FILL_PATH, CHORE_EVENT_PATH, AUDIO_NOTE_CHUNK_PATH)
    private val json = Json

    // Chunked-upload reassembly state, keyed by the watch's x-upload-id.
    // In-memory only — a dropped connection or app restart mid-upload just
    // means the watch's retry starts a fresh entry. Access is synchronized
    // because NanoHTTPD serves each request on its own worker thread; the
    // watch itself only ever has one upload in flight, but this guards
    // against overlap regardless.
    private val chunkUploads = HashMap<String, ChunkUpload>()

    override fun serve(session: IHTTPSession): Response {
        val isKnownPost = session.method == Method.POST && session.uri in postPaths
        val isChoreTypesGet = session.method == Method.GET && session.uri == CHORE_TYPES_PATH
        if (!isKnownPost && !isChoreTypesGet) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")
        }
        if (session.headers[TOKEN_HEADER] != WATCH_RELAY_TOKEN) {
            return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "unauthorized")
        }

        // Read-only: the watch fetches the chore-type list to build its menu.
        if (isChoreTypesGet) {
            return runBlocking {
                try {
                    val body = json.encodeToString(onListChoreTypes())
                    newFixedLengthResponse(Response.Status.OK, "application/json", body)
                } catch (e: Exception) {
                    android.util.Log.w("WatchRelay", "$CHORE_TYPES_PATH failed", e)
                    newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "failed: ${e.message}")
                }
            }
        }

        val action: (suspend () -> Unit) = when (session.uri) {
            BEER_FILL_PATH -> {
                val volumeMl = session.parameters[VOLUME_PARAM]?.firstOrNull()?.toIntOrNull()
                if (volumeMl == null || volumeMl <= 0) {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "invalid volume")
                }
                { onBeerFill(volumeMl) }
            }
            CHORE_EVENT_PATH -> {
                val typeId = session.parameters[TYPE_ID_PARAM]?.firstOrNull()?.trim()
                if (typeId.isNullOrEmpty()) {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "invalid typeId")
                }
                { onChoreEvent(typeId) }
            }
            AUDIO_NOTE_CHUNK_PATH -> {
                val uploadId = session.headers[UPLOAD_ID_HEADER]?.trim()
                val chunkIndex = session.headers[CHUNK_INDEX_HEADER]?.toIntOrNull()
                val chunkCount = session.headers[CHUNK_COUNT_HEADER]?.toIntOrNull()
                if (uploadId.isNullOrEmpty() || chunkIndex == null || chunkCount == null ||
                    chunkCount !in 1..MAX_CHUNK_COUNT || chunkIndex !in 0 until chunkCount
                ) {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "invalid chunk headers")
                }
                val body = readRequestBody(session, AUDIO_NOTE_CHUNK_MAX_BYTES)
                    ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "invalid body length")
                val payload = decodeAudioPayload(session, body)
                    ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "invalid base64")
                val recordedAtMillis = session.headers[RECORDED_AT_HEADER]?.toLongOrNull()

                val complete = putChunk(uploadId, chunkIndex, chunkCount, payload)
                return if (complete == null) {
                    newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "accepted")
                } else {
                    runBlocking {
                        try {
                            onAudioNote(complete, recordedAtMillis)
                            newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "ok")
                        } catch (e: Exception) {
                            android.util.Log.w("WatchRelay", "$AUDIO_NOTE_CHUNK_PATH failed", e)
                            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "failed: ${e.message}")
                        }
                    }
                }
            }
            // Unreachable: the guard above already narrows this to exactly
            // the three POST paths handled in this when, so this only exists
            // to satisfy the compiler's exhaustiveness check on a String.
            else -> return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")
        }

        // Every endpoint's action is a local, offline-first write (a queued
        // outbox mutation, or the audio-note PoC's disk write) — none blocks
        // on connectivity, so there is no network-reachability gate here.
        return runBlocking {
            try {
                action()
                newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "ok")
            } catch (e: Exception) {
                android.util.Log.w("WatchRelay", "${session.uri} failed", e)
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "failed: ${e.message}")
            }
        }
    }

    // NanoHTTPD 2.3.1 leaves session.inputStream positioned exactly at the
    // body start (mark/reset in decodeHeader), so reading Content-Length raw
    // bytes here is safe for binary — unlike parseBody(), which would
    // UTF-8-decode and trim() the body.
    private fun readRequestBody(session: IHTTPSession, maxBytes: Int): ByteArray? {
        val length = session.headers["content-length"]?.toIntOrNull() ?: -1
        if (length <= 0 || length > maxBytes) return null
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val r = session.inputStream.read(body, read, length - read)
            if (r < 0) break
            read += r
        }
        return if (read == length) body else null
    }

    private fun decodeAudioPayload(session: IHTTPSession, body: ByteArray): ByteArray? =
        if (session.headers[AUDIO_ENCODING_HEADER]?.lowercase() == "base64") {
            try {
                android.util.Base64.decode(body, android.util.Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                null
            }
        } else {
            body
        }

    /**
     * Accumulates one chunk of a chunked audio-note upload. Returns the
     * fully reassembled bytes once every chunk for [uploadId] has arrived,
     * null while chunks are still outstanding. Tolerates out-of-order
     * arrival (indexed slots) and idempotent retries (re-posting the same
     * index just overwrites that slot without double-counting it).
     */
    @Synchronized
    private fun putChunk(uploadId: String, chunkIndex: Int, chunkCount: Int, payload: ByteArray): ByteArray? {
        pruneExpiredChunkUploads()
        val existing = chunkUploads[uploadId]
        val upload = if (existing != null && existing.chunks.size == chunkCount) {
            existing
        } else {
            // First chunk seen for this upload id, or a chunk-count mismatch
            // (e.g. a stale retry after the entry was evicted) — start fresh.
            ChunkUpload(chunkCount).also { chunkUploads[uploadId] = it }
        }
        if (upload.chunks[chunkIndex] == null) {
            upload.receivedCount += 1
        }
        upload.chunks[chunkIndex] = payload
        upload.lastUpdatedAtMillis = System.currentTimeMillis()
        if (upload.receivedCount < chunkCount) {
            return null
        }

        chunkUploads.remove(uploadId)
        val total = upload.chunks.sumOf { it!!.size }
        val result = ByteArray(total)
        var offset = 0
        for (chunk in upload.chunks) {
            chunk!!.copyInto(result, offset)
            offset += chunk.size
        }
        return result
    }

    private fun pruneExpiredChunkUploads() {
        val now = System.currentTimeMillis()
        chunkUploads.entries.removeAll { now - it.value.lastUpdatedAtMillis > CHUNK_UPLOAD_TTL_MILLIS }
    }
}

private class ChunkUpload(chunkCount: Int) {
    val chunks = arrayOfNulls<ByteArray>(chunkCount)
    var receivedCount = 0
    var lastUpdatedAtMillis = System.currentTimeMillis()
}
