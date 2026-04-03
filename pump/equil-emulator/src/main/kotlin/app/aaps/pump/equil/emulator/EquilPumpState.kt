package app.aaps.pump.equil.emulator

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Mutable state of the emulated Equil pump.
 * Inspectable from tests for assertions.
 */
class EquilPumpState {

    // Device info — serial must start with 'A' (real Equil format: Axxxxx)
    var serialNumber: String = "A00001"
    var firmwareVersion: Float = 5.5f

    // Basal profile — 24 raw rate values (2 bytes each, as stored by CmdBasalSet)
    var basalRates: IntArray = IntArray(24) { 160 } // ~1.0 U/h

    // Temp basal
    var isTempBasalRunning: Boolean = false
    var tempBasalStep: Int = 0    // raw step value
    var tempBasalDuration: Int = 0 // seconds
    var tempBasalStartTime: Long = 0

    // Bolus (CmdLargeBasalSet)
    var lastBolusStep: Int = 0
    var lastBolusStepTime: Int = 0
    var lastBolusTime: Long = 0

    // Extended bolus (CmdExtendedBolusSet)
    var isExtendedBolusRunning: Boolean = false
    var extendedBolusStep: Int = 0
    var extendedBolusDuration: Int = 0 // seconds

    // Reservoir & battery
    var currentInsulin: Int = 150 // units remaining (raw byte value from CmdInsulinGet)
    var battery: Int = 80

    // Motor resistance (CmdResistanceGet) — >= 500 means piston reached insulin
    var resistance: Int = 600

    // Running mode (0=stopped, 1=running, 2=suspended)
    var runningMode: Int = 1

    // Alarm mode
    var alarmMode: Int = 0

    // Pump time
    var pumpTimeMillis: Long = Instant.fromEpochMilliseconds(System.currentTimeMillis()).toEpochMilliseconds()

    // Safety settings (CmdSettingSet)
    var bolusThresholdStep: Int = 0
    var basalThresholdStep: Int = 0

    // History
    var historyEvents: MutableList<HistoryEvent> = mutableListOf()
    var historyIndex: Int = 0

    // Authentication
    /** Password derived from device serial, used for initial request decryption */
    var devicePassword: ByteArray = ByteArray(32)

    /** Session password returned to app in first response, used for subsequent messages */
    var sessionPassword: ByteArray = ByteArray(32) { (it % 256).toByte() }

    /** 2-byte session code (hex string) */
    var sessionCode: String = "A1B2"

    /** Pairing: device key (32 hex bytes = 64 hex chars) returned to app during CmdPair */
    var pairingDeviceKey: String = "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"

    /** Pairing: password key (32 hex bytes = 64 hex chars) returned to app during CmdPair */
    var pairingPasswordKey: String = "FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210"
}

data class HistoryEvent(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
    val battery: Int,
    val insulin: Int,
    val rate: Int,
    val largeRate: Int,
    val index: Int,
    val type: Int,
    val level: Int,
    val parm: Int
) {

    companion object {

        fun now(index: Int, type: Int, rate: Int = 0, level: Int = 0, parm: Int = 0, battery: Int = 80, insulin: Int = 150): HistoryEvent {
            val ldt = Instant.fromEpochMilliseconds(System.currentTimeMillis()).toLocalDateTime(TimeZone.currentSystemDefault())
            return HistoryEvent(
                year = ldt.year - 2000,
                month = ldt.monthNumber,
                day = ldt.dayOfMonth,
                hour = ldt.hour,
                minute = ldt.minute,
                second = ldt.second,
                battery = battery,
                insulin = insulin,
                rate = rate,
                largeRate = 0,
                index = index,
                type = type,
                level = level,
                parm = parm
            )
        }
    }
}
