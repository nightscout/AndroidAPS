package app.aaps.pump.dana.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

const val TABLE_DANA_HISTORY = "danaHistory"

@Database(
    entities = [DanaHistoryRecord::class],
    exportSchema = true,
    version = DanaHistoryDatabase.VERSION
)
abstract class DanaHistoryDatabase : RoomDatabase() {

    abstract fun historyRecordDao(): DanaHistoryRecordDao

    companion object {

        const val VERSION = 1

        fun build(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                DanaHistoryDatabase::class.java,
                "dana_database.db"
            )
                .fallbackToDestructiveMigration(false)
                .build()
    }
}
