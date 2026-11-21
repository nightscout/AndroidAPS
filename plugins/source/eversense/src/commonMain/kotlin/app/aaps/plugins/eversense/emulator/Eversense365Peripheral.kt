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
import app.aaps.plugins.eversense.packets.e365.Eversense365Packets
import app.aaps.plugins.eversense.protocol.BinaryFields
import app.aaps.plugins.eversense.protocol.SecureV2Cipher
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The radio side of an emulated Eversense 365: it advertises the Eversense service, takes the driver's messages on the
 * 365 request characteristic, and answers on the response characteristic, encrypted with AES-CCM as a real 365 does.
 *
 * The sign-in: a phone that is registered with the transmitter (the shortcut) sends only the start; both sides then
 * agree a session key by ECDH and HKDF, which this side does with its own few lines rather than the driver's code. The
 * two steps of a first registration (who-am-I and the identity from the Eversense server) are answered too, so a
 * test with a fake Eversense server can run them; this side takes any certificate, as nothing on the phone checks
 * the transmitter either.
 *
 * After the sign-in it pushes a keep-alive every [keepAliveInterval], with the time of its newest reading, which
 * makes the driver read the glucose; the history log holds one reading every five minutes, for the backfill of a gap.
 *
 * UNVERIFIED: built from the driver's code, not from captured frames (see [EversenseTransmitterEmulator]).
 */
