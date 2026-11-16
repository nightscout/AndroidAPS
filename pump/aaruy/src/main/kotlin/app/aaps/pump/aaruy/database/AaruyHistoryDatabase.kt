package app.aaps.pump.aaruy.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.impl.Migration_1_2

const val TABLE_AARUY_HISTORY = "aaruyHistory"

@Database(
    entities = [HistoryDailyUnitInfo::class, HistoryUnitInfo::class],
    version = 2,
    exportSchema = true
)
abstract class AaruyHistoryDatabase : RoomDatabase() {
    abstract fun historyDailyUnitInfoDao(): HistoryDailyUnitInfoDao
    abstract fun historyUnitInfoDao(): HistoryUnitInfoDao

    companion object {

        fun build(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                AaruyHistoryDatabase::class.java,
                "aaruy_database.db"
            )
                .fallbackToDestructiveMigration()
                .addMigrations(Migration_1_2)
                .build()

        private val Migration_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE  history_daily_unit_info ADD COLUMN pumpType INTEGER NOT NULL DEFAULT 0"
                )

                database.execSQL(
                    "ALTER TABLE  history_unit_info ADD COLUMN pumpType INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
