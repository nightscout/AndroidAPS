package app.aaps.pump.omnipod.eros.history.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ErosHistoryRecordEntity::class],
    exportSchema = false,
    version = ErosHistoryDatabase.VERSION
)
abstract class ErosHistoryDatabase : RoomDatabase() {

    abstract fun historyRecordDao(): ErosHistoryRecordDao

    companion object {

        const val VERSION = 1

        fun build(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                ErosHistoryDatabase::class.java,
                "omnipod_eros_history_database.db"
            )
                .fallbackToDestructiveMigration(false)
                .build()
    }
}
