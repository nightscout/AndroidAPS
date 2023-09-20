package com.microtechmd.equil.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

const val TABLE_DANA_HISTORY_PUMP = "equilHistoryPump"
const val TABLE_DANA_HISTORY_RECORD = "equilHistoryRecord"

@Database(
    entities = [EquilHistoryPump::class, EquilHistoryRecord::class],
    exportSchema = true,
    version = DanaHistoryDatabase.VERSION
)
@TypeConverters(Converters::class)
abstract class DanaHistoryDatabase : RoomDatabase() {

    abstract fun historyRecordDao(): EquilHistoryRecordDao

    companion object {

        const val VERSION = 1

        fun build(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                DanaHistoryDatabase::class.java,
                "equil_database.db"
            )
                .fallbackToDestructiveMigration()
                .build()
    }
}
