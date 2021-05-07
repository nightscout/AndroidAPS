package info.nightscout.androidaps.insight.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
abstract class InsightDatabaseDao {

    @Query("SELECT * from $DATABASE_INSIGHT_BOLUS_IDS WHERE pumpSerial = :pumpSerial AND timestamp >= :timestamp - 259200000 AND timestamp <= :timestamp + 259200000 AND bolusID = :bolusID")
    abstract fun getInsightBolusID(pumpSerial: String, bolusID: Int, timestamp: Long): InsightBolusID?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun createOrUpdate(insightBolusID: InsightBolusID)

    @Query("SELECT * from $DATABASE_INSIGHT_HISTORY_OFFSETS WHERE pumpSerial = :pumpSerial")
    abstract fun getInsightHistoryOffset(pumpSerial: String): InsightHistoryOffset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun createOrUpdate(insightHistoryOffset: InsightHistoryOffset)

    @Query("SELECT * from $DATABASE_INSIGHT_PUMP_IDS WHERE pumpSerial = :pumpSerial AND (eventType = 'PumpStopped' OR eventType = 'PumpPaused') AND timestamp < :timestamp  ORDER BY timestamp DESC")
    abstract fun getPumpStoppedEvent(pumpSerial: String, timestamp: Long): InsightPumpID?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun createOrUpdate(insightPumpID: InsightPumpID)
}
