package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Read-cache mirror of [ch.mcfx.urs.data.remote.FillingStationDto], keyed by
 * the backend's own station ID (not a local autoincrement id — there's
 * nothing to reconcile on refresh, unlike [FillEntity]). Ad-hoc (GPS-only)
 * stations only ever appear here *after* their owning fill has synced and
 * received a real ID, tagged [SOURCE_GPS_AUTO] by [ch.mcfx.urs.data.sync.SyncManager]
 * at that point — the local mirror of the backend's own picker-exclusion
 * rule for `gps_auto` stations, since a synced-down row (always
 * [SOURCE_MANUAL], the backend's own listing endpoint already excludes
 * `gps_auto`) can't be told apart from one cached this way without it.
 */
@Entity(tableName = "filling_station")
data class FillingStationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val counter: String,
    val address: String,
    val latitude: String,
    val longitude: String,
    val source: String = SOURCE_MANUAL,
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_GPS_AUTO = "gps_auto"
    }
}
