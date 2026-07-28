package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BakePlanStepDao {

    @Query("SELECT * FROM bake_plan_step WHERE planId = :planId ORDER BY stepIndex ASC")
    fun observeByPlanId(planId: String): Flow<List<BakePlanStepEntity>>

    /** One-shot lookup — used both by `SyncManager.replayCreateBakePlan` (match by index) and boot rearm. */
    @Query("SELECT * FROM bake_plan_step WHERE planId = :planId ORDER BY stepIndex ASC")
    suspend fun getByPlanId(planId: String): List<BakePlanStepEntity>

    @Query("SELECT * FROM bake_plan_step WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BakePlanStepEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(step: BakePlanStepEntity): Long

    // Set once, right after insert — the real alarmId is derived from this
    // row's own Room-assigned id, so it can't be known until the row exists
    // (see BakingRepository.createPlan).
    @Query("UPDATE bake_plan_step SET alarmId = :alarmId WHERE id = :id")
    suspend fun updateAlarmId(id: Long, alarmId: Int)

    // Re-parents a plan's already-inserted steps from its pre-sync stand-in
    // planId to the real backend id, and stamps each step's own serverId —
    // called once per step, matched to the create response by stepIndex
    // order (see SyncManager.replayCreateBakePlan).
    @Query("UPDATE bake_plan_step SET serverId = :serverId, planId = :planId WHERE id = :id")
    suspend fun markSyncedWithServerId(id: Long, serverId: String, planId: String)

    @Query("UPDATE bake_plan_step SET doneAtMillis = :doneAtMillis, syncStatus = :syncStatus WHERE id = :id")
    suspend fun updateDone(id: Long, doneAtMillis: Long?, syncStatus: SyncStatus)

    @Query("UPDATE bake_plan_step SET snoozedAtMillis = :snoozedAtMillis, syncStatus = :syncStatus WHERE id = :id")
    suspend fun updateSnoozed(id: Long, snoozedAtMillis: Long?, syncStatus: SyncStatus)

    @Query("UPDATE bake_plan_step SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun markSynced(id: Long)
}
