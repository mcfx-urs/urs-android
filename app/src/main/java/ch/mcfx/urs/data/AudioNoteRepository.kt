package ch.mcfx.urs.data

import android.content.Context
import ch.mcfx.urs.data.local.AudioNoteDao
import ch.mcfx.urs.data.local.AudioNoteEntity
import ch.mcfx.urs.watchrelay.ZeppOpusToOgg
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Device-local store for watch voice notes (GitHub issue #41). The watch
 * relay hands raw recorder bytes to [saveFromWatch]; everything else is a
 * plain list + delete over the local Room table and the files on disk.
 */
class AudioNoteRepository(
    context: Context,
    private val dao: AudioNoteDao,
) {
    private val appContext = context.applicationContext
    private val dir: File get() = File(appContext.filesDir, "audio-notes")

    fun observeAll(): Flow<List<AudioNoteEntity>> = dao.observeAll()

    /**
     * Convert the watch recorder's raw output to a standard Ogg-Opus file,
     * store it, and record the row. Runs on IO. Throws if the bytes are not
     * decodable framing — the caller (relay) then returns a non-2xx so the
     * watch keeps the recording and retries. [recordedAtMillis] is the
     * watch's recording-start time if the upload carried one; falls back to
     * the save time otherwise (e.g. an older watch build without the
     * header).
     */
    suspend fun saveFromWatch(watchBytes: ByteArray, recordedAtMillis: Long? = null): AudioNoteEntity =
        withContext(Dispatchers.IO) {
            val converted = ZeppOpusToOgg.convert(watchBytes)
            require(converted.packetCount > 0) { "no opus frames in watch upload" }

            val timestamp = recordedAtMillis ?: System.currentTimeMillis()
            dir.mkdirs()
            val file = File(dir, "note-$timestamp.opus")
            file.writeBytes(converted.ogg)

            val entity = AudioNoteEntity(
                createdAtMillis = timestamp,
                filePath = file.absolutePath,
                durationMs = converted.durationMs,
                source = "watch",
            )
            entity.copy(id = dao.insert(entity))
        }

    suspend fun delete(note: AudioNoteEntity) = withContext(Dispatchers.IO) {
        dao.delete(note.id)
        runCatching { File(note.filePath).delete() }
        Unit
    }
}
