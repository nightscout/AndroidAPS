package app.aaps.pump.diaconn.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = TABLE_DIACONN_HISTORY,
    indices = [Index("code", "timestamp")])
data class DiaconnHistoryRecord(
    @PrimaryKey var timestamp: Long,
    var code: Byte = 0x0F,
    var value: Double = 0.0,
    var bolusType: String = "None",
    var stringValue: String = "",
    var duration: Int = 0,
    var dailyBasal: Double = 0.0,
    var dailyBolus: Double = 0.0,
    var alarm: String = "",
    var lognum: Int = 0,
    var wrappingCount: Int = 0,
    var pumpUid: String = ""
)
