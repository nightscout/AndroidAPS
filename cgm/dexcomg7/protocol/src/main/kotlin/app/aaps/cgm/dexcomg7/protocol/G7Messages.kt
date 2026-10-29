package app.aaps.cgm.dexcomg7.protocol

/** Lowest and highest glucose a sensor reports as a number, mg/dL. Outside is LOW / HIGH. */
object G7GlucoseLimits {

    const val MINIMUM = 40
    const val MAXIMUM = 400
}

/**
 * Direction of change, from the sensor's rate in mg/dL per minute. Thresholds as Loop uses them, which
 * are Dexcom's: 1-2 mg/dL/min is the 45 degree arrow, 2-3 the straight arrow, 3 and more the double arrow.
 */
enum class G7Trend {

    DOUBLE_DOWN, SINGLE_DOWN, FORTY_FIVE_DOWN, FLAT, FORTY_FIVE_UP, SINGLE_UP, DOUBLE_UP;

    companion object {

        /** A rate this large is treated as noise and gets no arrow (live readings only). */
        const val MAXIMUM_RATE_FOR_ARROW = 8.0

        fun fromRate(rate: Double?): G7Trend? = when {
            rate == null -> null
            rate <= -3.0 -> DOUBLE_DOWN
            rate <= -2.0 -> SINGLE_DOWN
            rate <= -1.0 -> FORTY_FIVE_DOWN
            rate < 1.0   -> FLAT
            rate < 2.0   -> FORTY_FIVE_UP
            rate < 3.0   -> SINGLE_UP
            else         -> DOUBLE_UP
        }
    }
}

/**
 * Trend byte to mg/dL per minute. 0x7f means unknown. Signed tenths.
 */
private fun trendRate(byte: Byte): Double? = if (byte.toInt() == 0x7f) null else byte / 10.0

/**
 * The 0x4E reading on the control characteristic. Layout from Loop's G7SensorKit:
 *
 *     0      opcode 0x4e
 *     1      status, must be 0
 *     2..5   message time, seconds since the sensor started (LE)
 *     6..7   sequence
 *     10..11 age of the reading in seconds
 *     12..13 glucose, low 12 bits; 0xffff means none
 *     14     algorithm state
 *     15     trend, signed tenths of mg/dL per minute; 0x7f unknown
 *     16..17 predicted glucose, low 12 bits; 0xffff means none
 *     18     flags; 0x10 means display only (a value that came from a calibration)
 */
class G7GlucoseMessage private constructor(
    val glucose: Int?,
    val predicted: Int?,
    val glucoseIsDisplayOnly: Boolean,
    /** Seconds since the sensor started, when the message was made. */
    val messageTimestamp: Long,
    val algorithmState: AlgorithmState,
    val sequence: Int,
    val trend: Double?,
    /** Seconds between the reading and the message. */
    val age: Int,
    val data: ByteArray
) {

    /** Seconds since the sensor started, when the glucose was measured. */
    val glucoseTimestamp: Long get() = messageTimestamp - age

    val hasReliableGlucose: Boolean get() = algorithmState.hasReliableGlucose

    val trendType: G7Trend?
        get() = if (trend != null && kotlin.math.abs(trend) <= G7Trend.MAXIMUM_RATE_FOR_ARROW) G7Trend.fromRate(trend) else null

    override fun equals(other: Any?): Boolean = other is G7GlucoseMessage && other.data.contentEquals(data)
    override fun hashCode(): Int = data.contentHashCode()
    override fun toString(): String =
        "G7GlucoseMessage(glucose=$glucose seq=$sequence displayOnly=$glucoseIsDisplayOnly state=$algorithmState " +
            "time=$messageTimestamp age=$age trend=$trend data=${data.toHex()})"

    companion object {

        fun parse(data: ByteArray): G7GlucoseMessage? {
            if (data.size < 19 || data.u8(0) != G7Opcode.GLUCOSE || data.u8(1) != 0) return null
            val glucoseBytes = data.u16le(12)
            val predictionBytes = data.u16le(16)
            return G7GlucoseMessage(
                glucose = if (glucoseBytes != 0xffff) glucoseBytes and 0xfff else null,
                predicted = if (predictionBytes != 0xffff) predictionBytes and 0xfff else null,
                glucoseIsDisplayOnly = glucoseBytes != 0xffff && (data.u8(18) and 0x10) != 0,
                messageTimestamp = data.u32le(2),
                algorithmState = AlgorithmState(data.u8(14)),
                sequence = data.u16le(6),
                trend = trendRate(data[15]),
                age = data.u16le(10),
                data = data.copyOf()
            )
        }
    }
}

