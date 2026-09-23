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
        BakePlanEntity::class,
        BakePlanStepEntity::class,
        NoteEntity::class,
        TagEntity::class,
        TrackerTypeEntity::class,
        TrackerEventEntity::class,
        AudioNoteEntity::class,
        LocationCaptureLogEntity::class,
        KanbanBoardEntity::class,
        KanbanColumnEntity::class,
        KanbanCardEntity::class,
        KanbanChecklistItemEntity::class,
        TrackerDomainEntity::class,
        AssetEntity::class,
        AssetComponentEntity::class,
        AssetCommentEntity::class,
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
    // v17: RecentlyUsedProductEntity re-keyed to (catalogProductId, listId)
    //      — "recently used" is now scoped per list instead of global per
    //      user — same destructive-fallback handling as every bump above.
    // v18: paidBreak column added to WorkTimeEntryEntity — same
    //      destructive-fallback handling as every bump above.
    // v19: new BakePlanEntity/BakePlanStepEntity tables (Baking) —
    //      same destructive-fallback handling as every bump above.
    // v20: userId added to InventoryEntity, InventoryDao.observeAll() now
    //      filters by it — a previous user's synced inventories no longer
    //      show up after switching the logged-in user on one device
    //      — same destructive-fallback handling as every bump above.
    // v21: new NoteEntity/NoteTagEntity tables (GitHub issue #10, Notes) —
    //      same destructive-fallback handling as every bump above.
    // v22: mealAllowance column added to WorkTimeEntryEntity (GitHub issue
    //      #24) — same destructive-fallback handling as every bump above.
    // v23: new TrackerTypeEntity/TrackerEventEntity tables (GitHub issue
    //      #27, Chores and Stuff) — same destructive-fallback handling as
    //      every bump above.
    // v24: calendar + lastExportedAtMillis columns added to
    //      TrackerTypeEntity (GitHub issue #28, ICS export) — same
    //      destructive-fallback handling as every bump above.
    // v25: expectedIntervalDays column added to TrackerTypeEntity (GitHub
    //      issue #29, overdue badge + reminders) — same destructive-fallback
    //      handling as every bump above.
    // v26: new AudioNoteEntity table (GitHub issue #41, watch voice notes) —
    //      same destructive-fallback handling as every bump above.
    // v27: comment column added to WorkTimeEntryEntity (GitHub issue #45) —
    //      same destructive-fallback handling as every bump above.
    // v28: bvgAmount column added to WorkTimeMonthOverrideEntity (GitHub
    //      issue #43) — same destructive-fallback handling as every bump above.
    // v29: isFavorite column added to ListEntity/InventoryEntity (GitHub
    //      issue #50, home-tile quick-jump badges) — same destructive-fallback
    //      handling as every bump above.
    // v30: new LocationCaptureLogEntity table (GitHub issue #60, adaptive
    //      capture interval debug trail) — same destructive-fallback
    //      handling as every bump above.
    // v31: iconId column added to ListEntity/InventoryEntity (GitHub issue
    //      #56, searchable icon picker) — same destructive-fallback handling
    //      as every bump above.
    // v32: new KanbanBoardEntity/KanbanColumnEntity/KanbanCardEntity/
    //      KanbanChecklistItemEntity/KanbanCardTagEntity tables (GitHub
    //      issue #62, Kanban board) — same destructive-fallback handling
    //      as every bump above.
    // v33: isFavorite/iconId columns added to KanbanBoardEntity (GitHub
    //      issue #80, Home tile quick-jump badges) — same destructive-fallback
    //      handling as every bump above.
    // v34: mode/startMillis/endMillis/scheduledForMillis/batteryPercent/
    //      isCharging/isPowerSaveMode/isDeviceIdleMode columns added to
    //      LocationCaptureLogEntity (GitHub issue #86, richer capture
    //      logging for battery analysis) — same destructive-fallback
    //      handling as every bump above.
    // v35: new TrackerDomainEntity table; domainId column added to
    //      TrackerTypeEntity; occurredOnEnd/occurredAtEnd columns added to
    //      TrackerEventEntity (GitHub issue #83, Journal) — same
    //      destructive-fallback handling as every bump above.
    // v36: color column added to NoteTagEntity (GitHub issue #85) — same
    //      destructive-fallback handling as every bump above.
    // v37: NoteTagEntity/KanbanCardTagEntity merged into one TagEntity
    //      (table "tag", nullable noteId/cardId; mcfx-urs/urs-backend#11 —
    //      Notes and Kanban now share one tag pool/color) — same
    //      destructive-fallback handling as every bump above.
    // v38: isContainer column added to VehicleEntity; sourceVehicleId column
    //      added to FillEntity (mcfx-urs/urs-android#87, fuel container
    //      tracking) — same destructive-fallback handling as every bump
    //      above.
    // v39: new AssetEntity/AssetComponentEntity/AssetCommentEntity tables
    //      (mcfx-urs/urs-android#89, personal-possession value tracking) —
    //      same destructive-fallback handling as every bump above.
    version = 39,
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
    abstract fun bakePlanDao(): BakePlanDao
    abstract fun bakePlanStepDao(): BakePlanStepDao
    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao
    abstract fun trackerTypeDao(): TrackerTypeDao
    abstract fun trackerEventDao(): TrackerEventDao
    abstract fun audioNoteDao(): AudioNoteDao
    abstract fun locationCaptureLogDao(): LocationCaptureLogDao
    abstract fun kanbanBoardDao(): KanbanBoardDao
    abstract fun kanbanColumnDao(): KanbanColumnDao
    abstract fun kanbanCardDao(): KanbanCardDao
    abstract fun kanbanChecklistItemDao(): KanbanChecklistItemDao
    abstract fun trackerDomainDao(): TrackerDomainDao
    abstract fun assetDao(): AssetDao
    abstract fun assetComponentDao(): AssetComponentDao
    abstract fun assetCommentDao(): AssetCommentDao
}
