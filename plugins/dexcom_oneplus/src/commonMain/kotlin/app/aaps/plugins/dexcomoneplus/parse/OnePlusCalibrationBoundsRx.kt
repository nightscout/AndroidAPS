package app.aaps.plugins.dexcomoneplus.parse

/**
 * The sensor's own calibration state (opcode 0x32), read after a calibration is sent.
 *
 * It answers two questions the 0x34 answer cannot: did the value really arrive when that answer
 * was lost on the way, and has the sensor finished taking it in. The second matters because a
 * reading or two right after a calibration can still be the old one.
 *
 * Layout (20 bytes): opcode | … | last glucose u16 LE @7 | last calibration time u32 LE @9 |
 * processing @13 | calibrations permitted @14 | …
 *
 * Provenance: AAPS PR 5397 (`G7CalibrationBoundsMessage`), tested on G7 sensors. Not yet seen
 * from a ONE+: when it does not answer, nothing changes.
 */
data class OnePlusCalibrationBoundsRx(
    val lastGlucoseMgdl: Int,
    /** Sensor seconds; 0 when the sensor was never calibrated. */
    val lastCalibrationTimeSeconds: Long,
    val processing: Processing,
    val calibrationsPermitted: Boolean,
) {

    enum class Processing(val code: Int) {
        NONE(0), FACTORY_CALIBRATED(1), IN_PROGRESS(2), COMPLETE_HIGH(3), COMPLETE_LOW(4), UNKNOWN(255);

        companion object {

            fun fromCode(code: Int): Processing = entries.firstOrNull { it.code == code } ?: UNKNOWN
        }
    }

    /** True when this is the value we sent, at the time we sent for it. */
    fun matches(glucoseMgdl: Int, dexTimeSeconds: Int): Boolean =
        lastGlucoseMgdl == glucoseMgdl && lastCalibrationTimeSeconds == dexTimeSeconds.toLong()

    companion object {

        const val OPCODE: Byte = 0x32
        const val PACKET_LENGTH: Int = 20

        /** The request is the opcode alone. */
        fun request(): ByteArray = byteArrayOf(OPCODE)

        fun parse(packet: ByteArray): OnePlusCalibrationBoundsRx? {
            if (packet.size < PACKET_LENGTH || packet[0] != OPCODE) return null
            val data = OnePlusByteReader(packet, position = 7)
            val lastGlucose = data.short().toInt() and 0xffff
            val lastTime = data.int().toLong() and 0xffffffffL
            val processing = Processing.fromCode(data.u8())
            val permitted = data.u8() == 1
            return OnePlusCalibrationBoundsRx(lastGlucose, lastTime, processing, permitted)
        }
    }
}