/**
 * One 9-byte backfill record:
 *
 *     0..2   time, seconds since the sensor started (LE, 24 bits)
 *     4..5   glucose, low 12 bits; 0xffff none
 *     6      algorithm state
 *     7      flags; 0x10 display only
 *     8      trend, signed tenths; 0x7f unknown
 */
class G7BackfillMessage private constructor(
    val timestamp: Long,
    val glucose: Int?,
    val glucoseIsDisplayOnly: Boolean,
    val algorithmState: AlgorithmState,
    val trend: Double?,
    val data: ByteArray
) {

    val hasReliableGlucose: Boolean get() = algorithmState.hasReliableGlucose

    val trendType: G7Trend? get() = G7Trend.fromRate(trend)

    override fun toString(): String =
        "G7BackfillMessage(glucose=$glucose displayOnly=$glucoseIsDisplayOnly state=$algorithmState time=$timestamp data=${data.toHex()})"

    companion object {

        const val LENGTH = 9

        fun parse(data: ByteArray): G7BackfillMessage? {
            if (data.size != LENGTH) return null
            val glucoseBytes = data.u16le(4)
            return G7BackfillMessage(
                timestamp = data.u24le(0),
                glucose = if (glucoseBytes != 0xffff) glucoseBytes and 0xfff else null,
                glucoseIsDisplayOnly = (data.u8(7) and 0x10) != 0,
                algorithmState = AlgorithmState(data.u8(6)),
                trend = trendRate(data[8]),
                data = data.copyOf()
            )
        }

        /**
         * Splits one notification into records. A G7 sends two per notification, a ONE+ one.
         * Returns null when the length is not a whole number of records.
         */
        fun parseFrame(frame: ByteArray): List<G7BackfillMessage>? {
            if (frame.isEmpty() || frame.size % LENGTH != 0) return null
            return (frame.indices step LENGTH).mapNotNull { parse(frame.copyOfRange(it, it + LENGTH)) }
        }
    }
}

/** 0x52 answer: session length, warmup and hardware facts. */
class ExtendedVersionMessage private constructor(
    /** Seconds, including the 12 hour grace period. */
    val sessionLengthSeconds: Long,
    val warmupSeconds: Int,
    val algorithmVersion: Long,
    val hardwareVersion: Int,
    val maxLifetimeDays: Int
) {

    override fun toString(): String =
        "ExtendedVersion(session=${sessionLengthSeconds}s warmup=${warmupSeconds}s algorithm=$algorithmVersion hardware=$hardwareVersion maxDays=$maxLifetimeDays)"

    companion object {

        // 10-day: 52 00 c0d70d00 5406 00020404 ff 0c00
        // 15-day: 52 00 406f1400 880e 00010a04 ff 1100
        fun parse(data: ByteArray): ExtendedVersionMessage? {
            if (data.size < 15 || data.u8(0) != G7Opcode.EXTENDED_VERSION) return null
            return ExtendedVersionMessage(
                sessionLengthSeconds = data.u32le(2),
                warmupSeconds = data.u16le(6),
                algorithmVersion = data.u32le(8),
                hardwareVersion = data.u8(12),
                maxLifetimeDays = data.u16le(13)
            )
        }
    }
}

/** 0x4A answer: firmware and the serial printed on the package. */
class TransmitterVersionMessage private constructor(
    val firmwareVersion: String,
    val softwareNumber: Long,
    val siliconVersion: Long,
    val serialNumber: Long
) {

    override fun toString(): String = "TransmitterVersion(firmware=$firmwareVersion software=$softwareNumber silicon=$siliconVersion serial=$serialNumber)"

    companion object {

        fun parse(data: ByteArray): TransmitterVersionMessage? {
            if (data.size < 20 || data.u8(0) != G7Opcode.TRANSMITTER_VERSION) return null
            var serial = 0L
            for (i in 19 downTo 14) serial = (serial shl 8) or data.u8(i).toLong()
            return TransmitterVersionMessage(
                firmwareVersion = (2..5).joinToString(".") { data.u8(it).toString() },
                softwareNumber = data.u32le(6),
                siliconVersion = data.u32le(10),
                serialNumber = serial
            )
        }
    }
}

/** The sensor's reason for refusing a session. */
enum class G7AuthFailureCode(val code: Int) {

    NONE(0),

    /** Our key is not the one the sensor holds for this display. */
    CHALLENGE_MISMATCH(1),

    /** Another display of our type holds the sensor's slot. */
    DEVICE_TYPE_RESTRICTION(2),

