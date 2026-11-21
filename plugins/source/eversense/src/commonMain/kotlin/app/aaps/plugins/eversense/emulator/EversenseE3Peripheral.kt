package app.aaps.plugins.eversense.emulator

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.Advertisement
import app.aaps.core.interfaces.pump.ble.BleFailure
import app.aaps.core.interfaces.pump.ble.BleResult
import app.aaps.core.interfaces.pump.ble.CharacteristicId
import app.aaps.core.interfaces.pump.ble.CharacteristicProperty
import app.aaps.core.interfaces.pump.ble.DeviceId
import app.aaps.core.interfaces.pump.ble.EmulatedPeripheral
import app.aaps.core.interfaces.pump.ble.GattServices
import app.aaps.core.interfaces.pump.ble.PeripheralLink
import app.aaps.core.interfaces.pump.ble.WriteType
import app.aaps.plugins.eversense.connection.EversenseSession
import app.aaps.plugins.eversense.enums.EversenseE3Memory
import app.aaps.plugins.eversense.packets.e3.EversenseE3Packets
import app.aaps.plugins.eversense.protocol.BinaryFields
import app.aaps.plugins.eversense.protocol.WireEncoding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The radio side of an emulated Eversense E3: it advertises the Eversense service, takes the driver's frames on the E3
 * request characteristic and answers on the response characteristic, with the CRC of the real protocol. Its frames
 * are built with the driver's own encoders ([WireEncoding], [BinaryFields]); the layouts are those the driver parses.
 *
 * After the sign-in (the bonding information) it pushes a keep-alive soon and then every [keepAliveInterval], as a
 * real E3 does about every five minutes: each push makes the driver read the glucose. An E3 needs no Eversense
 * account, so the driver is used without signing in.
 *
 * UNVERIFIED: built from the driver's code, not from captured frames (see [EversenseTransmitterEmulator]).
 */
