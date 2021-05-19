package info.nightscout.androidaps.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase.Callback
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
open class DatabaseModule {

    @DbFileName
    @Provides
    fun dbFileName() = "androidaps.db"

    @Provides
    @Singleton
    internal fun provideAppDatabase(context: Context, @DbFileName fileName: String) =
        Room
            .databaseBuilder(context, AppDatabase::class.java, fileName)
 //           .addMigrations(migration5to6)
 //           .addMigrations(migration6to7)
 //           .addMigrations(migration7to8)
 //           .addMigrations(migration11to12)
            .addCallback(object : Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_temporaryBasals_end` ON `temporaryBasals` (`timestamp` + `duration`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_extendedBoluses_end` ON `extendedBoluses` (`timestamp` + `duration`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_temporaryTargets_end` ON `temporaryTargets` (`timestamp` + `duration`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_carbs_end` ON `carbs` (`timestamp` + `duration`)")
                }
            })
            .fallbackToDestructiveMigration()
            .build()

    @Qualifier
    annotation class DbFileName

    private val migration5to6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE IF EXISTS userEntry")
            database.execSQL("CREATE TABLE userEntry (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `action` TEXT NOT NULL, `s` TEXT NOT NULL, `values` TEXT NOT NULL)")
        }
    }

    private val migration6to7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE IF EXISTS foods")
            database.execSQL("CREATE TABLE IF NOT EXISTS foods (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `name` TEXT NOT NULL, `category` TEXT, `subCategory` TEXT, `portion` REAL NOT NULL, `carbs` INTEGER NOT NULL, `fat` INTEGER, `protein` INTEGER, `energy` INTEGER, `unit` TEXT NOT NULL, `gi` INTEGER, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER, FOREIGN KEY(`referenceId`) REFERENCES `foods`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_foods_referenceId` ON `foods` (`referenceId`)")
        }
    }

    private val migration7to8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE IF EXISTS bolusCalculatorResults")
            database.execSQL("CREATE TABLE IF NOT EXISTS bolusCalculatorResults (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `targetBGLow` REAL NOT NULL, `targetBGHigh` REAL NOT NULL, `isf` REAL NOT NULL, `ic` REAL NOT NULL, `bolusIOB` REAL NOT NULL, `wasBolusIOBUsed` INTEGER NOT NULL, `basalIOB` REAL NOT NULL, `wasBasalIOBUsed` INTEGER NOT NULL, `glucoseValue` REAL NOT NULL, `wasGlucoseUsed` INTEGER NOT NULL, `glucoseDifference` REAL NOT NULL, `glucoseInsulin` REAL NOT NULL, `glucoseTrend` REAL NOT NULL, `wasTrendUsed` INTEGER NOT NULL, `trendInsulin` REAL NOT NULL, `cob` REAL NOT NULL, `wasCOBUsed` INTEGER NOT NULL, `cobInsulin` REAL NOT NULL, `carbs` REAL NOT NULL, `wereCarbsUsed` INTEGER NOT NULL, `carbsInsulin` REAL NOT NULL, `otherCorrection` REAL NOT NULL, `wasSuperbolusUsed` INTEGER NOT NULL, `superbolusInsulin` REAL NOT NULL, `wasTempTargetUsed` INTEGER NOT NULL, `totalInsulin` REAL NOT NULL, `percentageCorrection` INTEGER NOT NULL, `profileName` TEXT NOT NULL, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER, FOREIGN KEY(`referenceId`) REFERENCES `bolusCalculatorResults`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_bolusCalculatorResults_referenceId` ON bolusCalculatorResults (`referenceId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_bolusCalculatorResults_timestamp` ON bolusCalculatorResults (`timestamp`)")

            database.execSQL("DROP TABLE IF EXISTS mealLinks")
            database.execSQL("CREATE TABLE IF NOT EXISTS mealLinks (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `bolusId` INTEGER, `carbsId` INTEGER, `bolusCalcResultId` INTEGER, `superbolusTempBasalId` INTEGER, `noteId` INTEGER, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER, FOREIGN KEY(`bolusId`) REFERENCES `boluses`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`carbsId`) REFERENCES `carbs`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`bolusCalcResultId`) REFERENCES `bolusCalculatorResults`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`superbolusTempBasalId`) REFERENCES `temporaryBasals`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`noteId`) REFERENCES `therapyEvents`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`referenceId`) REFERENCES `mealLinks`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mealLinks_referenceId` ON mealLinks (`referenceId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mealLinks_bolusId` ON `mealLinks (`bolusId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mealLinks_carbsId` ON mealLinks (`carbsId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mealLinks_bolusCalcResultId` ON mealLinks (`bolusCalcResultId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mealLinks_superbolusTempBasalId` ON mealLinks (`superbolusTempBasalId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mealLinks_noteId` ON mealLinks (`noteId`)")
        }
    }

    private val migration11to12 = object : Migration(11,12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE IF EXISTS userEntry")
            database.execSQL("CREATE TABLE IF NOT EXISTS userEntry (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `action` TEXT NOT NULL, `source` TEXT NOT NULL, `note` TEXT NOT NULL, `values` TEXT NOT NULL)")
        }
    }
}