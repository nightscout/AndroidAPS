package info.nightscout.androidaps.plugins.pump.omnipod.dash.history.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.BolusRecord

const val DASH_TABLE_NAME = "historyrecords"
const val DASH_BOLUS_COLUMN_PREFIX = "bolusRecord_"
const val DASH_TBS_COLUMN_PREFIX = "tempBasalRecord_"
const val DASH_BASAL_COLUMN_PREFIX = "basalprofile_"

@Database(
    entities = [HistoryRecordEntity::class],
    exportSchema = false,
    version = DashHistoryDatabase.VERSION
)
@TypeConverters(Converters::class)
abstract class DashHistoryDatabase : RoomDatabase() {

    abstract fun historyRecordDao(): HistoryRecordDao

    companion object {

        const val VERSION = 4

        fun build(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                DashHistoryDatabase::class.java,
                "omnipod_dash_history_database.db",
            )
                .addMigrations(migration3to4)
                .fallbackToDestructiveMigration()
                .build()

        private val migration3to4 = object : Migration(3,4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `${DASH_TABLE_NAME}` ADD COLUMN `${DASH_BOLUS_COLUMN_PREFIX}notes` TEXT")
            }
        }
    }
}
