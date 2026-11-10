package app.aaps.core.interfaces.pump

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import kotlin.time.Clock

/**
 * The extended part of a meal: [grams] spread over [durationHours] from [timestamp]. [delayMinutes] is how far
 * [timestamp] lies after the entry, for the user entry log only. See [DetailedBolusInfo.eCarbs].
 */
data class ECarbs(val grams: Int, val timestamp: Long, val durationHours: Int, val delayMinutes: Int)

class DetailedBolusInfo {

    val id = Clock.System.now().toEpochMilliseconds()

    // Requesting parameters for driver
    var insulin = 0.0
    var carbs = 0.0

    // Additional requesting parameters
    var timestamp = Clock.System.now().toEpochMilliseconds()
    var lastKnownBolusTime: Long = 0 // for SMB check
    var deliverAtTheLatest: Long = 0 // SMB should be delivered within 1 min from this time

    // Prefilled info for storing to db
    var bolusCalculatorResult: BCR? = null
    var eventType = TE.Type.MEAL_BOLUS
    var notes: String? = null
    var mgdlGlucose: Double? = null // Bg value in mgdl
    var glucoseType: TE.MeterType? = null // NS values: Manual, Finger, Sensor
    var bolusType = BS.Type.NORMAL
    var carbsDuration = 0L // in milliseconds

    /**
     * The extended part of the same meal (cake / pizza carbs type, or a QuickWizard with eCarbs). It is
     * stored exactly when [carbs] are: by [app.aaps.core.interfaces.queue.CommandQueue.bolus] after the bolus succeeded, or at once when
     * there is no insulin. So a failed bolus leaves no eCarbs without the carbs they belong to (#5491).
     */
    var eCarbs: ECarbs? = null

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

    fun createECarbs(): CA =
        eCarbs?.let {
            CA(
                timestamp = it.timestamp,
                amount = it.grams.toDouble(),
                duration = T.hours(it.durationHours.toLong()).msecs(),
                notes = notes
            )
        } ?: error("eCarbs == null")

    fun copy(): DetailedBolusInfo {
        val n = DetailedBolusInfo()
        n.insulin = insulin
        n.carbs = carbs

        n.timestamp = timestamp
        n.lastKnownBolusTime = lastKnownBolusTime
        n.deliverAtTheLatest = deliverAtTheLatest

        n.bolusCalculatorResult = bolusCalculatorResult
        n.eventType = eventType
        n.notes = notes
        n.mgdlGlucose = mgdlGlucose
        n.glucoseType = glucoseType
        n.bolusType = bolusType
        n.carbsDuration = carbsDuration
        n.eCarbs = eCarbs

        n.pumpType = pumpType
        n.pumpSerial = pumpSerial
        n.bolusPumpId = bolusPumpId
        // Was missing: createBolus() reads `bolusTimestamp ?: timestamp`, so a copy that dropped it
        // would record the bolus at the wrong time. Nothing sets the field today, which is why the
        // omission stayed invisible. DetailedBolusInfoCopyTest names every field so the next one
        // added is not lost the same way. `id` is left out on purpose - see that test.
        n.bolusTimestamp = bolusTimestamp
        n.carbsTimestamp = carbsTimestamp
        return n
    }
}