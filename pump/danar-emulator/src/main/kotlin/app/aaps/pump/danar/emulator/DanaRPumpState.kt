package app.aaps.pump.danar.emulator

import app.aaps.pump.dana.emulator.HistoryEventStore

enum class DanaRVariant(val hwModel: Int, val protocol: Int) {
    DANA_R(hwModel = 0x03, protocol = 0x00),
    DANA_R_KOREAN(hwModel = 0x01, protocol = 0x00),
    DANA_R_V2(hwModel = 0x03, protocol = 0x02)
}

/**
 * Mutable state of the emulated DanaR pump.
 * Inspectable from tests for assertions.
 */
class DanaRPumpState(val variant: DanaRVariant = DanaRVariant.DANA_R_V2) {

    // History
    val historyStore = HistoryEventStore()

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

    // Time (pump internal clock) — returns real time by default
    var pumpTimeMillis: Long
        get() = System.currentTimeMillis()
        set(_) {} // SetTime command is a no-op — emulator always tracks real time

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
