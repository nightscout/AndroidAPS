package info.nightscout.androidaps.database

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
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
            .addMigrations(migration20to21)
            .addMigrations(migration21to22)
            .addCallback(object : Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    createCustomIndexes(db)
                }
            })
            .fallbackToDestructiveMigration()
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

    private val migration20to21 = object : Migration(20,21) {
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

    private val migration21to22 = object : Migration(21,22) {
        override fun migrate(database: SupportSQLiteDatabase) {
            addColumnIfNotExists(database,"glucoseValues", "smoothed", "REAL")
            dropCustomIndexes(database)
        }
    }

    private fun addColumnIfNotExists(db: SupportSQLiteDatabase, table: String, columnToCheck: String, columnTypeDefinition: String) {
        if(!columnExistsInTable(db, table, columnToCheck)) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `$columnToCheck` $columnTypeDefinition")
        }
    }

    private fun columnExistsInTable(db: SupportSQLiteDatabase?, table: String, columnToCheck: String?): Boolean {
        var cursor: Cursor? = null
        return try {
            cursor = db?.query("SELECT * FROM $table LIMIT 0", null)
            cursor?.getColumnIndex(columnToCheck) !== -1
        } catch (Exp: SQLiteException) {
            false
        } finally {
            cursor?.close()
        }
    }

}