class EversenseE3Peripheral(
    private val transmitter: EversenseTransmitterEmulator,
    private val aapsLogger: AAPSLogger? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /** How often the transmitter pushes a keep-alive; a test makes it short */
    var keepAliveInterval: Duration = 5.minutes,
    /** The first push after the sign-in, so a reading comes soon after a connect */
    var firstPushDelay: Duration = 2.seconds
) : EmulatedPeripheral {

    override val device = DeviceId(transmitter.address)

    override val services = GattServices(
        mapOf(
            EversenseSession.E3_REQUEST to setOf(CharacteristicProperty.WRITE, CharacteristicProperty.WRITE_WITHOUT_RESPONSE),
            EversenseSession.E3_RESPONSE to setOf(CharacteristicProperty.NOTIFY)
        )
    )

    private var pushes: Job? = null

    /** Each command the driver sent, by its id, in order: for a test that checks what was asked */
    val commands: List<Byte> get() = commandsLock.withLock { sent.toList() }
    private val sent = mutableListOf<Byte>()
    private val commandsLock = AapsLock()

    override fun advertisement(): Advertisement? =
        if (!transmitter.inRange) null
        else Advertisement(device, transmitter.name, rssi = -60, serviceUuids = listOf(EversenseSession.SERVICE), manufacturerData = emptyMap(), serviceData = emptyMap())

    override suspend fun onConnect(link: PeripheralLink): Boolean = transmitter.inRange

    override suspend fun onWrite(link: PeripheralLink, characteristic: CharacteristicId, data: ByteArray, type: WriteType): BleResult<Unit>? {
        if (characteristic != EversenseSession.E3_REQUEST || data.size < 3)
            return BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED, message = "not an E3 frame"))
        val message = data.copyOfRange(0, data.size - 2)
        val command = message[0]
        commandsLock.withLock { sent += command }
        val answer =
            if (!WireEncoding.crc16(message).contentEquals(data.copyOfRange(data.size - 2, data.size))) error(command, ERROR_CRC)
            else answer(link, command, message.copyOfRange(1, message.size))
        // After the write is done, as a real transmitter answers
        scope.launch { link.send(EversenseSession.E3_RESPONSE, answer) }
        return BleResult.Ok(Unit)
    }

    override fun onDisconnect(link: PeripheralLink) {
        pushes?.cancel()
        pushes = null
    }

    private fun answer(link: PeripheralLink, command: Byte, payload: ByteArray): ByteArray = when (command) {
        EversenseE3Packets.SaveBLEBondingInformationCommandId                -> {
            startPushes(link)
            frame(EversenseE3Packets.SaveBLEBondingInformationResponseId)
        }

        EversenseE3Packets.PingCommandId                                     -> frame(EversenseE3Packets.PingResponseId)
        EversenseE3Packets.ReadCurrentTransmitterDateAndTimeCommandId        -> {
            val clock = transmitter.clock()
            frame(EversenseE3Packets.ReadCurrentTransmitterDateAndTimeResponseId, BinaryFields.date(clock), BinaryFields.time(clock), zone(transmitter.zoneOffsetMillis()))
        }

        EversenseE3Packets.SetCurrentTransmitterDateAndTimeCommandId         -> {
            val bytes = payload.toUByteArray()
            transmitter.setClock(BinaryFields.dateMillis(bytes, 0) + BinaryFields.timeMillis(bytes, 2), BinaryFields.offsetMillis(bytes, 4))
            frame(EversenseE3Packets.SetCurrentTransmitterDateAndTimeResponseId)
        }

        EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId        -> register(command, payload, EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId, ::singleByte)
        EversenseE3Packets.ReadTwoByteSerialFlashRegisterCommandId           -> register(command, payload, EversenseE3Packets.ReadTwoByteSerialFlashRegisterResponseId, ::twoBytes)
        EversenseE3Packets.ReadFourByteSerialFlashRegisterCommandId          -> register(command, payload, EversenseE3Packets.ReadFourByteSerialFlashRegisterResponseId, ::fourBytes)
        // The app version and the disconnect timeout: taken, nothing to keep
        EversenseE3Packets.WriteFourByteSerialFlashRegisterCommandId         -> frame(EversenseE3Packets.WriteFourByteSerialFlashRegisterResponseId)
        EversenseE3Packets.WriteTwoByteSerialFlashRegisterCommandId          -> frame(EversenseE3Packets.WriteTwoByteSerialFlashRegisterResponseId)
        EversenseE3Packets.ReadSensorGlucoseCommandId                        -> glucose()
        EversenseE3Packets.SendBloodGlucoseDataWithTwoTimestampsCommandId    -> {
            // date(2) time(2) of the sample, date(2) time(2) of now, then the value
            transmitter.calibrate(BinaryFields.unsigned(payload.toUByteArray(), 8, 2).toInt())
            frame(EversenseE3Packets.SendBloodGlucoseDataWithTwoTimestampsResponseId)
        }

        EversenseE3Packets.EnterDiagnosticModeCommandId                      -> {
            transmitter.diagnosticMode = true
            frame(EversenseE3Packets.EnterDiagnosticModeResponseId)
        }

        EversenseE3Packets.ExitDiagnosticModeCommandId                       -> {
            transmitter.diagnosticMode = false
            frame(EversenseE3Packets.ExitDiagnosticModeResponseId)
        }

        else                                                                 -> {
            aapsLogger?.debug(LTag.BGSOURCE) { "Emulated E3: command ${command.toInt() and 0xFF} not emulated" }
            error(command, ERROR_UNKNOWN_COMMAND)
        }
    }

    /** The newest reading: frame [4..5] date, [6..7] time, [9..10] glucose, [13] trend (flat) */
    private fun glucose(): ByteArray {
        val reading = transmitter.latestReading()
        val body = ByteArray(13)
        BinaryFields.date(reading.timestamp).copyInto(body, 3)
        BinaryFields.time(reading.timestamp).copyInto(body, 5)
        BinaryFields.littleEndian(reading.glucoseMgDl.toLong(), 2).copyInto(body, 8)
        body[12] = TREND_FLAT
        return frame(EversenseE3Packets.ReadSensorGlucoseResponseId, body)
    }

    /** A register read: the answer repeats the address and has the value from index 4 */
    private fun register(command: Byte, payload: ByteArray, response: Byte, value: (Int) -> ByteArray?): ByteArray {
        if (payload.size < 3) return error(command, ERROR_UNKNOWN_COMMAND)
        val address = BinaryFields.unsigned(payload.toUByteArray(), 0, 2).toInt()
        val bytes = value(address) ?: return error(command, ERROR_UNKNOWN_REGISTER)
        return frame(response, payload.copyOfRange(0, 3), bytes)
    }

    private fun singleByte(address: Int): ByteArray? = when (address) {
        address(EversenseE3Memory.BatteryPercentage)    -> byteArrayOf(batteryIndex(transmitter.batteryPercent).toByte())
        address(EversenseE3Memory.CalibrationReadiness) -> byteArrayOf(READINESS_READY)
        address(EversenseE3Memory.IsOneCalibration)     -> byteArrayOf(ONE_CALIBRATION_A_DAY)
        address(EversenseE3Memory.CalibrationPhase)     -> byteArrayOf(PHASE_DAILY)
        address(EversenseE3Memory.MmaFeatures)          -> byteArrayOf(0)
        else                                            -> null
    }

    private fun twoBytes(address: Int): ByteArray? = when (address) {
        address(EversenseE3Memory.SensorInsertionDate)    -> BinaryFields.date(transmitter.insertedAt)
        address(EversenseE3Memory.SensorInsertionTime)    -> BinaryFields.time(transmitter.insertedAt)
        address(EversenseE3Memory.LastCalibrationDate)    -> BinaryFields.date(transmitter.lastCalibrationAt)
        address(EversenseE3Memory.LastCalibrationTime)    -> BinaryFields.time(transmitter.lastCalibrationAt)
        address(EversenseE3Memory.SensorFieldCurrentRaw) -> BinaryFields.littleEndian(transmitter.signalRaw.toLong(), 2)
        else                                              -> null
    }

    private fun fourBytes(address: Int): ByteArray? = when (address) {
        address(EversenseE3Memory.TransmitterSoftwareVersion)    -> VERSION.encodeToByteArray()
        address(EversenseE3Memory.TransmitterSoftwareVersionExt) -> VERSION_EXTENDED.encodeToByteArray()
        else                                                     -> null
    }

    private fun address(register: EversenseE3Memory): Int = BinaryFields.unsigned(register.getRequestData().toUByteArray(), 0, 2).toInt()

    /** The battery as the E3 reports it: an index into fixed steps (0, 5, 10, 25 ... 95, 100 %) */
    private fun batteryIndex(percent: Int): Int = BATTERY_STEPS.indices.minBy { abs(BATTERY_STEPS[it] - percent) }

    /** A zone offset as the E3 keeps it: hours and minutes of its size, then 0 for ahead of UTC, 0xFF for behind */
    private fun zone(offsetMs: Long): ByteArray {
        val minutes = (abs(offsetMs) / 60_000).toInt()
        return BinaryFields.clockFields(minutes / 60, minutes % 60) + byteArrayOf(if (offsetMs < 0) 0xFF.toByte() else 0)
    }

    private fun startPushes(link: PeripheralLink) {
        pushes?.cancel()
        pushes = scope.launch {
            delay(firstPushDelay)
            while (link.isUp) {
                link.send(EversenseSession.E3_RESPONSE, frame(EversenseE3Packets.KeepAlivePush))
                delay(keepAliveInterval)
            }
        }
    }

    private fun frame(response: Byte, vararg parts: ByteArray): ByteArray =
        WireEncoding.e3(response, parts.fold(ByteArray(0)) { all, part -> all + part })

    private fun error(command: Byte, code: Byte): ByteArray = frame(EversenseE3Packets.ErrorResponseId, byteArrayOf(command, code))

    private companion object {

        val BATTERY_STEPS = listOf(0, 5, 10, 25, 35, 45, 55, 65, 75, 85, 95, 100)
        const val READINESS_READY: Byte = 0
        const val ONE_CALIBRATION_A_DAY: Byte = 1
        const val PHASE_DAILY: Byte = 3
        const val TREND_FLAT: Byte = 4
        const val VERSION = "6.03"
        const val VERSION_EXTENDED = "1.20"
        const val ERROR_UNKNOWN_COMMAND: Byte = 1
        const val ERROR_UNKNOWN_REGISTER: Byte = 2
        const val ERROR_CRC: Byte = 3
    }
}
