package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A voice memo recorded on the watch and relayed to the phone (GitHub
 * issue #41). Device-local only — no server sync, no outbox. [filePath] is
 * an absolute path to a standard Ogg-Opus file under
 * `filesDir/audio-notes/`, converted from the watch recorder's raw output
 * on receipt (see [ch.mcfx.urs.watchrelay.ZeppOpusToOgg]).
 */
@Entity(tableName = "audio_note")
data class AudioNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtMillis: Long,
    val filePath: String,
    val durationMs: Long,
    val source: String = "watch",
)
