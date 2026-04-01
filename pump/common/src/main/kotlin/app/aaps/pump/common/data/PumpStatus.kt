package app.aaps.pump.common.data

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.pump.common.defs.TempBasalPair
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Date

/**
 * Created by andy on 4/28/18.
 */
abstract class PumpStatus(var pumpType: PumpType) {

    // connection
    var lastDataTime: Long = 0
    val lastConnectionFlow = MutableStateFlow(0L)
    var lastConnection: Long
        get() = lastConnectionFlow.value
        set(value) {
            lastConnectionFlow.value = value
        }
    var previousConnection = 0L // here should be stored last connection of previous session (so needs to be

    // bolus
    val lastBolusTimeFlow = MutableStateFlow<Date?>(null)
    var lastBolusTime: Date?
        get() = lastBolusTimeFlow.value
        set(value) {
            lastBolusTimeFlow.value = value
        }
    val lastBolusAmountFlow = MutableStateFlow<Double?>(null)
    var lastBolusAmount: Double?
        get() = lastBolusAmountFlow.value
        set(value) {
            lastBolusAmountFlow.value = value
        }
    var lastBolus: DetailedBolusInfo? = null

    // other pump settings
    val reservoirRemainingUnitsFlow = MutableStateFlow(0.0)
    var reservoirRemainingUnits: Double
        get() = reservoirRemainingUnitsFlow.value
        set(value) {
            reservoirRemainingUnitsFlow.value = value
        }
    var reservoirFullUnits = 0
    val batteryRemainingFlow = MutableStateFlow<Int?>(null)
    var batteryRemaining: Int?
        get() = batteryRemainingFlow.value
        set(value) {
            batteryRemainingFlow.value = value
        }
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

    // temp basal
    var currentTempBasal: TempBasalPair? = null
        set(value) {
            if (value != null) {
                if (value.start == null) {
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