    /** The sensor has no key for us at all. Fixed by a fresh key exchange with the pairing code. */
    NO_APP_KEY(3);

    companion object {

        fun fromCode(code: Int): G7AuthFailureCode? = entries.firstOrNull { it.code == code }
    }
}

/** 0x05 on the authentication characteristic: the sensor's verdict on our challenge answer. */
class AuthStatusMessage private constructor(val authStatus: Int, val bondStatus: Int) {

    val isAuthenticated: Boolean get() = authStatus == 1
    val isBonded: Boolean get() = bondStatus == 1

    /**
     * The sensor checked our key and still refused. Retrying the same way cannot help, and four
     * refusals in a row make the sensor stop accepting connections for a while.
     */
    val isRejected: Boolean get() = authStatus == 2

    val failureCode: G7AuthFailureCode? get() = if (isRejected) G7AuthFailureCode.fromCode(bondStatus) else null

    companion object {

        fun parse(data: ByteArray): AuthStatusMessage? {
            if (data.size < 3 || data.u8(0) != G7Opcode.AUTH_STATUS) return null
            return AuthStatusMessage(data.u8(1), data.u8(2))
        }
    }
}

/** A meter value to calibrate with. [sensorAgeSeconds] is when it was taken, on the sensor's clock. */
class G7CalibrateTxMessage(val glucose: Int, val sensorAgeSeconds: Long) {

    val data: ByteArray get() = byteArrayOf(G7Opcode.CALIBRATE.toByte()) + glucose.u16leBytes() + sensorAgeSeconds.u32leBytes()
}

class G7CalibrateRxMessage private constructor(val status: Int) {

    val accepted: Boolean get() = status == ACCEPTED_STATUS

    companion object {

        const val ACCEPTED_STATUS = 1

        fun parse(data: ByteArray): G7CalibrateRxMessage? {
            if (data.size < 4 || data.u8(0) != G7Opcode.CALIBRATE) return null
            return G7CalibrateRxMessage(data.u16le(2))
        }
    }
}

enum class G7CalibrationProcessingStatus(val code: Int) {

    NONE(0), FACTORY_CALIBRATED(1), IN_PROGRESS(2), COMPLETE_HIGH(3), COMPLETE_LOW(4), UNKNOWN(255);

    companion object {

        fun fromCode(code: Int): G7CalibrationProcessingStatus = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** 0x32 answer: the sensor's calibration state. */
class G7CalibrationBoundsMessage private constructor(
    val lastGlucose: Int,
    /** Sensor seconds; 0 when the sensor has never been calibrated. */
    val lastCalibrationTime: Long,
    val processingStatus: G7CalibrationProcessingStatus,
    val calibrationsPermitted: Boolean,
    val lastDisplayType: G7DisplayType
) {

    val hasCalibration: Boolean get() = lastCalibrationTime > 0

    override fun toString(): String =
        "CalibrationBounds(lastGlucose=$lastGlucose at=${lastCalibrationTime}s processing=$processingStatus permitted=$calibrationsPermitted display=$lastDisplayType)"

    companion object {

        const val LENGTH = 20

        fun parse(data: ByteArray): G7CalibrationBoundsMessage? {
            if (data.size < LENGTH || data.u8(0) != G7Opcode.CALIBRATION_BOUNDS) return null
            return G7CalibrationBoundsMessage(
                lastGlucose = data.u16le(7),
                lastCalibrationTime = data.u32le(9),
                processingStatus = G7CalibrationProcessingStatus.fromCode(data.u8(13)),
                calibrationsPermitted = data.u8(14) == 1,
                lastDisplayType = G7DisplayType.fromCode(data.u8(15))
            )
        }
    }
}

/** The requests this driver sends on the control characteristic. */
object G7Commands {

    val GLUCOSE: ByteArray get() = byteArrayOf(G7Opcode.GLUCOSE.toByte())
    val EXTENDED_VERSION: ByteArray get() = byteArrayOf(G7Opcode.EXTENDED_VERSION.toByte())
    val TRANSMITTER_VERSION: ByteArray get() = byteArrayOf(G7Opcode.TRANSMITTER_VERSION.toByte())
    val CALIBRATION_BOUNDS: ByteArray get() = byteArrayOf(G7Opcode.CALIBRATION_BOUNDS.toByte())

    /** Asks for the records between two sensor times, in seconds since the sensor started. */
    fun backfill(startSeconds: Long, endSeconds: Long): ByteArray =
        byteArrayOf(G7Opcode.BACKFILL.toByte()) + startSeconds.u32leBytes() + endSeconds.u32leBytes()
}
