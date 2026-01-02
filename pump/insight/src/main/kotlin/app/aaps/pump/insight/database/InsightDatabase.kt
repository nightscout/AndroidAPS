package app.aaps.pump.insight.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

const val DATABASE_INSIGHT_BOLUS_IDS = "insightBolusIDs"
const val DATABASE_INSIGHT_PUMP_IDS = "insightPumpIDs"
const val DATABASE_INSIGHT_HISTORY_OFFSETS = "insightHistoryOffsets"

@Database(
    entities = [InsightBolusID::class, InsightHistoryOffset::class, InsightPumpID::class],
    exportSchema = true,
    version = InsightDatabase.VERSION
)
@TypeConverters(Converters::class)
abstract class InsightDatabase : RoomDatabase() {

    abstract fun insightDatabaseDao(): InsightDatabaseDao

    companion object {

        const val VERSION = 2

        fun build(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                InsightDatabase::class.java,
                "insight_database.db"
            )
                .fallbackToDestructiveMigration(false)
                .build()
    }
}
