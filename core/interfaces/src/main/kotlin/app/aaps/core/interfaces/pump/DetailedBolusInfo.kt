package app.aaps.core.interfaces.pump

import android.content.Context
import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType

class DetailedBolusInfo {

    val id = System.currentTimeMillis()

    // Requesting parameters for driver
    @JvmField var insulin = 0.0
    @JvmField var carbs = 0.0

    // Additional requesting parameters
    @JvmField var timestamp = System.currentTimeMillis()
    var lastKnownBolusTime: Long = 0 // for SMB check
    var deliverAtTheLatest: Long = 0 // SMB should be delivered within 1 min from this time
    @Transient var context: Context? = null // context for progress dialog

    // Prefilled info for storing to db
    var bolusCalculatorResult: BCR? = null
    var eventType = TE.Type.MEAL_BOLUS
    var notes: String? = null
    var mgdlGlucose: Double? = null // Bg value in mgdl
    var glucoseType: TE.MeterType? = null // NS values: Manual, Finger, Sensor
    var bolusType = BS.Type.NORMAL
    var carbsDuration = 0L // in milliseconds

    // Collected info from driver
    var pumpType: PumpType? = null // if == USER
    var pumpSerial: String? = null
    var bolusPumpId: Long? = null
    var bolusTimestamp: Long? = null
    var carbsTimestamp: Long? = null

    /**
     * Used for create record going directly to db (record only)
     */
    fun createBolus(iCfg: ICfg): BS =
        if (insulin != 0.0)
            BS(
                timestamp = bolusTimestamp ?: timestamp,
                amount = insulin,
                type = bolusType,
                notes = notes,
                ids = IDs(pumpId = timestamp),
                iCfg = iCfg
            )
        else error("insulin == 0.0")

    fun createCarbs(): CA =
        if (carbs != 0.0)
            CA(
                timestamp = carbsTimestamp ?: timestamp,
                amount = carbs,
                duration = carbsDuration,
                notes = notes
            )
        else error("carbs == 0.0")

    fun copy(): DetailedBolusInfo {
        val n = DetailedBolusInfo()
        n.insulin = insulin
        n.carbs = carbs

        n.timestamp = timestamp
        n.lastKnownBolusTime = lastKnownBolusTime
        n.deliverAtTheLatest = deliverAtTheLatest
        n.context = context

        n.bolusCalculatorResult = bolusCalculatorResult
        n.eventType = eventType
        n.notes = notes
        n.mgdlGlucose = mgdlGlucose
        n.glucoseType = glucoseType
        n.bolusType = bolusType
        n.carbsDuration = carbsDuration

        n.pumpType = pumpType
        n.pumpSerial = pumpSerial
        n.bolusPumpId = bolusPumpId
        n.carbsTimestamp = carbsTimestamp
        return n
    }
}