package app.aaps.pump.aaruy.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** The daily totals of the pump, for the history screen only. The pump day starts at midnight China time. */
@Entity(tableName = "history_daily_unit_info")
class HistoryDailyUnitInfo {
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0

    /** The start of the pump day, in milliseconds. */
    var dateTimestamp: Long = 0

    /** All insulin of the day, times 10: 102 is 10.2 U. */
    var dailyTotal: Int = 0

    /** Bolus insulin of the day, times 10: 102 is 10.2 U. */
    var dailyBolus: Int = 0
    var pumpName: String = ""
    var isReplace: Boolean = false

    /** Not used by this driver. The vendor app uploaded the rows to its server, AAPS does not. */
    var isUploaded: Boolean = false
    var timestamp:Long = 0
    var pumpType:Int = 0
    override fun toString(): String {
        return "HistoryDailyUnitInfo(id=$id, date=$dateTimestamp, dailyTotal=$dailyTotal, dailyBolus=$dailyBolus, pumpName='$pumpName', isReplace=$isReplace, " +
            "isUploaded=$isUploaded, timestamp=$timestamp, pumpType=$pumpType)"
    }


}