package ch.mcfx.urs.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Local, always-available mirror of one vehicle service/maintenance
 * entry — mirrors [ch.mcfx.urs.data.local.WorkTimeEntryEntity]'s
 * offline-first shape (nullable [serverId]/[outboxId] until confirmed,
 * [syncStatus]).
 */
@Entity(tableName = "vehicle_service")
data class VehicleServiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long? = null,
    val outboxId: Long? = null,
    val vehicleId: String,
    val date: String,
    val odometer: String,
    val provider: String = "",
    val isDiy: Boolean = false,
    val notes: String = "",
    val costAmount: String,
    val currencyCode: String,
    val syncStatus: SyncStatus,
)

/**
 * One category tag for a [VehicleServiceEntity], keyed by the local (not
 * server) service id — see [VehicleServiceDao.replaceTags]. [code] is one
 * of [ch.mcfx.urs.service.ServiceCategory]'s fixed codes, or `"custom"`
 * when [label] holds a free-typed tag.
 */
@Entity(tableName = "vehicle_service_tag")
data class VehicleServiceTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serviceId: Long,
    val code: String,
    val label: String? = null,
)

data class VehicleServiceWithTags(
    @Embedded val service: VehicleServiceEntity,
    @Relation(parentColumn = "id", entityColumn = "serviceId")
    val tags: List<VehicleServiceTagEntity>,
)
