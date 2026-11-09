package app.aaps.pump.danars.emulator

import app.aaps.pump.dana.emulator.HistoryEventStore
import java.io.Serializable

/**
 * Mutable state of the emulated Dana RS pump.
 * Inspectable from tests for assertions. Saved whole for a test that kills the app, see `PumpEmulator.saveState`.
 */
class PumpState : Serializable {

    // History (shared with DanaR emulator)
    val historyStore = HistoryEventStore()

    /**
     * Records served by the per-type `REVIEW__*` commands behind the pump history screen.
     *
     * Separate from [historyStore], which serves the APS event history: different wire format
     * (see `HistoryEventStore.buildReviewRecordData`) and a different consumer, so a record added
     * for one must not surface in the other.
     */
    val reviewHistoryStore = HistoryEventStore()

    // Device info
    var serialNumber: String = "AAA00000AA"
    var shippingCountry: String = "INT"
    var shippingDate: Triple<Int, Int, Int> = Triple(2024, 1, 1) // year, month, day
    var hwModel: Int = 0x05  // Dana RS
    var protocol: Int = 5
    var productCode: Int = 0

    // Basal
    var activeProfileNumber: Int = 0
    var basalProfiles: Array<DoubleArray> = Array(4) { DoubleArray(24) { 1.0 } } // 4 profiles, 24 hours
    var maxBasal: Double = 3.0
    var basalStep: Double = 0.01

    // Temp basal
    /** Test fault: refuse every "set temp basal" command and keep the TBR state as it is. */
    var rejectTempBasal: Boolean = false
    var isTempBasalRunning: Boolean = false
    var tempBasalPercent: Int = 0
    var tempBasalDurationMinutes: Int = 0
    var tempBasalStartTime: Long = 0

    // Bolus
    var maxBolus: Double = 10.0
    var bolusStep: Double = 0.05
    /** 0 gives a whole bolus at once, for unit tests. Any other value gives it step by step at the speed of the command. */
    var bolusDeliveryIntervalMs: Long = 1000
    var lastBolusAmount: Double = 0.0
    var lastBolusTime: Long = 0

    /** The bolus the pump gives now, or null. */
    var runningBolus: RunningBolus? = null

    /** Every bolus the pump gave, oldest first, each with the amount it really gave. */
    val givenBoluses: MutableList<Double> = ArrayList()
    var isExtendedBolusRunning: Boolean = false
    var extendedBolusAmount: Double = 0.0
    var extendedBolusDurationHalfHours: Int = 0
    var isExtendedEnabled: Boolean = true

    // Reservoir & battery
    var reservoirRemainingUnits: Double = 150.0
    var batteryRemaining: Int = 80

    // Daily totals
    var dailyTotalUnits: Double = 5.0
    var maxDailyTotalUnits: Double = 25.0

    // Current basal rate
    var currentBasal: Double = 1.0

    // IOB
    var iob: Double = 0.0

    /** How far the pump clock is off the phone clock. A real pump clock runs on, so only this is kept. */
    var clockOffsetMs: Long = 0

    /** The pump clock now. Setting it sets the pump clock, as the time commands do. */
    var pumpTimeMillis: Long
        get() = System.currentTimeMillis() + clockOffsetMs
        set(value) {
            clockOffsetMs = value - System.currentTimeMillis()
        }
    var usingUTC: Boolean = false
    var zoneOffset: Int = 0

    // Status
    var isSuspended: Boolean = false
    var isDualBolusRunning: Boolean = false
    var errorState: Int = 0

    // User options
    var timeDisplayType: Int = 0  // 0 = 24h
    var buttonScroll: Int = 0
    var beepAndAlarm: Int = 0
    var lcdOnTimeSec: Int = 5
    var backlightOnTimeSec: Int = 5
    var selectedLanguage: Int = 0
    var units: Int = 0  // 0 = mg/dL, 1 = mmol/L
    var shutdownHour: Int = 0
    var lowReservoirRate: Int = 20
    var cannulaVolume: Int = 15
    var refillAmount: Int = 0
    var targetBG: Int = 100

    // CIR/CF (simple mode: 7 time blocks)
    var language: Int = 0
    var cirValues: IntArray = IntArray(7) { 10 }  // carb/insulin ratios
    var cfValues: IntArray = IntArray(7) { 30 }   // correction factors

    // 24-hour CIR/CF
    var profile24: Boolean = true
    var cir24Values: IntArray = IntArray(24) { 10 }
    var cf24Values: IntArray = IntArray(24) { 30 }

    // Bolus calculation info
    var currentBG: Int = 0
    var currentCarb: Int = 0
    var currentTarget: Int = 100
    var currentCIR: Int = 10
    var currentCF: Int = 30

    var historyDone: Boolean = true

    // Password (v1 encryption)
    var pumpPassword: String = "0000"

    // Pairing key for v1
    var pairingKey: ByteArray = byteArrayOf(0xAB.toByte(), 0xCD.toByte())

    // RSv3 pairing keys
    var v3PairingKey: ByteArray = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
    var v3RandomPairingKey: ByteArray = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
    var v3RandomSyncKey: Byte = 0x42

    // BLE5 pairing key (6 ASCII digits, e.g. "474632")
    var ble5PairingKey: String = "474632"

}

/**
 * A bolus the pump gives now. Seen on a real Dana-i (2026-11-09): 0.05 U steps, about 0.6 s apart at 12 s/U, with a
 * progress message after each step.
 *
 * @param startedAt phone time of the start
 * @param amountHundredths what the command asked for, in 0.01 U
 * @param msPerStep time for one step, 0 for a whole bolus at once
 * @param link the link the start came on: the progress goes only there
 */
class RunningBolus(val startedAt: Long, val amountHundredths: Int, val msPerStep: Long, var link: Int) : Serializable {

    /** Steps given and reported so far. */
    var stepsGiven = 0

    /** Set by a stop: the pump ends after this many steps. */
    var stopAfterSteps: Int? = null

    val totalSteps: Int get() = (amountHundredths + STEP_HUNDREDTHS - 1) / STEP_HUNDREDTHS

    /** The steps the pump has given by [now], up to its end. */
    fun stepsDue(now: Long): Int {
        val end = stopAfterSteps ?: totalSteps
        if (msPerStep <= 0) return end
        return minOf(end, ((now - startedAt) / msPerStep).toInt())
    }

    fun givenHundredths(steps: Int): Int = minOf(amountHundredths, steps * STEP_HUNDREDTHS)

    fun ended(steps: Int): Boolean = steps >= (stopAfterSteps ?: totalSteps)

    companion object {

        /** 0.05 U. */
        const val STEP_HUNDREDTHS = 5
    }
}
