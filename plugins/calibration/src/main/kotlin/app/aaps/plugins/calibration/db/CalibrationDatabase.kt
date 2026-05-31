package app.aaps.plugins.calibration.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CalibrationEntry::class],
    exportSchema = true,
    version = CalibrationDatabase.VERSION
)
abstract class CalibrationDatabase : RoomDatabase() {

    abstract fun calibrationEntryDao(): CalibrationEntryDao

    companion object {

        const val VERSION = 1

        fun build(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                CalibrationDatabase::class.java,
                "calibration_database.db"
            )
                .fallbackToDestructiveMigration(false)
                .build()
    }
}
