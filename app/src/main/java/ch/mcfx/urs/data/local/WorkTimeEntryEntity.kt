package ch.mcfx.urs.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Local, always-available mirror of one day's work-time entry — mirrors
 * [FillEntity]'s offline-first shape (nullable [serverId]/[outboxId] until
 * confirmed, [syncStatus]).
 */
@Entity(tableName = "work_time_entry")
data class WorkTimeEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long? = null,
    val outboxId: Long? = null,
    val date: String,
    val workStart: String,
    val workEnd: String,
    // Empty string = no per-day override, falls back to the user's default —
    // mirrors the backend's nullable-column-as-empty-string convention.
    val targetDailyHours: String = "",
    // The almost-always-paid 15-minute morning break — added on top of the
    // work span in WorkTimeCalculations.dailyHoursWorked() rather than
    // logged as a WorkTimeBreakEntity, since breaks subtract from the total
    // and this must add to it. Defaults on since it applies most days;
    // per-entry so a day without it can opt out.
    val paidBreak: Boolean = true,
    val syncStatus: SyncStatus,
)

/**
 * One break interval within a [WorkTimeEntryEntity] day, keyed by the local
 * (not server) entry id — see [WorkTimeDao.replaceBreaks].
 */
@Entity(tableName = "work_time_break")
data class WorkTimeBreakEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val startTime: String,
    val endTime: String,
)

data class WorkTimeEntryWithBreaks(
    @Embedded val entry: WorkTimeEntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val breaks: List<WorkTimeBreakEntity>,
)
