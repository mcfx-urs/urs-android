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
        InventoryEntity::class,
        InventoryProductEntity::class,
        CatalogCategoryEntity::class,
        CatalogProductEntity::class,
        RecentlyUsedProductEntity::class,
        ListEntity::class,
        ListItemEntity::class,
        LocationHistoryEntity::class,
    ],
    // Bumped for isFullTank column on FillEntity, the life map
    // feature's LocationHistoryEntity (Phase 1, local-only), and Phase 3's
    // serverId/outboxId/syncStatus sync fields added to that same entity —
    // still no Migration objects needed at this pre-release stage, see
    // AppContainer.database's fallbackToDestructiveMigration doc comment.
    version = 11,
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
    abstract fun inventoryDao(): InventoryDao
    abstract fun inventoryProductDao(): InventoryProductDao
    abstract fun catalogCategoryDao(): CatalogCategoryDao
    abstract fun catalogProductDao(): CatalogProductDao
    abstract fun recentlyUsedProductDao(): RecentlyUsedProductDao
    abstract fun listDao(): ListDao
    abstract fun listItemDao(): ListItemDao
    abstract fun locationHistoryDao(): LocationHistoryDao
}
