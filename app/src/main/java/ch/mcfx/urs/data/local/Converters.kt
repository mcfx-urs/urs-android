package ch.mcfx.urs.data.local

import androidx.room.TypeConverter
import ch.mcfx.urs.data.AssetCategory
import ch.mcfx.urs.data.VehicleType
import ch.mcfx.urs.data.assetCategoryFromRaw
import ch.mcfx.urs.data.toRaw
import ch.mcfx.urs.data.vehicleTypeFromRaw
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

    @TypeConverter
    fun fromAssetCategory(value: AssetCategory): String = value.toRaw()

    @TypeConverter
    fun toAssetCategory(value: String): AssetCategory = assetCategoryFromRaw(value)

    // Plain JSON array, no per-item structure needed — AssetEntity.tags is
    // the only List<String> column in this database so far.
    @TypeConverter
    fun fromStringList(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> = Json.decodeFromString(value)
}
