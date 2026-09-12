package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KanbanCardTagDao {

    @Query("SELECT * FROM kanban_card_tag")
    fun observeAll(): Flow<List<KanbanCardTagEntity>>

    @Query("SELECT * FROM kanban_card_tag WHERE cardId = :cardId ORDER BY tagName ASC")
    suspend fun getByCardId(cardId: Long): List<KanbanCardTagEntity>

    // Fuzzy suggestions for the tag input's autocomplete — same shape as
    // [NoteTagDao.suggestTagNames], scoped to this app's single-user-per-
    // device model (kanban cards, unlike notes, carry no userId column of
    // their own to scope by — see KanbanCardEntity's doc comment).
    @Query("SELECT DISTINCT tagName FROM kanban_card_tag WHERE tagName LIKE '%' || :query || '%' ORDER BY tagName ASC LIMIT 10")
    suspend fun suggestTagNames(query: String): List<String>

    @Insert
    suspend fun insertAll(tags: List<KanbanCardTagEntity>)

    @Query("DELETE FROM kanban_card_tag WHERE cardId = :cardId")
    suspend fun deleteByCardId(cardId: Long)
}
