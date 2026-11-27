package app.aaps.pump.ap550s.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

const val TABLE_AP550_HISTORY = "ap550History"

@Database(
    entities = [Ap550HistoryRecord::class],
    exportSchema = false,
    version = Ap550HistoryDatabase.VERSION
)
abstract class Ap550HistoryDatabase : RoomDatabase() {

    abstract fun historyRecordDao(): Ap550HistoryRecordDao

    companion object {

        const val VERSION = 1

        fun build(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                Ap550HistoryDatabase::class.java,
                "ap550_database.db"
            )
                .fallbackToDestructiveMigration()
                .build()
    }
}
