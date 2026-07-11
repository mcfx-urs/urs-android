package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Read-cache mirror of [ch.mcfx.urs.data.remote.FillingStationDto], keyed by
 * the backend's own station ID (not a local autoincrement id — there's
 * nothing to reconcile on refresh, unlike [FillEntity]). Ad-hoc (GPS-only)
 * stations only ever appear here *after* their owning fill has synced and
 * received a real ID — matches the backend's own picker-exclusion rule for
 * `gps_auto` stations structurally, rather than needing a local flag for it.
 */
@Entity(tableName = "filling_station")
data class FillingStationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val counter: String,
    val address: String,
    val latitude: String,
    val longitude: String,
)
