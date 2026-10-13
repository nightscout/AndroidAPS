package app.aaps.pump.diaconn.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

const val TABLE_DIACONN_HISTORY = "diaconnHistory"

@Database(
    entities = [DiaconnHistoryRecord::class],
    exportSchema = true,
    version = DiaconnHistoryDatabase.VERSION
)
abstract class DiaconnHistoryDatabase : RoomDatabase() {

    abstract fun historyRecordDao(): DiaconnHistoryRecordDao

    companion object {

        const val VERSION = 2

        fun build(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                DiaconnHistoryDatabase::class.java,
                "diaconn_database.db"
            )
                .fallbackToDestructiveMigration(false)
                .build()
    }
}
