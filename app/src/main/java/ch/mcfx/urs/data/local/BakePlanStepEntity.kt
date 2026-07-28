package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local, always-available mirror of one step of a [BakePlanEntity] — same
 * offline-first shape as [ListItemEntity]. [planId] holds the parent
 * [BakePlanEntity.publicId] as it stood at creation time (a real backend id,
 * or a not-yet-synced stand-in); unlike a shopping-list item, a step never
 * gets its own CREATE outbox mutation (all of a plan's steps ride along in
 * the single plan-create payload — see `SyncManager.replayCreateBakePlan`),
 * so there's no `outboxId` here, only [syncStatus] to reflect a still-queued
 * done/snooze update. Times are stored as epoch millis, not `LocalDateTime`/
 * String — converted to the backend's date format only at outbox-payload
 * encode time. [alarmId] is assigned once at plan-creation and stays stable
 * across snooze/re-arm/reboot, so `AlarmManager` always identifies the same
 * step's `PendingIntent` (see `BakingStepAlarmScheduler`).
 */
@Entity(tableName = "bake_plan_step")
data class BakePlanStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Null until this row has been confirmed by the backend — the real bake_plan_step_id.
    val serverId: String? = null,
    val planId: String,
    val stepIndex: Int,
    val label: String,
    val plannedAtMillis: Long,
    val snoozedAtMillis: Long? = null,
    val doneAtMillis: Long? = null,
    val alarmId: Int,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
)
