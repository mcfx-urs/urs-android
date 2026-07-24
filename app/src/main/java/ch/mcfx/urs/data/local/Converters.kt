package ch.mcfx.urs.data.local

import androidx.room.TypeConverter
import ch.mcfx.urs.data.VehicleType
import ch.mcfx.urs.data.toRaw
import ch.mcfx.urs.data.vehicleTypeFromRaw

class Converters {
    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    @TypeConverter
    fun fromOutboxStatus(value: OutboxStatus): String = value.name

    @TypeConverter
    fun toOutboxStatus(value: String): OutboxStatus = OutboxStatus.valueOf(value)

    @TypeConverter
    fun fromVehicleType(value: VehicleType): String = value.toRaw()

    @TypeConverter
    fun toVehicleType(value: String): VehicleType = vehicleTypeFromRaw(value)
}
