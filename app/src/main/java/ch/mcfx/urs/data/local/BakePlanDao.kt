package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BakePlanDao {

    @Query("SELECT * FROM bake_plan ORDER BY anchorAtMillis DESC")
    fun observeAll(): Flow<List<BakePlanEntity>>

    @Query("SELECT * FROM bake_plan WHERE status = :status ORDER BY anchorAtMillis DESC")
    fun observeByStatus(status: String): Flow<List<BakePlanEntity>>

    @Query("SELECT * FROM bake_plan WHERE status IN (:statuses) ORDER BY anchorAtMillis DESC")
    fun observeByStatuses(statuses: List<String>): Flow<List<BakePlanEntity>>

    /** One-shot lookup for boot-time alarm re-arming — see `BakingRepository.rearmPendingStepAlarms`. */
    @Query("SELECT * FROM bake_plan WHERE status = 'active'")
    suspend fun getActive(): List<BakePlanEntity>

    @Query("SELECT * FROM bake_plan WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BakePlanEntity?

    @Query("SELECT * FROM bake_plan WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): BakePlanEntity?

    @Query("SELECT id FROM bake_plan WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: String): Long?

    /** Local-only write — see [InventoryDao.upsert]'s doc comment. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plan: BakePlanEntity): Long

    @Query("UPDATE bake_plan SET syncStatus = 'SYNCED', serverId = :serverId, outboxId = NULL WHERE id = :id")
    suspend fun markSynced(id: Long, serverId: String)

    @Query("UPDATE bake_plan SET status = :status, completedAtMillis = :completedAtMillis WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, completedAtMillis: Long?)
}
