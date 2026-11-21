package app.aaps.pump.aaruy.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** The file name on every platform. On Android it is the one the database always had, so the stored log stays. */
const val AARUY_DATABASE_FILE = "aaruy_database.db"

/**
 * The pump log the driver keeps. Each platform opens it with its own builder and the bundled SQLite driver; the tables,
 * the DAOs and the migrations are here, once.
 */
@Database(
    entities = [HistoryDailyUnitInfo::class, HistoryUnitInfo::class],
    version = 2,
    exportSchema = true
)
// @ConstructedBy lets the class live in commonMain: Room generates the initialiser per target, into the expect object.
@ConstructedBy(AaruyHistoryDatabaseConstructor::class)
abstract class AaruyHistoryDatabase : RoomDatabase() {

    abstract fun historyDailyUnitInfoDao(): HistoryDailyUnitInfoDao
    abstract fun historyUnitInfoDao(): HistoryUnitInfoDao

    companion object {

        /** Every step from an older version. Given to the builder of each platform, so the history is the same everywhere. */
        val migrations: Array<Migration> = arrayOf(
            object : Migration(1, 2) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE  history_daily_unit_info ADD COLUMN pumpType INTEGER NOT NULL DEFAULT 0")
                    connection.execSQL("ALTER TABLE  history_unit_info ADD COLUMN pumpType INTEGER NOT NULL DEFAULT 0")
                }
            }
        )
    }
}

@Suppress("KotlinNoActualForExpect")
expect object AaruyHistoryDatabaseConstructor : RoomDatabaseConstructor<AaruyHistoryDatabase> {

    override fun initialize(): AaruyHistoryDatabase
}
