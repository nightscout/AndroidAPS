package app.aaps.core.interfaces.pump

import android.content.Context
import app.aaps.core.interfaces.pump.defs.PumpType
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.embedments.InterfaceIDs

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
    var bolusCalculatorResult: BolusCalculatorResult? = null
    var eventType = EventType.MEAL_BOLUS
    var notes: String? = null
    var mgdlGlucose: Double? = null // Bg value in mgdl
    var glucoseType: MeterType? = null // NS values: Manual, Finger, Sensor
    var bolusType = BolusType.NORMAL
    var carbsDuration = 0L // in milliseconds

    // Collected info from driver
    var pumpType: PumpType? = null // if == USER
    var pumpSerial: String? = null
    var bolusPumpId: Long? = null
    var bolusTimestamp: Long? = null
    var carbsPumpId: Long? = null
    var carbsTimestamp: Long? = null

    enum class MeterType(val text: String) {
        FINGER("Finger"),
        SENSOR("Sensor"),
        MANUAL("Manual");

        fun toDbMeterType(): TherapyEvent.MeterType =
            when (this) {
                FINGER -> TherapyEvent.MeterType.FINGER
                SENSOR -> TherapyEvent.MeterType.SENSOR
                MANUAL -> TherapyEvent.MeterType.MANUAL
            }
    }

    enum class BolusType {
        NORMAL,
        SMB,
        PRIMING;

        fun toDBbBolusType(): Bolus.Type =
            when (this) {
                NORMAL  -> Bolus.Type.NORMAL
                SMB     -> Bolus.Type.SMB
                PRIMING -> Bolus.Type.PRIMING
            }
    }

    enum class EventType {
        MEAL_BOLUS,
        BOLUS_WIZARD,
        CORRECTION_BOLUS,
        CARBS_CORRECTION,
        CANNULA_CHANGE,
        INSULIN_CHANGE,
        PUMP_BATTERY_CHANGE,
        NOTE;

        fun toDBbEventType(): TherapyEvent.Type =
            when (this) {
                MEAL_BOLUS          -> TherapyEvent.Type.MEAL_BOLUS
                BOLUS_WIZARD        -> TherapyEvent.Type.BOLUS_WIZARD
                CORRECTION_BOLUS    -> TherapyEvent.Type.CORRECTION_BOLUS
                CARBS_CORRECTION    -> TherapyEvent.Type.CARBS_CORRECTION
                CANNULA_CHANGE      -> TherapyEvent.Type.CANNULA_CHANGE
                INSULIN_CHANGE      -> TherapyEvent.Type.INSULIN_CHANGE
                PUMP_BATTERY_CHANGE -> TherapyEvent.Type.PUMP_BATTERY_CHANGE
                NOTE                -> TherapyEvent.Type.NOTE
            }
    }

    fun createTherapyEvent(): TherapyEvent =
        TherapyEvent(
            timestamp = timestamp,
            type = eventType.toDBbEventType(),
            glucoseUnit = TherapyEvent.GlucoseUnit.MGDL,
            note = notes,
            glucose = mgdlGlucose,
            glucoseType = glucoseType?.toDbMeterType()
        )

    /**
     * Used for create record going directly to db (record only)
     */
    fun createBolus(): Bolus =
        if (insulin != 0.0)
            Bolus(
                timestamp = bolusTimestamp ?: timestamp,
                amount = insulin,
                type = bolusType.toDBbBolusType(),
                notes = notes,
                interfaceIDs_backing = InterfaceIDs(pumpId = timestamp)
            )
        else throw IllegalStateException("insulin == 0.0")

    fun createCarbs(): Carbs =
        if (carbs != 0.0)
            Carbs(
                timestamp = carbsTimestamp ?: timestamp,
                amount = carbs,
                duration = carbsDuration,
                notes = notes
            )
        else throw IllegalStateException("carbs == 0.0")

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
        n.carbsPumpId = carbsPumpId
        n.carbsTimestamp = carbsTimestamp
        return n
    }
}