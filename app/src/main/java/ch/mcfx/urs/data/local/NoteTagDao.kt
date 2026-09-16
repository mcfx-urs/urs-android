package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteTagDao {

    @Query("SELECT * FROM note_tag WHERE noteId = :noteId ORDER BY tagName ASC")
    fun observeByNoteId(noteId: Long): Flow<List<NoteTagEntity>>

    @Query("SELECT * FROM note_tag WHERE noteId = :noteId ORDER BY tagName ASC")
    suspend fun getByNoteId(noteId: Long): List<NoteTagEntity>

    // Fuzzy suggestions for the tag input's autocomplete, scoped to this
    // user's own notes only (never another user's — same privacy bar as the
    // notes themselves) and this user's own tag names only, most recently
    // used first among duplicates.
    @Query(
        """
        SELECT DISTINCT note_tag.tagName FROM note_tag
        INNER JOIN note ON note.id = note_tag.noteId
        WHERE note.userId = :userId AND note_tag.tagName LIKE '%' || :query || '%'
        ORDER BY note_tag.tagName ASC
        LIMIT 10
        """,
    )
    suspend fun suggestTagNames(userId: String, query: String): List<String>

    @Insert
    suspend fun insertAll(tags: List<NoteTagEntity>)

    @Query("DELETE FROM note_tag WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: Long)

    /**
     * Atomic delete+insert for a note's tag set — without [Transaction],
     * Room's Flow invalidation tracker can re-run an observer between the
     * delete committing and the insert committing, observed as the tag list
     * briefly going empty on the Notes screen (GitHub issue #70).
     */
    @Transaction
    suspend fun replaceTags(noteId: Long, tags: List<NoteTagEntity>) {
        deleteByNoteId(noteId)
        insertAll(tags)
    }
}
