package ch.mcfx.urs.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    @TypeConverter
    fun fromOutboxStatus(value: OutboxStatus): String = value.name

    @TypeConverter
    fun toOutboxStatus(value: String): OutboxStatus = OutboxStatus.valueOf(value)
}
