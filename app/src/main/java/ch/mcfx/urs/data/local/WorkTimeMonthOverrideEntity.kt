package ch.mcfx.urs.data.local

import androidx.room.Entity

/**
 * Local cache of a month's manual adjustments — the days-worked override and
 * the BVG deduction amount, each an empty string when unset. Direct REST
 * reads/writes, no outbox: low-frequency settings-adjacent values, not
 * offline-first write data like [WorkTimeEntryEntity]. Cached locally purely
 * so a past month's values are still visible while offline. The BVG amount
 * is carried forward: a month with no value of its own inherits the most
 * recent earlier month that has one (see [ch.mcfx.urs.data.computeMonthlySummary]).
 */
@Entity(tableName = "work_time_month_override", primaryKeys = ["year", "month"])
data class WorkTimeMonthOverrideEntity(
    val year: Int,
    val month: Int,
    val daysWorked: String,
    val bvgAmount: String = "",
)
