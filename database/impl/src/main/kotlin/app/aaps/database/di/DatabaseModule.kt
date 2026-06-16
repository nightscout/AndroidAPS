package app.aaps.database.di

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Room
import androidx.room.RoomDatabase.Callback
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.aaps.database.AppDatabase
import app.aaps.database.entities.TABLE_APS_RESULTS
import app.aaps.database.entities.TABLE_BOLUSES
import app.aaps.database.entities.TABLE_CALIBRATION_ENTRIES
import app.aaps.database.entities.TABLE_EFFECTIVE_PROFILE_SWITCHES
import app.aaps.database.entities.TABLE_HEART_RATE
import app.aaps.database.entities.TABLE_PREFERENCE_CHANGES
import app.aaps.database.entities.TABLE_PROFILE_SWITCHES
import app.aaps.database.entities.TABLE_RUNNING_MODE
import app.aaps.database.entities.TABLE_STEPS_COUNT
import app.aaps.database.entities.TABLE_TEMPORARY_BASALS
import app.aaps.database.entities.TABLE_THERAPY_EVENTS
import app.aaps.database.entities.TABLE_TOTAL_DAILY_DOSES
import app.aaps.database.entities.TABLE_USER_ENTRY
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
open class DatabaseModule {

    @DbFileName
    @Provides
    fun dbFileName() = "androidaps.db"

