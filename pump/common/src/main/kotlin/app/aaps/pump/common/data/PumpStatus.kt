package app.aaps.pump.common.data

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.pump.common.defs.PumpRunningState
import app.aaps.pump.common.defs.TempBasalPair
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
    var lastBolusTime: Date? = null  // legacy
    var lastBolusAmount: Double? = null // legqacy
    var lastBolus: DetailedBolusInfo? = null

    // other pump settings
    var reservoirRemainingUnits = 0.0
    var reservoirFullUnits = 0
    var batteryRemaining : Int? = null // percent, so 0-100
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
        get() = field
        set(value) {
            if (value!=null) {
                if (value.start==null) {
                    this.currentTempBasalEstimatedEnd = System.currentTimeMillis() + (value.durationMinutes * 60 * 1000)
                } else {
                    this.currentTempBasalEstimatedEnd = value.start!! + (value.durationMinutes * 60 * 1000)
                }
            } else {
                this.currentTempBasalEstimatedEnd = null
            }
            field = value
        }

    var currentTempBasalInternal: TempBasalPair? = null


    var currentTempBasalEstimatedEnd: Long? = null
    var tempBasalLegacyMode = false

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

    fun setLastCommunicationToNow() {
        lastDataTime = System.currentTimeMillis()
        lastConnection = System.currentTimeMillis()
        updateLastConnectionInFragment()
    }

    fun clearTbr() {
        this.currentTempBasal = null
        this.currentTempBasalEstimatedEnd = null
    }

    abstract val errorInfo: String?

    /**
     * This needs to be overriden by any pump status implementation
     */
    open fun updateLastConnectionInFragment() {

    }

}
