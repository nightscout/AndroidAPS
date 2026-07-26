package app.aaps.pump.diaconn.database

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import app.aaps.core.ui.compose.pump.PumpHistoryRecord

@Entity(tableName = TABLE_DIACONN_HISTORY,
    indices = [Index("code", "timestamp")])
data class DiaconnHistoryRecord(
    @PrimaryKey override var timestamp: Long,
    override var code: Byte = 0x0F,
    override var value: Double = 0.0,
    override var bolusType: String = "None",
    override var stringValue: String = "",
    var duration: Int = 0,
    override var dailyBasal: Double = 0.0,
    override var dailyBolus: Double = 0.0,
    override var alarm: String = "",
    var lognum: Int = 0,
    var wrappingCount: Int = 0,
    var pumpUid: String = ""
) : PumpHistoryRecord {

    @get:Ignore
    override val durationMinutes: Int get() = duration
}
