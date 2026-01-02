package app.aaps.pump.common.data

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.pump.common.defs.PumpRunningState
import java.util.Date

/**
 * Created by andy on 4/28/18.
 */
abstract class PumpStatus(var pumpType: PumpType) {

    // connection
    var lastDataTime: Long = 0
    var lastConnection = 0L
    var previousConnection = 0L // here should be stored last connection of previous session (so needs to be

    // read before lastConnection is modified for first time).
    // last bolus
    var lastBolusTime: Date? = null
    var lastBolusAmount: Double? = null

    // other pump settings
    var activeProfileName = "0"
    var reservoirRemainingUnits = 0.0
    var reservoirFullUnits = 0
    var batteryRemaining : Int? = null // percent, so 0-100
    var batteryVoltage: Double? = null

    // iob
    var iob: String? = null

    // TDD
    var dailyTotalUnits: Double? = null
    var maxDailyTotalUnits: String? = null
    var units: String? = null // GlucoseUnit.MGDL.asText or GlucoseUnit.MMOL.asText
    var pumpRunningState = PumpRunningState.Running
    var basalsByHour: DoubleArray? = null
    var tempBasalStart: Long? = null
    var tempBasalAmount: Double? = 0.0
    var tempBasalLength: Int? = 0
    var tempBasalEnd: Long? = null
    var pumpTime: PumpTimeDifferenceDto? = null

    fun setLastCommunicationToNow() {
        lastDataTime = System.currentTimeMillis()
        lastConnection = System.currentTimeMillis()
    }

    abstract val errorInfo: String?

}