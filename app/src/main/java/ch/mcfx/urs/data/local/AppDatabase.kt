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
        VehicleEntity::class,
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
        VehicleServiceEntity::class,
        VehicleServiceTagEntity::class,
    ],
    // Bumped for the isFullTank column on FillEntity, the life map
    // feature's LocationHistoryEntity (local capture + sync fields), and
    // the quantity/onSale columns on ListItemEntity — still no Migration
    // objects needed at this pre-release stage, see
    // AppContainer.database's fallbackToDestructiveMigration doc comment.
    // v13: source added to CatalogProductEntity/CatalogCategoryEntity,
    // catalogImageId added to CatalogCategoryEntity.
    // v14: source added to FillingStationEntity (proximity-sort picker) —
    // this bump was missed when that column was added, which left an
    // installed v13 build's on-disk schema (no `source` column) with a
    // different identity hash than a rebuilt-but-still-v13 APK's schema,
    // crashing on startup with Room's "cannot verify data integrity"
    // instead of going through fallbackToDestructiveMigration at all
    // (that only triggers on an actual version transition).
    // v15: CarEntity/CarDao renamed to VehicleEntity/VehicleDao,
    //      table "car" renamed to "vehicle" — schema identity hash changes,
    //      same destructive-fallback handling as every bump above.
    // v16: new vehicle detail columns (engine code, vehicle type,
    //      Fahrzeugausweis fields, MFK date) on VehicleEntity, plus the new
    //      VehicleServiceEntity/VehicleServiceTagEntity tables — same
    //      destructive-fallback handling as every bump above.
    version = 16,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fillDao(): FillDao
    abstract fun fillingStationDao(): FillingStationDao
    abstract fun outboxDao(): OutboxDao
    abstract fun currencyDao(): CurrencyDao
    abstract fun vehicleDao(): VehicleDao
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
    abstract fun vehicleServiceDao(): VehicleServiceDao
}
