package app.aaps.plugins.dexcomoneplus.parse

import kotlin.time.Clock

/**
 * Control TransmitterTime response (opcode 0x25).
 *
 * Provenance: xDrip `TransmitterTimeRxMessage` at pin
 * `1e86d9a2a52577ed2c30fbf7b69d75fd56e6918f` (GPL-3.0).
 */
data class OnePlusTransmitterTimeRx(
    val statusByte: Int,
    val currentTimeSeconds: Int,
    val sessionStartTimeSeconds: Int,
) {

    fun sessionInProgress(): Boolean =
        sessionStartTimeSeconds != INVALID_TIME &&
            currentTimeSeconds != sessionStartTimeSeconds

    /**
     * Wall-clock estimate of when the sensor session started, or null if not in progress.
     */
    fun realSessionStartEpochMs(nowMs: Long = Clock.System.now().toEpochMilliseconds()): Long? {
        if (!sessionInProgress()) return null
        val ageSec = (currentTimeSeconds.toLong() - sessionStartTimeSeconds.toLong())
        return nowMs - ageSec * 1000L
    }

    fun sessionAgeSeconds(): Long? {
        if (!sessionInProgress()) return null
        return (currentTimeSeconds.toLong() - sessionStartTimeSeconds.toLong()).coerceAtLeast(0L)
    }

    companion object {
        const val OPCODE: Byte = 0x25
        /** op + status + current(4) + sessionStart(4) + crc(2). */
        const val MIN_LENGTH: Int = 12
        const val INVALID_TIME: Int = -1 // 0xFFFFFFFF

        fun parse(packet: ByteArray): OnePlusTransmitterTimeRx? {
            if (packet.size < MIN_LENGTH) return null
            if (packet[0] != OPCODE) return null
            if (!OnePlusFastCrc16.check(packet)) return null
            // Fields sit in the first 10 bytes; CRC is the final 2 (xDrip reads via absolute offsets).
            val status = packet[1].toInt() and 0xff
            val data = OnePlusByteReader(packet)
            val current = data.intAt(2)
            val sessionStart = data.intAt(6)
            return OnePlusTransmitterTimeRx(
                statusByte = status,
                currentTimeSeconds = current,
                sessionStartTimeSeconds = sessionStart,
            )
        }
    }
}
