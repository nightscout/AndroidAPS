package info.nightscout.database.impl

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Room
import androidx.room.RoomDatabase.Callback
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.aaps.database.entities.TABLE_HEART_RATE
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
            .addMigrations(*migrations)
            .addCallback(object : Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    createCustomIndexes(db)
                }
            })
            .fallbackToDestructiveMigration()
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Qualifier
    annotation class DbFileName

    private fun createCustomIndexes(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_temporaryBasals_end` ON `temporaryBasals` (`timestamp` + `duration`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_extendedBoluses_end` ON `extendedBoluses` (`timestamp` + `duration`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_temporaryTargets_end` ON `temporaryTargets` (`timestamp` + `duration`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_carbs_end` ON `carbs` (`timestamp` + `duration`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_offlineEvents_end` ON `offlineEvents` (`timestamp` + `duration`)")
    }

    private fun dropCustomIndexes(database: SupportSQLiteDatabase) {
        database.execSQL("DROP INDEX IF EXISTS `index_temporaryBasals_end`")
        database.execSQL("DROP INDEX IF EXISTS `index_extendedBoluses_end`")
        database.execSQL("DROP INDEX IF EXISTS `index_temporaryTargets_end`")
        database.execSQL("DROP INDEX IF EXISTS `index_carbs_end`")
        database.execSQL("DROP INDEX IF EXISTS `index_offlineEvents_end`")
    }

    private val migration20to21 = object : Migration(20, 21) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE IF EXISTS offlineEvents")
            database.execSQL("CREATE TABLE IF NOT EXISTS `offlineEvents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `reason` TEXT NOT NULL, `duration` INTEGER NOT NULL, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER, FOREIGN KEY(`referenceId`) REFERENCES `offlineEvents`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_offlineEvents_id` ON offlineEvents (`id`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_offlineEvents_isValid` ON offlineEvents (`isValid`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_offlineEvents_nightscoutId` ON offlineEvents (`nightscoutId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_offlineEvents_referenceId` ON offlineEvents (`referenceId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_offlineEvents_timestamp` ON offlineEvents (`timestamp`)")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(database)
        }
    }

    private val migration21to22 = object : Migration(21, 22) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `carbs` ADD COLUMN `notes` TEXT")
            database.execSQL("ALTER TABLE `boluses` ADD COLUMN `notes` TEXT")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(database)
        }
    }

    private val migration22to23 = object : Migration(22, 23) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `deviceStatus` ADD COLUMN `isCharging` INTEGER")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(database)
        }
    }

    private val migration23to24 = object : Migration(23, 24) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
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
            database.execSQL("""CREATE INDEX IF NOT EXISTS `index_heartRate_id` ON `$TABLE_HEART_RATE` (`id`)""")
            database.execSQL("""CREATE INDEX IF NOT EXISTS `index_heartRate_timestamp` ON `$TABLE_HEART_RATE` (`timestamp`)""")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(database)
        }
    }

    /** List of all migrations for easy reply in tests. */
    @VisibleForTesting
    internal val migrations = arrayOf(migration20to21, migration21to22, migration22to23, migration23to24)
}
