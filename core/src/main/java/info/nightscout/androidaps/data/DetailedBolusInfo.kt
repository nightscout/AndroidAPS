package info.nightscout.androidaps.data

import android.content.Context
import com.google.gson.Gson
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.database.entities.BolusCalculatorResult
import info.nightscout.androidaps.database.entities.Carbs
import info.nightscout.androidaps.database.entities.TherapyEvent

class DetailedBolusInfo {

    // Requesting parameters for driver
    @JvmField var insulin = 0.0
    @JvmField var carbs = 0.0

    // Additional requesting parameters
    @JvmField var timestamp = System.currentTimeMillis()
    @JvmField var carbTime = 0 // time shift of carbs in minutes
    @JvmField var lastKnownBolusTime: Long = 0 // for SMB check
    @JvmField var deliverAtTheLatest: Long = 0 // SMB should be delivered within 1 min from this time
    @Transient var context: Context? = null // context for progress dialog

    // Prefilled info for storing to db
    var bolusCalculatorResult: BolusCalculatorResult? = null
    var eventType = TherapyEvent.Type.MEAL_BOLUS
    var notes: String? = null
    var mgdlGlucose: Double? = null // Bg value in mgdl
    var glucoseType: TherapyEvent.MeterType? = null // NS values: Manual, Finger, Sensor
    var bolusType = Bolus.Type.NORMAL

    // Collected info from driver
    var pumpType: InterfaceIDs.PumpType? = null // if == USER
    var pumpSerial: String? = null
    var bolusPumpId: Long? = null
    var carbsPumpId: Long? = null

    fun createTherapyEvent(): TherapyEvent? =
        if (mgdlGlucose != null || notes != null)
            TherapyEvent(
                timestamp = timestamp,
                type = TherapyEvent.Type.NOTE,
                glucoseUnit = TherapyEvent.GlucoseUnit.MGDL,
                note = notes,
                glucose = mgdlGlucose,
                glucoseType = glucoseType
            )
        else null

    fun createBolus(): Bolus? =
        if (insulin != 0.0)
            Bolus(
                timestamp = timestamp,
                amount = insulin,
                type = bolusType,
                isBasalInsulin = false
            )
        else null

    fun createCarbs(): Carbs? =
        if (carbs != 0.0)
            Carbs(
                timestamp = timestamp,
                amount = carbs,
                duration = 0
            )
        else null

    fun toJsonString(): String =
        Gson().toJson(this)

    fun copy(): DetailedBolusInfo {
        val n = DetailedBolusInfo()
        n.insulin = insulin
        n.carbs = carbs

        n.timestamp = timestamp
        n.carbTime = carbTime
        n.lastKnownBolusTime = lastKnownBolusTime
        n.deliverAtTheLatest = deliverAtTheLatest
        n.context = context

        n.bolusCalculatorResult = bolusCalculatorResult
        n.eventType = eventType
        n.notes = notes
        n.mgdlGlucose = mgdlGlucose
        n.glucoseType = glucoseType
        n.bolusType = bolusType

        n.pumpType = pumpType
        n.pumpSerial = pumpSerial
        n.bolusPumpId = bolusPumpId
        n.carbsPumpId = carbsPumpId
        return n
    }

    override fun toString(): String = toJsonString()

    companion object {

        fun fromJsonString(json: String): DetailedBolusInfo =
            Gson().fromJson(json, DetailedBolusInfo::class.java)
    }
}