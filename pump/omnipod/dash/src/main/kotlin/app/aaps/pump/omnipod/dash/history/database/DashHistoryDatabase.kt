package app.aaps.pump.omnipod.dash.history.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
                .fallbackToDestructiveMigration(false)
                .build()
    }
}
