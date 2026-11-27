package app.aaps.plugins.dexcomoneplus.parse

/**
 * What the sensor says about its own session (opcode 0x52): how long it lasts and how long it warms up.
 *
 * A G7 15-day sensor runs 15 days and warms up about 1 hour. A 10-day G7 or a ONE+ runs 10 days and
 * warms up about 27 minutes. Both lengths include the 12 hour grace period at the end.
 *
 * Layout (at least 15 bytes): opcode | status | session length u32 LE @2 (seconds) |
 * warm-up u16 LE @6 (seconds) | algorithm version u32 LE @8 | hardware @12 | max days u16 LE @13.
 *
 * Seen answers:
 * - 10-day: `52 00 c0d70d00 5406 00020404 ff 0c00` (907200 s = 10.5 days, 1620 s = 27 min)
 * - 15-day: `52 00 406f1400 880e 00010a04 ff 1100` (1339200 s = 15.5 days, 3720 s = 62 min)
 *
 * Provenance: AAPS PR 5397 (`ExtendedVersionMessage`, ported from Trio G7SensorKit, MIT), tested on
 * G7 sensors.
 */
data class OnePlusExtendedVersionRx(
    /** Whole session, grace period included, in seconds. */
    val sessionLengthSeconds: Long,
    val warmupSeconds: Int,
) {

    val sessionLengthMs: Long get() = sessionLengthSeconds * 1000L
    val warmupMs: Long get() = warmupSeconds * 1000L

    companion object {

        const val OPCODE: Byte = 0x52
        const val MIN_LENGTH: Int = 15

        /** A session shorter or longer than this is a misread, not a sensor. */
        private const val MIN_SESSION_SECONDS = 5L * 24L * 3600L
        private const val MAX_SESSION_SECONDS = 20L * 24L * 3600L

        /** A warm-up outside this range is a misread. */
        private const val MIN_WARMUP_SECONDS = 10 * 60
        private const val MAX_WARMUP_SECONDS = 3 * 3600

        /** The request is the opcode alone. */
        fun request(): ByteArray = byteArrayOf(OPCODE)

        /** Null when this is not a 0x52 answer, or when its values make no sense. */
        fun parse(packet: ByteArray): OnePlusExtendedVersionRx? {
            if (packet.size < MIN_LENGTH || packet[0] != OPCODE) return null
            val data = OnePlusByteReader(packet, position = 2)
            val session = data.int().toLong() and 0xffffffffL
            val warmup = data.short().toInt() and 0xffff
            if (session !in MIN_SESSION_SECONDS..MAX_SESSION_SECONDS) return null
            if (warmup !in MIN_WARMUP_SECONDS..MAX_WARMUP_SECONDS) return null
            return OnePlusExtendedVersionRx(sessionLengthSeconds = session, warmupSeconds = warmup)
        }
    }
}
