package ch.mcfx.urs.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        FillEntity::class,
        FillingStationEntity::class,
        OutboxMutationEntity::class,
        CurrencyEntity::class,
        CarEntity::class,
        WorkTimeEntryEntity::class,
        WorkTimeBreakEntity::class,
        WorkTimeMonthOverrideEntity::class,
        InventoryCategoryEntity::class,
        InventoryProductEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fillDao(): FillDao
    abstract fun fillingStationDao(): FillingStationDao
    abstract fun outboxDao(): OutboxDao
    abstract fun currencyDao(): CurrencyDao
    abstract fun carDao(): CarDao
    abstract fun workTimeDao(): WorkTimeDao
    abstract fun workTimeMonthOverrideDao(): WorkTimeMonthOverrideDao
    abstract fun inventoryCategoryDao(): InventoryCategoryDao
    abstract fun inventoryProductDao(): InventoryProductDao
}
