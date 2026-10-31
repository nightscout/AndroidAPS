package app.aaps.pump.omnipod.omnipod5.history.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [HistoryRecordEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class O5HistoryDatabase : RoomDatabase() {

    abstract fun historyRecordDao(): HistoryRecordDao

    companion object {
        fun build(context: Context): O5HistoryDatabase = Room.databaseBuilder(
            context.applicationContext,
            O5HistoryDatabase::class.java,
            "omnipod_o5_history_database.db"
        ).addMigrations(MIGRATION_1_2).build()
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE historyrecords ADD COLUMN resolvedAt INTEGER")
            }
        }
    }
}
