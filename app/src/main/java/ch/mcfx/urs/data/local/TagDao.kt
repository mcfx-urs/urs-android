package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Replaces the former separate `NoteTagDao`/`KanbanCardTagDao` — one shared local tag pool (mcfx-urs/urs-backend#11). */
@Dao
interface TagDao {

    @Query("SELECT * FROM tag WHERE noteId = :noteId ORDER BY tagName ASC")
    fun observeByNoteId(noteId: Long): Flow<List<TagEntity>>

    @Query("SELECT * FROM tag WHERE noteId = :noteId ORDER BY tagName ASC")
    suspend fun getByNoteId(noteId: Long): List<TagEntity>

    @Query("SELECT * FROM tag WHERE cardId IS NOT NULL")
    fun observeAllCardTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tag WHERE cardId = :cardId ORDER BY tagName ASC")
    suspend fun getByCardId(cardId: Long): List<TagEntity>

    @Insert
    suspend fun insertAll(tags: List<TagEntity>)

    @Query("DELETE FROM tag WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: Long)

    @Query("DELETE FROM tag WHERE cardId = :cardId")
    suspend fun deleteByCardId(cardId: Long)

    /**
     * Atomic delete+insert for a note's tag set — without [Transaction],
     * Room's Flow invalidation tracker can re-run an observer between the
     * delete committing and the insert committing, observed as the tag list
     * briefly going empty on the Notes screen (GitHub issue #70).
     */
    @Transaction
    suspend fun replaceNoteTags(noteId: Long, tags: List<TagEntity>) {
        deleteByNoteId(noteId)
        insertAll(tags)
    }

    @Transaction
    suspend fun replaceCardTags(cardId: Long, tags: List<TagEntity>) {
        deleteByCardId(cardId)
        insertAll(tags)
    }
}
