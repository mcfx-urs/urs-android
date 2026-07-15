package ch.mcfx.urs.data.local

import androidx.room.Entity

/**
 * Local cache of a manually-set monthly target-hours override — direct
 * REST reads/writes, no outbox: this is a low-frequency settings-adjacent
 * value, not offline-first write data like [WorkTimeEntryEntity]. Cached
 * locally purely so a past month's override is still visible while offline.
 */
@Entity(tableName = "work_time_month_override", primaryKeys = ["year", "month"])
data class WorkTimeMonthOverrideEntity(
    val year: Int,
    val month: Int,
    val targetHours: String,
)