class Eversense365Peripheral(
    private val transmitter: EversenseTransmitterEmulator,
    private val aapsLogger: AAPSLogger? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /** How often the transmitter pushes a keep-alive; a real 365 about every five minutes */
    var keepAliveInterval: Duration = 5.minutes,
    /** The largest notification fragment; a real one follows the MTU */
    private val fragmentSize: Int = 180
) : EmulatedPeripheral {

    override val device = DeviceId(transmitter.address)

    override val services = GattServices(
        mapOf(
            EversenseSession.V2_REQUEST to setOf(CharacteristicProperty.WRITE, CharacteristicProperty.WRITE_WITHOUT_RESPONSE),
            EversenseSession.V2_RESPONSE to setOf(CharacteristicProperty.NOTIFY)
        )
    )

    private val lock = AapsLock()

    /** The session of the current link: the phone's salt, the agreed key and this side's message counter */
    private var salt: ByteArray? = null
    private var key: ByteArray? = null
    private var sequence = 1L
    private var pushes: Job? = null

    /** A message the phone sends in several writes, until its last fragment */
    private var pending = ByteArray(0)

    /** The active alarms it reports, by their codes; [pushAlarm] adds one */
    private val alarms = mutableListOf<Int>()

    /** What the phone asked, by request and type id, in order: for a test that checks what was sent */
    val requests: List<Pair<Byte, Byte>> get() = lock.withLock { received.toList() }
    private val received = mutableListOf<Pair<Byte, Byte>>()

    /** True once a sign-in has agreed a session key on the current link */
    val signedIn: Boolean get() = lock.withLock { key != null }

    /** The identity (the certificate from the Eversense server) the phone sent at its first registration, if any */
    var identity: ByteArray? = null
        private set

    override fun advertisement(): Advertisement? =
        if (!transmitter.inRange) null
        else Advertisement(device, transmitter.name, rssi = -60, serviceUuids = listOf(EversenseSession.SERVICE), manufacturerData = emptyMap(), serviceData = emptyMap())

    override suspend fun onConnect(link: PeripheralLink): Boolean {
        if (!transmitter.inRange) return false
        lock.withLock {
            salt = null
            key = null
            sequence = 1
            pending = ByteArray(0)
        }
        return true
    }

    override fun onDisconnect(link: PeripheralLink) {
        pushes?.cancel()
        pushes = null
        lock.withLock { key = null }
    }

    override suspend fun onWrite(link: PeripheralLink, characteristic: CharacteristicId, data: ByteArray, type: WriteType): BleResult<Unit>? {
        if (characteristic != EversenseSession.V2_REQUEST || data.size < 2)
            return BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED, message = "not a 365 message"))
        val message = assemble(data) ?: return BleResult.Ok(Unit)
        val answer = answer(link, message)
        // After the write is done, as a real transmitter answers
        if (answer != null) scope.launch { send(link, answer) }
        return BleResult.Ok(Unit)
    }

    /** An alarm the transmitter raises by itself: pushed now, and listed by the active alarms from then on */
    fun pushAlarm(link: PeripheralLink, code: Int) {
        lock.withLock { alarms += code }
        val body = ByteArray(12)
        body[0] = Eversense365Packets.NotificationResponseId
        body[1] = Eversense365Packets.NotificationAlarmWithData
        body[2] = code.toByte()
        BinaryFields.sensorTimestamp(transmitter.clock()).copyInto(body, 4)
        scope.launch { send(link, encrypt(body) ?: return@launch) }
    }

    /** The current link, for a test that pushes an alarm */
    var link: PeripheralLink? = null
        private set

    /** Joins the fragments of one message: the first is [1, total, 1, data...], the others [i, total, data...] */
    private fun assemble(data: ByteArray): ByteArray? = lock.withLock {
        val index = data[0].toInt() and 0xFF
        val total = data[1].toInt() and 0xFF
        if (index == 1) pending = ByteArray(0)
        pending += data.copyOfRange(if (index == 1) 3 else 2, data.size)
        if (index < total) null else pending.also { pending = ByteArray(0) }
    }

    /** The answer to one message, encrypted unless it is a sign-in step; null for none */
    private fun answer(link: PeripheralLink, message: ByteArray): ByteArray? {
        this.link = link
        if (message[0] == Eversense365Packets.AuthenticateCommandId) return authenticate(message)
        val plain = decrypt(message) ?: return null.also { aapsLogger?.debug(LTag.BGSOURCE) { "Emulated 365: a message it cannot decrypt" } }
        if (plain.size < 2) return null
        lock.withLock { received += plain[0] to plain[1] }
        startPushes(link)
        return encrypt(command(plain))
    }

    private fun authenticate(message: ByteArray): ByteArray? = when (message.getOrNull(1)) {
        // Who am I: the serial number, a nonce for the server, and flags
        Eversense365Packets.AuthenticateWhoAmI -> plain(Eversense365Packets.AuthenticateWhoAmI, transmitter.serialBytes(), CryptographyRandom.nextBytes(8), ByteArray(2))
        // The identity the Eversense server signed: taken as it is
        Eversense365Packets.AuthenticateIdentity -> {
            identity = message.copyOfRange(2, message.size)
            plain(Eversense365Packets.AuthenticateIdentity)
        }
        // The start: 80 00, the client id, the phone's ephemeral key (X || Y), its salt and signature
        Eversense365Packets.AuthenticateStart    -> {
            if (message.size < START_SIZE) null
            else {
                val phoneSalt = message.copyOfRange(100, 108)
                val (ownPublic, sessionKey) = agree(message.copyOfRange(36, 100), phoneSalt)
                lock.withLock {
                    salt = phoneSalt
                    key = sessionKey
                    sequence = 1
                }
                plain(Eversense365Packets.AuthenticateStart, ownPublic)
            }
        }

        else                                     -> null
    }

    private fun command(plain: ByteArray): ByteArray {
        val request = plain[0]
        val type = plain[1]
        return when (request) {
            Eversense365Packets.ReadCommandId      -> when (type) {
                Eversense365Packets.ReadPing              -> response(Eversense365Packets.ReadResponseId, type)
                Eversense365Packets.ReadSensorInformation -> sensorInformation()
                Eversense365Packets.ReadCalibrationInfo   -> calibrationInfo()
                Eversense365Packets.ReadActiveAlerts      -> activeAlarms()
                Eversense365Packets.ReadGlucoseData       -> glucose()
                Eversense365Packets.ReadLogRange          -> logRange(plain.getOrElse(2) { 0 })
                Eversense365Packets.ReadLogValue          -> logValues(plain)
                Eversense365Packets.ReadSignalStrength    -> signal()
                else                                      -> rejected(type)
            }

            Eversense365Packets.WriteCommandId     -> when (type) {
                Eversense365Packets.WriteCurrentDateTime -> {
                    transmitter.setClock(BinaryFields.sensorTimestamp(plain.copyOfRange(2, 10).toUByteArray()), 0)
                    response(Eversense365Packets.WriteResponseId, type)
                }

                Eversense365Packets.WriteCalibration     -> {
                    // ts(sample) ts(now) glucose
                    transmitter.calibrate(BinaryFields.unsigned(plain.toUByteArray(), 18, 2).toInt())
                    response(Eversense365Packets.WriteResponseId, type)
                }

                // The app version and the disconnect timeout: taken, nothing to keep
                Eversense365Packets.WriteAppVersion,
                Eversense365Packets.WriteBleDisconnect   -> response(Eversense365Packets.WriteResponseId, type)

                else                                     -> rejected(type)
            }

            Eversense365Packets.OperationCommandId -> when (type) {
                Eversense365Packets.EnterDiagnosticModeOperationId -> {
                    transmitter.diagnosticMode = true
                    response(Eversense365Packets.OperationResponseId, type)
                }

                Eversense365Packets.ExitDiagnosticModeOperationId  -> {
                    transmitter.diagnosticMode = false
                    response(Eversense365Packets.OperationResponseId, type)
                }

                else                                               -> rejected(type)
            }

            else                                   -> rejected(type)
        }
    }

    /** [2..17] serial, [18..42] name, [43..50] clock, [103] sensor id length, then insertion, battery, versions */
    private fun sensorInformation(): ByteArray {
        val body = response(Eversense365Packets.ReadResponseId, Eversense365Packets.ReadSensorInformation, size = 139 + 2 * SENSOR_ID_LENGTH)
        text(transmitter.serial, 16).copyInto(body, 2)
        text(transmitter.name, 25).copyInto(body, 18)
        BinaryFields.sensorTimestamp(transmitter.clock()).copyInto(body, 43)
        body[103] = SENSOR_ID_LENGTH.toByte()
        BinaryFields.sensorTimestamp(transmitter.insertedAt).copyInto(body, 104 + SENSOR_ID_LENGTH)
        body[114 + 2 * SENSOR_ID_LENGTH] = transmitter.batteryPercent.toByte()
        text(VERSION, 16).copyInto(body, 115 + 2 * SENSOR_ID_LENGTH)
        text(VERSION_EXTENDED, 8).copyInto(body, 131 + 2 * SENSOR_ID_LENGTH)
        return body
    }

    /** [2] phase (daily), [4..11] next calibration, [12] calibrations a day (0: daily), [34..41] last calibration */
    private fun calibrationInfo(): ByteArray {
        val body = response(Eversense365Packets.ReadResponseId, Eversense365Packets.ReadCalibrationInfo, size = 42)
        body[2] = PHASE_DAILY
        BinaryFields.sensorTimestamp(transmitter.lastCalibrationAt + CALIBRATION_INTERVAL_MS).copyInto(body, 4)
        BinaryFields.sensorTimestamp(transmitter.lastCalibrationAt).copyInto(body, 34)
        return body
    }

    /** [2] count, then code, flag and priority of each */
    private fun activeAlarms(): ByteArray {
        val codes = lock.withLock { alarms.toList() }
        val body = response(Eversense365Packets.ReadResponseId, Eversense365Packets.ReadActiveAlerts, size = 3 + 3 * codes.size)
        body[2] = codes.size.toByte()
        codes.forEachIndexed { index, code -> body[3 + 3 * index] = code.toByte() }
        return body
    }

    /** [11] sensor id length, then the time, the glucose, the signal, the "unavailable" reason (0) and the trend */
    private fun glucose(): ByteArray {
        val reading = transmitter.latestReading()
        val body = response(Eversense365Packets.ReadResponseId, Eversense365Packets.ReadGlucoseData, size = 165 + SENSOR_ID_LENGTH)
        body[11] = SENSOR_ID_LENGTH.toByte()
        BinaryFields.sensorTimestamp(reading.timestamp).copyInto(body, 12 + SENSOR_ID_LENGTH)
        BinaryFields.littleEndian(reading.glucoseMgDl.toLong(), 2).copyInto(body, 20 + SENSOR_ID_LENGTH)
        BinaryFields.littleEndian(SIGNAL_PERCENT.toLong(), 2).copyInto(body, 22 + SENSOR_ID_LENGTH)
        body[164 + SENSOR_ID_LENGTH] = TREND_FLAT
        return body
    }

    /** The glucose log holds a day of readings, numbered by their time; the calibration log is empty */
    private fun logRange(logType: Byte): ByteArray {
        val body = response(Eversense365Packets.ReadResponseId, Eversense365Packets.ReadLogRange, size = 11)
        body[2] = logType
        if (logType == Eversense365Packets.LogTypeGlucose) {
            val newest = readingNumber(transmitter.latestReading().timestamp)
            BinaryFields.littleEndian(newest - LOG_SIZE, 4).copyInto(body, 3)
            BinaryFields.littleEndian(newest, 4).copyInto(body, 7)
        }
        return body
    }

    /** The glucose log records from..to: 193 bytes each, the time at 4, the glucose at 12 + id length, the trend 6 after */
    private fun logValues(plain: ByteArray): ByteArray {
        val logType = plain.getOrElse(2) { 0 }
        val head = ByteArray(7).also {
            it[0] = Eversense365Packets.ReadLogsId
            it[1] = Eversense365Packets.ReadLogValue
            it[6] = logType
        }
        if (logType != Eversense365Packets.LogTypeGlucose || plain.size < 11) return head
        val bytes = plain.toUByteArray()
        val from = BinaryFields.unsigned(bytes, 3, 4)
        val to = BinaryFields.unsigned(bytes, 7, 4)
        val interval = transmitter.readingInterval.inWholeMilliseconds
        val records = (from..to).map { number ->
            val time = number * interval
            ByteArray(GLUCOSE_RECORD).also { record ->
                BinaryFields.sensorTimestamp(time).copyInto(record, 4)
                BinaryFields.littleEndian(transmitter.glucoseAt(time).toLong(), 2).copyInto(record, 12 + SENSOR_ID_LENGTH)
                record[18 + SENSOR_ID_LENGTH] = TREND_FLAT
            }
        }
        return records.fold(head) { all, record -> all + record }
    }

    /** [3] sensor id length, then the placement signal as a fraction (float, little endian) */
    private fun signal(): ByteArray {
        val body = response(Eversense365Packets.ReadResponseId, Eversense365Packets.ReadSignalStrength, size = 18 + SENSOR_ID_LENGTH)
        body[3] = SENSOR_ID_LENGTH.toByte()
        BinaryFields.littleEndian((SIGNAL_PERCENT / 100f).toRawBits().toLong(), 4).copyInto(body, 14 + SENSOR_ID_LENGTH)
        return body
    }

    private fun readingNumber(time: Long): Long = time / transmitter.readingInterval.inWholeMilliseconds

    private fun startPushes(link: PeripheralLink) {
        if (pushes?.isActive == true) return
        pushes = scope.launch {
            while (link.isUp) {
                delay(keepAliveInterval)
                val reading = transmitter.latestReading()
                val body = ByteArray(19)
                body[0] = Eversense365Packets.NotificationResponseId
                body[1] = Eversense365Packets.NotificationKeepAlive
                body[10] = transmitter.batteryPercent.toByte()
                BinaryFields.sensorTimestamp(reading.timestamp).copyInto(body, 11)
                send(link, encrypt(body) ?: continue)
            }
        }
    }

    /** A message as notifications: the first fragment [1, total, 1, data...], the others [i, total, data...] */
    private fun send(link: PeripheralLink, message: ByteArray) {
        val first = fragmentSize - 3
        val rest = fragmentSize - 2
        val parts = mutableListOf(message.copyOfRange(0, minOf(first, message.size)))
        var offset = first
        while (offset < message.size) {
            parts += message.copyOfRange(offset, minOf(offset + rest, message.size))
            offset += rest
        }
        parts.forEachIndexed { index, part ->
            val header = if (index == 0) byteArrayOf(1, parts.size.toByte(), 1) else byteArrayOf((index + 1).toByte(), parts.size.toByte())
            link.send(EversenseSession.V2_RESPONSE, header + part)
        }
    }

    private fun encrypt(plain: ByteArray): ByteArray? {
        val (sessionKey, phoneSalt, index) = lock.withLock { Triple(key, salt, sequence++ and 0x3fff) }
        if (sessionKey == null || phoneSalt == null) return null
        return SecureV2Cipher.encrypt(plain, sessionKey, SecureV2Cipher.prefix(index), SecureV2Cipher.nonce(phoneSalt, index))
    }

    private fun decrypt(message: ByteArray): ByteArray? {
        val (sessionKey, phoneSalt) = lock.withLock { key to salt }
        if (sessionKey == null || phoneSalt == null || message.size < 2) return null
        val prefix = message.copyOfRange(0, 2)
        val index = (BinaryFields.unsigned(prefix.toUByteArray()) shr 2) and 0x3fff
        return SecureV2Cipher.decrypt(message.copyOfRange(2, message.size), sessionKey, prefix, SecureV2Cipher.nonce(phoneSalt, index))
    }

    /** ECDH with the phone's ephemeral key, then HKDF-SHA256 with no salt and the phone's salt as the info, 16 bytes */
    private fun agree(phonePublic: ByteArray, phoneSalt: ByteArray): Pair<ByteArray, ByteArray> {
        val ecdh = CryptographyProvider.Default.get(ECDH)
        val keyPair = ecdh.keyPairGenerator(EC.Curve.P256).generateKeyBlocking()
        val ownPublic = keyPair.publicKey.encodeToByteArrayBlocking(EC.PublicKey.Format.RAW).copyOfRange(1, 65)
        val phone = ecdh.publicKeyDecoder(EC.Curve.P256).decodeFromByteArrayBlocking(EC.PublicKey.Format.RAW, byteArrayOf(4) + phonePublic)
        val shared = keyPair.privateKey.sharedSecretGenerator().generateSharedSecretToByteArrayBlocking(phone)
        val pseudoRandomKey = hmac(ByteArray(32), shared)
        return ownPublic to hmac(pseudoRandomKey, phoneSalt + byteArrayOf(1)).copyOf(16)
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        CryptographyProvider.Default.get(HMAC).keyDecoder(SHA256).decodeFromByteArrayBlocking(HMAC.Key.Format.RAW, key)
            .signatureGenerator().generateSignatureBlocking(data)

    private fun response(request: Byte, type: Byte, size: Int = 2): ByteArray = ByteArray(size).also {
        it[0] = request
        it[1] = type
    }

    /** A sign-in answer: plain, 0B and the step, then the fields */
    private fun plain(step: Byte, vararg fields: ByteArray): ByteArray =
        fields.fold(byteArrayOf(Eversense365Packets.AuthenticateResponseId, step)) { all, field -> all + field }

    /** A command it does not know: FF, the type, an error code */
    private fun rejected(type: Byte): ByteArray = byteArrayOf(0xFF.toByte(), type, 5)

    private fun text(value: String, size: Int): ByteArray = value.encodeToByteArray().copyOf(size)

    private companion object {

        const val SENSOR_ID_LENGTH = 10
        const val START_SIZE = 172
        const val GLUCOSE_RECORD = 193
        const val LOG_SIZE = 288L
        const val PHASE_DAILY: Byte = 3
        const val TREND_FLAT: Byte = 4
        const val SIGNAL_PERCENT = 60
        const val VERSION = "1.0.0"
        const val VERSION_EXTENDED = "1"
        const val CALIBRATION_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
