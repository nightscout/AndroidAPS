package app.aaps.pump.insight.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = DATABASE_INSIGHT_BOLUS_IDS,
    indices = [
        Index("bolusID"),
        Index("pumpSerial"),
        Index("timestamp")
    ]
)
data class InsightBolusID(
    var timestamp: Long,
    val pumpSerial: String? = null,
    val bolusID: Int? = null,
    var startID: Long? = null,
    var endID: Long? = null
) {

    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}