    @Provides
    @Singleton
    internal fun provideAppDatabase(context: Context, @DbFileName fileName: String) =
        Room
            .databaseBuilder(context, AppDatabase::class.java, fileName)
            // Bundled SQLite driver: ships its own SQLite compiled from source instead of the
            // device's framework SQLite. This is Google's recommended driver (consistent engine
            // across all devices) and, crucially, it does not allocate the framework CursorWindow
            // mem buffer, eliminating CursorWindowAllocationException on memory-constrained devices.
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*migrations)
            .addCallback(object : Callback() {
                // Driver mode delivers an SQLiteConnection (not a SupportSQLiteConnection), so the
                // SupportSQLiteDatabase overload of onOpen never fires here — the connection overload must.
                override fun onOpen(connection: SQLiteConnection) {
                    super.onOpen(connection)
                    createCustomIndexes(connection)
                }
            })
            .fallbackToDestructiveMigration(false)
            .build()

    @Qualifier
    annotation class DbFileName

    private fun createCustomIndexes(connection: SQLiteConnection) {
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_temporaryBasals_end` ON `temporaryBasals` (`timestamp` + `duration`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_extendedBoluses_end` ON `extendedBoluses` (`timestamp` + `duration`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_temporaryTargets_end` ON `temporaryTargets` (`timestamp` + `duration`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_carbs_end` ON `carbs` (`timestamp` + `duration`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_runningModes_end` ON `runningModes` (`timestamp` + `duration`)")
    }

    private fun dropCustomIndexes(connection: SQLiteConnection) {
        connection.execSQL("DROP INDEX IF EXISTS `index_temporaryBasals_end`")
        connection.execSQL("DROP INDEX IF EXISTS `index_extendedBoluses_end`")
        connection.execSQL("DROP INDEX IF EXISTS `index_temporaryTargets_end`")
        connection.execSQL("DROP INDEX IF EXISTS `index_carbs_end`")
        connection.execSQL("DROP INDEX IF EXISTS `index_runningModes_end`")
    }

    internal val migration22to23 = object : Migration(22, 23) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `deviceStatus` ADD COLUMN `isCharging` INTEGER")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(connection)
        }
    }

    internal val migration23to24 = object : Migration(23, 24) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """CREATE TABLE IF NOT EXISTS `$TABLE_HEART_RATE` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `duration` INTEGER NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `beatsPerMinute` REAL NOT NULL,
                    `device` TEXT NOT NULL,
                    `utcOffset` INTEGER NOT NULL,
                    `version` INTEGER NOT NULL,
                    `dateCreated` INTEGER NOT NULL,
                    `isValid` INTEGER NOT NULL,
                    `referenceId` INTEGER,
                    `nightscoutSystemId` TEXT,
                    `nightscoutId` TEXT,
                    `pumpType` TEXT,
                    `pumpSerial` TEXT,
                    `temporaryId` INTEGER,
                    `pumpId` INTEGER, `startId` INTEGER,
                    `endId` INTEGER)""".trimIndent()
            )
            connection.execSQL("""CREATE INDEX IF NOT EXISTS `index_heartRate_id` ON `$TABLE_HEART_RATE` (`id`)""")
            connection.execSQL("""CREATE INDEX IF NOT EXISTS `index_heartRate_timestamp` ON `$TABLE_HEART_RATE` (`timestamp`)""")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(connection)
        }
    }
    internal val migration24to25 = object : Migration(24, 25) {
        override fun migrate(connection: SQLiteConnection) {
            // Creation of table TABLE_STEPS_COUNT
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `${TABLE_STEPS_COUNT}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `duration` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `steps5min` INTEGER NOT NULL, `steps10min` INTEGER NOT NULL, `steps15min` INTEGER NOT NULL, `steps30min` INTEGER NOT NULL, `steps60min` INTEGER NOT NULL, `steps180min` INTEGER NOT NULL, `device` TEXT NOT NULL, `utcOffset` INTEGER NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER)"
            )
            // Creation of index for table TABLE_STEPS_COUNT
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_stepsCount_id` ON `${TABLE_STEPS_COUNT}` (`id`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_stepsCount_timestamp` ON `${TABLE_STEPS_COUNT}` (`timestamp`)")

            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(connection)
        }
    }

    internal val migration25to26 = object : Migration(25, 26) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("DROP TABLE IF EXISTS $TABLE_APS_RESULTS")
            connection.execSQL("CREATE TABLE IF NOT EXISTS `${TABLE_APS_RESULTS}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `algorithm` TEXT NOT NULL, `glucoseStatusJson` TEXT NOT NULL, `currentTempJson` TEXT NOT NULL, `iobDataJson` TEXT NOT NULL, `profileJson` TEXT NOT NULL, `autosensDataJson` TEXT, `mealDataJson` TEXT NOT NULL, `resultJson` TEXT NOT NULL, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER, FOREIGN KEY(`referenceId`) REFERENCES `apsResults`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_apsResults_referenceId` ON `${TABLE_APS_RESULTS}` (`referenceId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_apsResults_timestamp` ON `${TABLE_APS_RESULTS}` (`timestamp`)")
            connection.execSQL("DROP TABLE IF EXISTS apsResultLinks")
            connection.execSQL("DROP TABLE IF EXISTS multiwaveBolusLinks")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(connection)
        }
    }

    internal val migration26to27 = object : Migration(26, 27) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("DROP TABLE IF EXISTS $TABLE_APS_RESULTS")
            connection.execSQL("CREATE TABLE IF NOT EXISTS `${TABLE_APS_RESULTS}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `algorithm` TEXT NOT NULL, `glucoseStatusJson` TEXT, `currentTempJson` TEXT, `iobDataJson` TEXT, `profileJson` TEXT, `autosensDataJson` TEXT, `mealDataJson` TEXT, `resultJson` TEXT NOT NULL, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER, FOREIGN KEY(`referenceId`) REFERENCES `apsResults`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_apsResults_referenceId` ON `${TABLE_APS_RESULTS}` (`referenceId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_apsResults_timestamp` ON `${TABLE_APS_RESULTS}` (`timestamp`)")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(connection)
        }
    }

    internal val migration27to28 = object : Migration(27, 28) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("DELETE FROM $TABLE_APS_RESULTS")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(connection)
        }
    }

    internal val migration28to29 = object : Migration(28, 29) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("DROP TABLE IF EXISTS $TABLE_PREFERENCE_CHANGES")
            connection.execSQL("CREATE TABLE IF NOT EXISTS `$TABLE_PREFERENCE_CHANGES` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `key` TEXT NOT NULL, `value` TEXT NOT NULL)")
            connection.execSQL("DROP TABLE IF EXISTS $TABLE_USER_ENTRY")
            connection.execSQL("CREATE TABLE IF NOT EXISTS `$TABLE_USER_ENTRY` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `action` TEXT NOT NULL, `source` TEXT NOT NULL, `note` TEXT NOT NULL, `values` TEXT NOT NULL)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_userEntry_source` ON `$TABLE_USER_ENTRY` (`source`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_userEntry_timestamp` ON `$TABLE_USER_ENTRY` (`timestamp`)")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(connection)
        }
    }

    internal val migration29to30 = object : Migration(29, 30) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("DROP TABLE IF EXISTS `offlineEvents`")

            connection.execSQL("CREATE TABLE IF NOT EXISTS `$TABLE_RUNNING_MODE` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `mode` TEXT NOT NULL, `duration` INTEGER NOT NULL, `autoForced` INTEGER NOT NULL, `reasons` TEXT, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER, FOREIGN KEY(`referenceId`) REFERENCES `runningModes`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_runningModes_id` ON `$TABLE_RUNNING_MODE` (`id`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_runningModes_nightscoutId` ON `$TABLE_RUNNING_MODE` (`nightscoutId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_runningModes_referenceId` ON `$TABLE_RUNNING_MODE` (`referenceId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_runningModes_timestamp` ON `$TABLE_RUNNING_MODE` (`timestamp`)")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(connection)
        }
    }

    internal val migration30to31 = object : Migration(30, 31) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `$TABLE_THERAPY_EVENTS` ADD COLUMN `location` TEXT")
            connection.execSQL("ALTER TABLE `$TABLE_THERAPY_EVENTS` ADD COLUMN `arrow` TEXT")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(connection)
        }
    }

    internal val migration31to32 = object : Migration(31, 32) {
        override fun migrate(connection: SQLiteConnection) {
            // Add carbInsulin column to TDD table — cached data, old rows get default 0 and will be recalculated
            connection.execSQL("DELETE FROM $TABLE_TOTAL_DAILY_DOSES")
            connection.execSQL("ALTER TABLE `$TABLE_TOTAL_DAILY_DOSES` ADD COLUMN `carbInsulin` REAL NOT NULL DEFAULT 0")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(connection)
        }
    }

    internal val migration32to33 = object : Migration(32, 33) {
        override fun migrate(connection: SQLiteConnection) {
            // Migration of boluses table (insulinPeakTime must be migrated in MainApp)
            connection.execSQL("CREATE TABLE IF NOT EXISTS new_boluses (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `amount` REAL NOT NULL, `type` TEXT NOT NULL, `notes` TEXT, `isBasalInsulin` INTEGER NOT NULL, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER, `insulinLabel` TEXT NOT NULL, `insulinEndTime` INTEGER NOT NULL, `insulinPeakTime` INTEGER NOT NULL, `concentration` REAL NOT NULL, FOREIGN KEY(`referenceId`) REFERENCES `boluses`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            connection.execSQL(
                """
                INSERT INTO new_boluses (id, version, dateCreated, isValid, referenceId, timestamp, utcOffset, amount, type, notes, isBasalInsulin, nightscoutSystemId, nightscoutId, pumpType, pumpSerial, temporaryId, pumpId, startId, endId, insulinLabel, insulinEndTime, insulinPeakTime, concentration)
                SELECT id, version, dateCreated, isValid, referenceId, timestamp, utcOffset, amount, type, notes, isBasalInsulin, nightscoutSystemId, nightscoutId, pumpType, pumpSerial, temporaryId, pumpId, startId, endId, '', -1, -1, 1.0 
                FROM `$TABLE_BOLUSES`
                """.trimIndent()
            )
            connection.execSQL("DROP TABLE `$TABLE_BOLUSES`")
            connection.execSQL("ALTER TABLE new_boluses RENAME TO `$TABLE_BOLUSES`")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_boluses_id` ON `$TABLE_BOLUSES` (`id`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_boluses_pumpId` ON `$TABLE_BOLUSES` (`pumpId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_boluses_referenceId` ON `$TABLE_BOLUSES` (`referenceId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_boluses_timestamp` ON `$TABLE_BOLUSES` (`timestamp`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_boluses_nightscoutId` ON `$TABLE_BOLUSES` (`nightscoutId`)")

            // Migration of effectiveProfileSwitches table (insulinPeakTime must be migrated in MainApp)
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS new_effectiveProfileSwitches (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `basalBlocks` TEXT NOT NULL, `isfBlocks` TEXT NOT NULL, `icBlocks` TEXT NOT NULL, `targetBlocks` TEXT NOT NULL, `glucoseUnit` TEXT NOT NULL, `originalProfileName` TEXT NOT NULL, `originalCustomizedName` TEXT NOT NULL, `originalTimeshift` INTEGER NOT NULL, `originalPercentage` INTEGER NOT NULL, `originalDuration` INTEGER NOT NULL, `originalEnd` INTEGER NOT NULL, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER, `insulinLabel` TEXT NOT NULL, `insulinEndTime` INTEGER NOT NULL, `insulinPeakTime` INTEGER NOT NULL, `concentration` REAL NOT NULL, FOREIGN KEY(`referenceId`) REFERENCES `effectiveProfileSwitches`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
            )
            connection.execSQL(
                """
                INSERT INTO new_effectiveProfileSwitches (id, version, dateCreated, isValid, referenceId, timestamp, utcOffset, basalBlocks, isfBlocks, icBlocks, targetBlocks, glucoseUnit, originalProfileName, originalCustomizedName, originalTimeshift, originalPercentage, originalDuration, originalEnd, nightscoutSystemId, nightscoutId, pumpType, pumpSerial, temporaryId, pumpId, startId, endId, insulinLabel, insulinEndTime, insulinPeakTime, concentration)
                SELECT id, version, dateCreated, isValid, referenceId, timestamp, utcOffset, basalBlocks, isfBlocks, icBlocks, targetBlocks, glucoseUnit, originalProfileName, originalCustomizedName, originalTimeshift, originalPercentage, originalDuration, originalEnd, nightscoutSystemId, nightscoutId, pumpType, pumpSerial, temporaryId, pumpId, startId, endId, '', -1, -1, 1.0 
                FROM `$TABLE_EFFECTIVE_PROFILE_SWITCHES`
                """.trimIndent()
            )
            connection.execSQL("DROP TABLE `$TABLE_EFFECTIVE_PROFILE_SWITCHES`")
            connection.execSQL("ALTER TABLE new_effectiveProfileSwitches RENAME TO `$TABLE_EFFECTIVE_PROFILE_SWITCHES`")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_effectiveProfileSwitches_id` ON `$TABLE_EFFECTIVE_PROFILE_SWITCHES` (`id`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_effectiveProfileSwitches_referenceId` ON `$TABLE_EFFECTIVE_PROFILE_SWITCHES` (`referenceId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_effectiveProfileSwitches_timestamp` ON `$TABLE_EFFECTIVE_PROFILE_SWITCHES` (`timestamp`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_effectiveProfileSwitches_nightscoutId` ON `$TABLE_EFFECTIVE_PROFILE_SWITCHES` (`nightscoutId`)")

            // Migration of profileSwitches table (insulinPeakTime must be migrated in MainApp)
            connection.execSQL("CREATE TABLE IF NOT EXISTS new_profileSwitches (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `basalBlocks` TEXT NOT NULL, `isfBlocks` TEXT NOT NULL, `icBlocks` TEXT NOT NULL, `targetBlocks` TEXT NOT NULL, `glucoseUnit` TEXT NOT NULL, `profileName` TEXT NOT NULL, `timeshift` INTEGER NOT NULL, `percentage` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER, `insulinLabel` TEXT NOT NULL, `insulinEndTime` INTEGER NOT NULL, `insulinPeakTime` INTEGER NOT NULL, `concentration` REAL NOT NULL, FOREIGN KEY(`referenceId`) REFERENCES `profileSwitches`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            connection.execSQL(
                """
                INSERT INTO new_profileSwitches (id, version, dateCreated, isValid, referenceId, timestamp, utcOffset, basalBlocks, isfBlocks, icBlocks, targetBlocks, glucoseUnit, profileName, timeshift, percentage, duration, nightscoutSystemId, nightscoutId, pumpType, pumpSerial, temporaryId, pumpId, startId, endId, insulinLabel, insulinEndTime, insulinPeakTime, concentration)
                SELECT id, version, dateCreated, isValid, referenceId, timestamp, utcOffset, basalBlocks, isfBlocks, icBlocks, targetBlocks, glucoseUnit, profileName, timeshift, percentage, duration, nightscoutSystemId, nightscoutId, pumpType, pumpSerial, temporaryId, pumpId, startId, endId, '', -1, -1, 1.0 
                FROM `$TABLE_PROFILE_SWITCHES`
                """.trimIndent()
            )
            connection.execSQL("DROP TABLE `$TABLE_PROFILE_SWITCHES`")
            connection.execSQL("ALTER TABLE new_profileSwitches RENAME TO `$TABLE_PROFILE_SWITCHES`")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_profileSwitches_id` ON `$TABLE_PROFILE_SWITCHES` (`id`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_profileSwitches_referenceId` ON `$TABLE_PROFILE_SWITCHES` (`referenceId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_profileSwitches_timestamp` ON `$TABLE_PROFILE_SWITCHES` (`timestamp`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_profileSwitches_nightscoutId` ON `$TABLE_PROFILE_SWITCHES` (`nightscoutId`)")

            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(connection)
        }
    }

    internal val migration33to34 = object : Migration(33, 34) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `$TABLE_EFFECTIVE_PROFILE_SWITCHES` ADD COLUMN `originalPsId` INTEGER DEFAULT NULL")
            // Remove redundant indexes on primary key columns
            connection.execSQL("DROP INDEX IF EXISTS `index_effectiveProfileSwitches_id`")
            connection.execSQL("DROP INDEX IF EXISTS `index_boluses_id`")
            connection.execSQL("DROP INDEX IF EXISTS `index_bolusCalculatorResults_id`")
            connection.execSQL("DROP INDEX IF EXISTS `index_bolusCalculatorResults_isValid`")
            connection.execSQL("DROP INDEX IF EXISTS `index_carbs_id`")
            connection.execSQL("DROP INDEX IF EXISTS `index_carbs_isValid`")
            connection.execSQL("DROP INDEX IF EXISTS `index_extendedBoluses_id`")
            connection.execSQL("DROP INDEX IF EXISTS `index_extendedBoluses_isValid`")
            connection.execSQL("DROP INDEX IF EXISTS `index_extendedBoluses_pumpSerial`")
            connection.execSQL("DROP INDEX IF EXISTS `index_extendedBoluses_pumpType`")
            connection.execSQL("DROP INDEX IF EXISTS `index_glucoseValues_id`")
            connection.execSQL("DROP INDEX IF EXISTS `index_glucoseValues_sourceSensor`")
            connection.execSQL("DROP INDEX IF EXISTS `index_profileSwitches_id`")
            connection.execSQL("DROP INDEX IF EXISTS `index_temporaryBasals_id`")
            connection.execSQL("DROP INDEX IF EXISTS `index_temporaryBasals_isValid`")
            connection.execSQL("DROP INDEX IF EXISTS `index_temporaryBasals_pumpType`")
            connection.execSQL("DROP INDEX IF EXISTS `index_temporaryBasals_pumpSerial`")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_temporaryBasals_pumpId` ON `$TABLE_TEMPORARY_BASALS` (`pumpId`)")
            connection.execSQL("DROP INDEX IF EXISTS `index_temporaryTargets_id`")
            connection.execSQL("DROP INDEX IF EXISTS `index_temporaryTargets_isValid`")
            connection.execSQL("DROP INDEX IF EXISTS `index_therapyEvents_id`")
            connection.execSQL("DROP INDEX IF EXISTS `index_therapyEvents_isValid`")
            connection.execSQL("DROP INDEX IF EXISTS `index_totalDailyDoses_id`")
            connection.execSQL("DROP INDEX IF EXISTS `index_totalDailyDoses_isValid`")
            connection.execSQL("DROP INDEX IF EXISTS `index_totalDailyDoses_pumpType`")
            connection.execSQL("DROP INDEX IF EXISTS `index_totalDailyDoses_pumpSerial`")
            connection.execSQL("DROP INDEX IF EXISTS `index_foods_id`")
            connection.execSQL("DROP INDEX IF EXISTS `index_foods_isValid`")
            connection.execSQL("DROP INDEX IF EXISTS `index_deviceStatus_id`")
            connection.execSQL("DROP INDEX IF EXISTS `index_runningModes_id`")
            connection.execSQL("DROP INDEX IF EXISTS `index_heartRate_id`")
            connection.execSQL("DROP INDEX IF EXISTS `index_stepsCount_id`")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(connection)
        }
    }

    internal val migration34to35 = object : Migration(34, 35) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("DROP TABLE IF EXISTS `$TABLE_CALIBRATION_ENTRIES`")
            connection.execSQL("CREATE TABLE IF NOT EXISTS `$TABLE_CALIBRATION_ENTRIES` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `fingerstickMgdl` REAL NOT NULL, `sensorMgdlAtPairing` REAL NOT NULL, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER, FOREIGN KEY(`referenceId`) REFERENCES `$TABLE_CALIBRATION_ENTRIES`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_calibrationEntries_nightscoutId` ON `$TABLE_CALIBRATION_ENTRIES` (`nightscoutId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_calibrationEntries_referenceId` ON `$TABLE_CALIBRATION_ENTRIES` (`referenceId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_calibrationEntries_timestamp` ON `$TABLE_CALIBRATION_ENTRIES` (`timestamp`)")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(connection)
        }
    }

    /** List of all migrations for easy reply in tests. */
    @VisibleForTesting
    internal val migrations = arrayOf(migration22to23, migration23to24, migration24to25, migration25to26, migration26to27, migration27to28, migration28to29, migration29to30, migration30to31, migration31to32, migration32to33, migration33to34, migration34to35)
}
