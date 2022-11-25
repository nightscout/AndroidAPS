package info.nightscout.pump.dana.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = TABLE_DANA_HISTORY,
        indices = [Index("code", "timestamp")])
data class DanaHistoryRecord(
    @PrimaryKey var timestamp: Long,
    var code: Byte = 0x0F,
    var value: Double = 0.0,
    var bolusType: String = "None",
    var stringValue: String = "",
    var duration: Long = 0,
    var dailyBasal: Double = 0.0,
    var dailyBolus: Double = 0.0,
    var alarm: String = ""
)
