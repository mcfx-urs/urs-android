package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioNoteDao {

    @Query("SELECT * FROM audio_note ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<AudioNoteEntity>>

    @Query("SELECT * FROM audio_note WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AudioNoteEntity?

    @Insert
    suspend fun insert(note: AudioNoteEntity): Long

    @Query("DELETE FROM audio_note WHERE id = :id")
    suspend fun delete(id: Long)
}
