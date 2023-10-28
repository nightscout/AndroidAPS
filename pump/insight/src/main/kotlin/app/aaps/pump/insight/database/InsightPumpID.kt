package app.aaps.pump.insight.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = DATABASE_INSIGHT_PUMP_IDS,
    indices = [
        Index("timestamp"),
        Index("pumpSerial"),
        Index("eventType")
    ]
)
data class InsightPumpID(
    var timestamp: Long,
    var eventType: EventType = EventType.None,
    val pumpSerial: String? = null,
    @PrimaryKey
    var eventID: Long
) {

    enum class EventType {
        PumpStarted,
        PumpStopped,
        PumpPaused,
        StartOfTBR,
        EndOfTBR,
        None;
    }
}