package app.aaps.pump.danars.emulator

/**
 * Mutable state of the emulated Dana RS pump.
 * Inspectable from tests for assertions.
 */
class PumpState {

    // Device info
    var serialNumber: String = "AAA00000AA"
    var shippingCountry: String = "INT"
    var shippingDate: Triple<Int, Int, Int> = Triple(2024, 1, 1) // year, month, day
    var hwModel: Int = 0x03  // Default v1 encryption; set to 0x05/0x06 for RSv3, 0x09/0x0A for BLE5
    var protocol: Int = 5
    var productCode: Int = 0

    // Basal
    var activeProfileNumber: Int = 0
    var basalProfiles: Array<DoubleArray> = Array(4) { DoubleArray(24) { 1.0 } } // 4 profiles, 24 hours
    var maxBasal: Double = 3.0
    var basalStep: Double = 0.01

    // Temp basal
    var isTempBasalRunning: Boolean = false
    var tempBasalPercent: Int = 0
    var tempBasalDurationMinutes: Int = 0
    var tempBasalStartTime: Long = 0

    // Bolus
    var maxBolus: Double = 10.0
    var bolusStep: Double = 0.05
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

    // Time (pump internal clock)
    var pumpTimeMillis: Long = System.currentTimeMillis()
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

    // History
    var historyEvents: MutableList<HistoryEvent> = mutableListOf()
    var historyDone: Boolean = true

    // Error simulation
    var pumpCheckResponse: PumpCheckResponse = PumpCheckResponse.OK

    // Password (v1 encryption)
    var pumpPassword: String = "0000"

    // Pairing key for v1
    var pairingKey: ByteArray = byteArrayOf(0xAB.toByte(), 0xCD.toByte())

    // RSv3 encryption (hwModel 0x05/0x06)
    var rsv3PairingKey: ByteArray = ByteArray(6) { (it + 1).toByte() }
    var rsv3RandomPairingKey: ByteArray = ByteArray(6) { (it + 0x10).toByte() }
    var rsv3RandomSyncKey: Byte = 0x42

    // BLE5 encryption (hwModel 0x09/0x0A)
    var ble5PairingKey: String = "123456" // 6-char ASCII key
}

enum class PumpCheckResponse {
    OK,
    PUMP_ERROR,
    BUSY
}

data class HistoryEvent(
    val code: Int,
    val timestamp: Long,
    val param1: Int = 0,
    val param2: Int = 0
)
