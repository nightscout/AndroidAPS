package info.nightscout.aaps.pump.common.data

import info.nightscout.aaps.pump.common.data.PumpTimeDifferenceDto
import info.nightscout.interfaces.pump.defs.PumpType
import info.nightscout.aaps.pump.common.defs.PumpRunningState
import info.nightscout.aaps.pump.common.defs.TempBasalPair
import java.util.Date

/**
 * Created by andy on 4/28/18.
 */
abstract class PumpStatus(var pumpType: PumpType) {

    // connection
    var lastDataTime: Long = 0
    var lastConnection = 0L
    var previousConnection = 0L // here should be stored last connection of previous session (so needs to be

    // bolus
    var lastBolusTime: Date? = null
    var lastBolusAmount: Double? = null

    // other pump settings
    var reservoirRemainingUnits = 0.0
    var reservoirFullUnits = 0
    var batteryRemaining = 0 // percent, so 0-100
    var batteryVoltage: Double? = null
    var units: String? = null // Constants.MGDL or Constants.MMOL

    // iob
    var iob: String? = null

    // basal profile
    var basalsByHour: DoubleArray? = null
    var activeProfileName = "1"

    // TDD
    var dailyTotalUnits: Double? = null
    var maxDailyTotalUnits: String? = null


    // state
    var pumpRunningState = PumpRunningState.Running

    // temp basal
    var currentTempBasal: TempBasalPair? = null
    var currentTempBasalEstimatedEnd: Long? = null

    // time
    var pumpTime: PumpTimeDifferenceDto? = null


    // TODO refactor to use TempBasalPair - remove this
    var tempBasalStart: Long? = null
    var tempBasalAmount: Double? = 0.0
    var tempBasalPercent: Int? = 100
    var tempBasalDuration: Int? = 0
    var tempBasalEnd: Long? = null


// OLD - Start
//     var units: String? = null // Constants.MGDL or Constants.MMOL
//     var pumpRunningState = PumpRunningState.Running
//     var basalsByHour: DoubleArray? = null
//     var tempBasalStart: Long? = null
//     var tempBasalAmount: Double? = 0.0
//     var tempBasalLength: Int? = 0
//     var tempBasalEnd: Long? = null
//     var pumpTime: PumpTimeDifferenceDto? = null
// OLD - End

    abstract fun initSettings()

    fun setLastCommunicationToNow() {
        lastDataTime = System.currentTimeMillis()
        lastConnection = System.currentTimeMillis()
    }

    abstract val errorInfo: String?

}
