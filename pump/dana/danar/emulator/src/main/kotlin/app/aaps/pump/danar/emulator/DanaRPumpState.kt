package app.aaps.pump.danar.emulator

import app.aaps.pump.dana.emulator.HistoryEventStore
import java.io.Serializable

enum class DanaRVariant(val hwModel: Int, val protocol: Int) {
    DANA_R(hwModel = 0x03, protocol = 0x00),
    DANA_R_KOREAN(hwModel = 0x01, protocol = 0x00),
    DANA_R_V2(hwModel = 0x03, protocol = 0x02)
}

/**
 * Mutable state of the emulated DanaR pump.
 * Inspectable from tests for assertions. Serializable: a test that kills the app saves it and starts a new process
 * with the pump as it was.
 */
class DanaRPumpState(val variant: DanaRVariant = DanaRVariant.DANA_R_V2) : Serializable {

    /** Every bolus the pump gave, oldest first, with the amount it really gave. */
    val givenBoluses: MutableList<Double> = mutableListOf()

    /** The bolus the pump gives now, or null. Saved with the rest, so a pump read back after an app kill goes on with it. */
    @Volatile var runningBolus: RunningBolus? = null

    // History
    val historyStore = HistoryEventStore()

    // Review history (the per-type records behind the Pump-history screen), kept separate from the APS
    // event historyStore because they use a different wire format and consumer - see HistoryEventStore.
    val reviewHistoryStore = HistoryEventStore()

    // Diagnostic: how many bolus (0x3101) review-history requests the emulator has served. Lets a test
    // tell "the read never reached the emulator" (0) from "it did but the record didn't parse" (>0).
    var bolusHistoryRequestCount = 0

    // Device info
    var serialNumber: String = "DAN12345AB"
    var shippingCountry: String = "INT"
    var shippingDate: Triple<Int, Int, Int> = Triple(2024, 1, 1) // year, month, day
    val hwModel: Int get() = variant.hwModel
    val protocol: Int get() = variant.protocol
    var productCode: Int = 0

    // Password (numeric, XOR 0x3463 when sent)
    var password: Int = 1234

    // Basal
    var activeProfile: Int = 0
    var basalProfiles: Array<DoubleArray> = Array(4) { DoubleArray(24) { 1.0 } }
    var maxBasal: Double = 3.0
    var basalStep: Double = 0.01

    // Temp basal
    var isTempBasalRunning: Boolean = false
    var tempBasalPercent: Int = 0
    var tempBasalDurationMinutes: Int = 0
    var tempBasalStartTime: Long = 0

    // Bolus
    var maxBolus: Double = 10.0
    var bolusStep: Double = 0.1
    var lastBolusAmount: Double = 0.0
    var lastBolusTime: Long = 0
    var isExtendedBolusRunning: Boolean = false
    var extendedBolusAmount: Double = 0.0
    var extendedBolusDurationHalfHours: Int = 0
    /** By the pump clock. The status gives how long the extended bolus runs, and the driver takes its start from that. */
    var extendedBolusStartTime: Long = 0
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

    /** The pump clock now. Setting it sets the pump clock, as the time command does. */
    var pumpTimeMillis: Long
        get() = System.currentTimeMillis() + clockOffsetMs
        set(value) {
            clockOffsetMs = value - System.currentTimeMillis()
        }

    // Status
    var isSuspended: Boolean = false
    var isDualBolusRunning: Boolean = false
    var calculatorEnabled: Boolean = true
    var bolusBlocked: Boolean = false

    // User options
    var timeDisplayType24: Boolean = true
    var buttonScroll: Boolean = false
    var beepAndAlarm: Int = 1  // 1=Sound
    var lcdOnTimeSec: Int = 15
    var backlightOnTimeSec: Int = 5
    var glucoseUnit: Int = 0  // 0=mg/dL, 1=mmol/L
    var shutdownHour: Int = 0
    var lowReservoirRate: Int = 20
}

/** A bolus the pump gives: [hundredths] asked, [given] hundredths given until now. */
class RunningBolus(val hundredths: Int) : Serializable {

    @Volatile var given = 0
    @Volatile var stopped = false
}
