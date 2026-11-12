package app.aaps.pump.equil.emulator

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import java.io.Serializable
import kotlin.time.Instant

/**
 * Mutable state of the emulated Equil pump.
 * Inspectable from tests for assertions. Serializable, so a test that kills the app can keep the pump as it was.
 */
class EquilPumpState : Serializable {

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

    /** The bolus the pump gives now, or null. */
    var runningBolus: RunningBolus? = null

    /** Every bolus the pump gave, oldest first, in units. A stopped one with what it gave until the stop. */
    val deliveredBoluses: MutableList<Double> = mutableListOf()

    /** How many commands reached the pump. */
    var commands: Int = 0

    /** The reservoir alarm of CmdSettingSet, in steps of 0.00625 U. The driver sends 1600, that is 10 U. */
    var lowAlarmStep: Int = 1600

    /** When the pump was last brought up to the present: the basal of the time since then is used from the reservoir. */
    var lastSettleAt: Long = 0

    /** The pump clock minus the phone clock. The pump logs its own time. A time set from the phone makes it 0 again. */
    var clockOffsetMs: Long = 0

    /** Running time not yet counted as used battery. */
    var batteryUseMs: Long = 0

    /** Insulin left, in units. A bolus takes it step by step. */
    var reservoir: Double = 150.0

    // Reservoir & battery
    /** Units remaining, as CmdInsulinGet reports them: whole units. */
    var currentInsulin: Int
        get() = reservoir.toInt()
        set(value) {
            reservoir = value.toDouble()
        }
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

    private companion object {

        private const val serialVersionUID = 1L
    }
}

/**
 * A bolus as the pump gives it: [steps] (160 per unit) spread over [durationMs], the time the command asked for.
 * UNVERIFIED: that the steps are given evenly over that time. The driver shows its progress the same way.
 */
class RunningBolus(val startedAt: Long, val steps: Int, val durationMs: Long) : Serializable {

    /** Steps given so far. */
    var givenSteps: Int = 0

    private companion object {

        private const val serialVersionUID = 1L
    }
}

/**
 * One entry of the pump log, in the layout CmdHistoryGet reads. [rate] and [largeRate] are the basal and the bolus
 * speed in steps of 0.00625 U/h. [port], [type] and [level] name the event, see `PumpEvent` in the driver.
 */
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
    val parm: Int,
    val port: Int = PORT_STATUS
) : Serializable {

    companion object {

        /** Port 4: status, delivery and reservoir events. Port 5: battery and power events. */
        const val PORT_STATUS = 4
        const val PORT_POWER = 5

        fun now(
            index: Int, type: Int, rate: Int = 0, level: Int = 0, parm: Int = 0, battery: Int = 80, insulin: Int = 150,
            port: Int = PORT_STATUS, largeRate: Int = 0
        ): HistoryEvent = at(System.currentTimeMillis(), index, type, rate, level, parm, battery, insulin, port, largeRate)

        /** An entry with the pump time [timeMillis]. The pump time has whole seconds. */
        fun at(
            timeMillis: Long, index: Int, type: Int, rate: Int = 0, level: Int = 0, parm: Int = 0, battery: Int = 80, insulin: Int = 150,
            port: Int = PORT_STATUS, largeRate: Int = 0
        ): HistoryEvent {
            val ldt = Instant.fromEpochMilliseconds(timeMillis).toLocalDateTime(TimeZone.currentSystemDefault())
            return HistoryEvent(
                year = ldt.year - 2000,
                month = ldt.month.number,
                day = ldt.day,
                hour = ldt.hour,
                minute = ldt.minute,
                second = ldt.second,
                battery = battery,
                insulin = insulin,
                rate = rate,
                largeRate = largeRate,
                index = index,
                type = type,
                level = level,
                parm = parm,
                port = port
            )
        }

        private const val serialVersionUID = 1L
    }
}
