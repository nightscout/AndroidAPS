package app.aaps.plugins.libre3.parse

/**
 * What the sensor says about its own health.
 *
 * Ported from LibreCRKit `DataPlane/PatchStatus.swift` at pin `a86b92f`.
 */
enum class Libre3SensorError {

    NONE,

    /** The sensor never started properly when it was put on. */
    INSERTION_FAILURE,

    /** The sensor reached the end of its life in the normal way. */
    EXPIRED,

    /** The sensor was shut down and cannot be started again. */
    TERMINATED,

    /** The sensor had trouble sending. Often passes by itself. */
    TRANSMISSION_ERROR,

    /** A code this driver does not know. Treated as a fault, never as good news. */
    UNKNOWN;

    /** True when AAPS should tell the user that something is wrong with the sensor. */
    val needsUserAttention: Boolean get() = this != NONE && this != TRANSMISSION_ERROR

    companion object {

        fun fromCode(code: Int): Libre3SensorError = when (code) {
            0    -> NONE
            3    -> INSERTION_FAILURE
            5    -> EXPIRED
            6, 8 -> TERMINATED
            7    -> TRANSMISSION_ERROR
            else -> UNKNOWN
        }
    }
}

/**
 * The state the sensor reports for itself.
 *
 * Only state 4 means a sensor that is running normally.
 */
data class Libre3PatchState(val raw: Int) {

    val isActive: Boolean get() = raw == 4

    /** Ended or in error. The sensor may still send old readings, then stop. */
    val isExpiredOrError: Boolean get() = raw == 3 || raw == 5 || raw == 7

    /** Already shut down. Nothing more will come from it. */
    val isTerminated: Boolean get() = raw == 6 || raw == 8
}

/**
 * One health message from the sensor, 12 plain bytes.
 *
 * @param lifeCount minutes since the sensor started, as reported in this message.
 * @param errorData the fault code, see [Libre3SensorError].
 */
data class Libre3PatchStatus(
    val lifeCount: Int,
    val errorData: Int,
    val eventData: Int,
    val index: Int,
    val patchState: Libre3PatchState,
    val currentLifeCount: Int,
    val stackDisconnectReason: Int,
    val appDisconnectReason: Int,
) {

    val sensorError: Libre3SensorError get() = Libre3SensorError.fromCode(errorData)

    /**
     * True when AAPS should treat the sensor as faulty.
     *
     * A fault code counts, and so does a state that says ended or shut down, even when no code
     * came with it.
     */
    fun hasSensorError(): Boolean =
        sensorError.needsUserAttention || patchState.isTerminated || patchState.isExpiredOrError
}

/** Reads the 12 plain bytes of a health message. */
object Libre3PatchStatusParser {

    const val PLAINTEXT_SIZE = 12

    fun parse(plaintext: ByteArray): Libre3PatchStatus {
        if (plaintext.size != PLAINTEXT_SIZE) {
            throw Libre3ParseException("a health message is 12 bytes, not ${plaintext.size}")
        }
        return Libre3PatchStatus(
            lifeCount = s16(plaintext, 0),
            errorData = s16(plaintext, 2),
            // The sensor sends this number shifted down by 4000.
            eventData = 4000 + s16(plaintext, 4),
            index = plaintext[6].toInt(),
            patchState = Libre3PatchState(plaintext[7].toInt()),
            currentLifeCount = s16(plaintext, 8),
            stackDisconnectReason = plaintext[10].toInt(),
            appDisconnectReason = plaintext[11].toInt(),
        )
    }

    /** These numbers can be negative, so the sign has to be kept. */
    private fun s16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)).toShort().toInt()
}
