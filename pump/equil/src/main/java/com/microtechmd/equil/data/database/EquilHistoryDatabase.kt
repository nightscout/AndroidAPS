package com.microtechmd.equil.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

const val TABLE_EQUIL_HISTORY_PUMP = "equilHistoryPump"
const val TABLE_EQUIL_HISTORY_RECORD = "equilHistoryRecord"
const val TABLE_EQUIL_LOGS = "EquilLogs"

@Database(
    entities = [EquilHistoryPump::class, EquilHistoryRecord::class],
    exportSchema = true,
    version = EquilHistoryDatabase.VERSION
)
@TypeConverters(Converters::class)
abstract class EquilHistoryDatabase : RoomDatabase() {

    abstract fun historyRecordDao(): EquilHistoryRecordDao

    companion object {

        const val VERSION = 10
        fun build(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                EquilHistoryDatabase::class.java,
                "equil_database.db"
            )
                .fallbackToDestructiveMigration()
                .build()
    }
}
