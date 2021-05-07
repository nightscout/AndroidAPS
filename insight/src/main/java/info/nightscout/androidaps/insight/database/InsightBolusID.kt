package info.nightscout.androidaps.insight.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = DATABASE_INSIGHT_BOLUS_IDS,
    indices = [Index("bolusID")])
data class InsightBolusID(
    var timestamp: Long,
    var pumpSerial: String? = "None",
    var bolusID: Int? = null,
    var startID: Long? = null,
    var endID: Long? = null
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}