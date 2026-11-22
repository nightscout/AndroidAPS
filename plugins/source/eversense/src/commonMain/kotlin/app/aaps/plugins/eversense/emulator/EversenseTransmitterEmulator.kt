package app.aaps.plugins.eversense.emulator

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * An emulated Eversense transmitter on its sensor: its clock, its readings, battery, calibration and alarms. The radio
 * side is [EversenseE3Peripheral]. It lets the driver run end to end with no transmitter, in tests and with the
 * `emulate_eversense` option file.
 *
 * Built from the driver's code and the protocol notes, not from captured frames: UNVERIFIED until real frames are
 * recorded. What a real transmitter does that this one does not: history logs (an E3 has no backfill in the driver),
 * the readiness change after a calibration, alarms it raises by itself.
 *
 * The readings are a slow wave around 120 mg/dL, one every [readingInterval] on the transmitter's clock, so each new
 * reading differs a little from the one before. The phone sets the clock at each full sync, as on a real transmitter.
 */
class EversenseTransmitterEmulator(
    /** The name it advertises, "T" and the serial number for an E3 */
    val name: String = "T1234567",
    /** Its Bluetooth address; on Android a MAC address */
    val address: String = "00:EE:00:12:34:56",
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    val readingInterval: Duration = 5.minutes,
    /** Its serial number: the digits of the name */
    val serial: String = name.filter { it.isDigit() }
) {

    /** The serial number as a 365 reports it to the Eversense server: 32 bytes, padded with zeros */
    fun serialBytes(): ByteArray = serial.encodeToByteArray().copyOf(32)

    private val lock = AapsLock()

    /** What the transmitter's clock is ahead of the phone's; the phone sets it at a full sync */
    private var clockOffsetMs = 0L

    /** The phone's zone offset at the last time it set the clock, which an E3 keeps and reports */
    private var zoneOffsetMs = 0L

    /** False: out of range, it neither advertises nor answers a connect */
    var inRange: Boolean = true

    var batteryPercent: Int = 85

    /** The raw placement signal of an E3; from 903 up the driver calls it excellent */
    var signalRaw: Int = 950

    val insertedAt: Long = now() - 10.days.inWholeMilliseconds
    var lastCalibrationAt: Long = now() - 6.hours.inWholeMilliseconds
        private set

    /** The finger-stick values the phone sent, in mg/dL, oldest first */
    val calibrations: List<Int> get() = lock.withLock { receivedCalibrations.toList() }
    private val receivedCalibrations = mutableListOf<Int>()

    var diagnosticMode: Boolean = false

    /** The transmitter's clock now */
    fun clock(): Long = lock.withLock { now() + clockOffsetMs }

    fun zoneOffsetMillis(): Long = lock.withLock { zoneOffsetMs }

    /** The phone sets the clock: the transmitter keeps what it was told, to its 2 s resolution */
    fun setClock(transmitterTime: Long, zoneOffset: Long) = lock.withLock {
        clockOffsetMs = transmitterTime - now()
        zoneOffsetMs = zoneOffset
    }

    /** The newest reading: its time on the transmitter's clock and its value */
    fun latestReading(): Reading {
        val interval = readingInterval.inWholeMilliseconds
        val time = clock() / interval * interval
        return Reading(time, glucoseAt(time))
    }

    fun calibrate(glucoseMgDl: Int) = lock.withLock {
        receivedCalibrations += glucoseMgDl
        lastCalibrationAt = now()
    }

    data class Reading(val timestamp: Long, val glucoseMgDl: Int)

    /** The glucose at [time] on the transmitter's clock, also for its history log */
    fun glucoseAt(time: Long): Int = (120 + 40 * sin(2 * PI * time / WAVE_PERIOD_MS)).roundToInt()

    companion object {

        /** One emulated transmitter for the app's life: an app restart in a test finds the same one */
        val KEY = PumpEmulators.Key<EversenseTransmitterEmulator>("eversense")

        /** The glucose wave: up and down in four hours */
        private val WAVE_PERIOD_MS = 4.hours.inWholeMilliseconds.toDouble()
    }
}
