package app.aaps.pump.aaruy.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One entry of the pump history, kept for the history screen. */
@Entity(tableName = "history_unit_info")
class HistoryUnitInfo {
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0

    /** In milliseconds. */
    var time: Long = 0

    /**
     * 0 basal, 1 temp basal, 2 bolus (also extended), 3 alarm, 4 rewind, 5 prime, 6 factory reset, 7 pause,
     * 110 a new pump paired (written by the driver). 108 carbs and 109 glucose come from the vendor app and are not written here.
     */
    var type: Int = 0

    /** Basal and temp basal: the rate. Bolus: the amount now. Both in thousandths of a unit. Alarm: the low byte of the code. */
    var data: Long = 0

    /** How long the bolus took, in seconds. */
    var immTime: Long = 0

    /** Minutes. For a bolus 0 means no extended part, otherwise [delayBolus] is the extended amount. */
    var injectTime: Long = 0

    /** For a bolus only: the extended amount. */
    var delayBolus: Long = 0

    /** Not written by this driver. The column stays so that the table does not change. */
    var bgValue: Double = 0.0

    /** A counter from the pump. Seen on a real pump: it counts by 4 and starts again at 64. */
    var reserved: Int = 0
    var blueName: String = ""

    /** Not used by this driver. The vendor app uploaded the rows to its server, AAPS does not. */
    var isUploaded: Boolean = false
    var pumpType:Int = 0

    override fun toString(): String {
        return "HistoryUnitInfo(id=$id, time=$time, type=$type, data=$data, immTime=$immTime, injectTime=$injectTime, delayBolus=$delayBolus, " +
            "bgValue=$bgValue, reserved=$reserved, blueName='$blueName', isUploaded=$isUploaded, pumpType=$pumpType)"
    }


}