package info.nightscout.androidaps.insight.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = DATABASE_INSIGHT_PUMP_IDS,
    indices = [Index("timestamp")])
data class InsightPumpID(
    var timestamp: Long,
    var eventType: String? = null,
    var pumpSerial: String? = "None",
    @PrimaryKey
    var eventID: Long
) {
    fun getId(): Long = eventID
}