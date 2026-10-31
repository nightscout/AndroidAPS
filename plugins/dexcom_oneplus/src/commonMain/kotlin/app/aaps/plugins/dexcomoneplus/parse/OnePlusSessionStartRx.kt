package app.aaps.plugins.dexcomoneplus.parse

/**
 * Control SessionStart response (opcode 0x27).
 *
 * Provenance: xDrip `SessionStartRxMessage` at pin
 * `1e86d9a2a52577ed2c30fbf7b69d75fd56e6918f` (GPL-3.0).
 */
data class OnePlusSessionStartRx(
    val status: Int,
    val info: Int,
    val requestedStartTime: Int,
    val sessionStartTime: Int,
    val transmitterTime: Int,
    val valid: Boolean,
) {

    fun isOkay(): Boolean =
        valid &&
            status == 0x00 &&
            (info == 0x01 || info == 0x05 || info == 0x06) &&
            sessionStartTime != INVALID_TIME

    /** Sensor already running — not a hard failure for reconnect / re-start UX. */
    fun isAlreadyStarted(): Boolean = valid && info == 0x02

    fun isFubar(): Boolean = valid && info == 0x04

    fun message(): String = when (info) {
        0x01 -> "OK"
        0x02 -> "Already started"
        0x03 -> "Invalid"
        0x04 -> "Clock not synchronized or other error"
        0x05 -> "OK G6"
        0x06 -> "OK G6 - unsure"
        else -> "Unknown code: $info"
    }

    companion object {
        const val OPCODE: Byte = 0x27
        const val PACKET_LENGTH: Int = 17
        const val INVALID_TIME: Int = -1 // 0xFFFFFFFF as signed int

        fun parse(packet: ByteArray): OnePlusSessionStartRx? {
            if (packet.size != PACKET_LENGTH) return null
            if (packet[0] != OPCODE) return null
            if (!OnePlusFastCrc16.check(packet)) return null
            val data = OnePlusByteReader(packet)
            data.skip(1) // opcode
            val status = data.u8()
            val info = data.u8()
            val requested = data.int()
            val sessionStart = data.int()
            val txTime = data.int()
            return OnePlusSessionStartRx(
                status = status,
                info = info,
                requestedStartTime = requested,
                sessionStartTime = sessionStart,
                transmitterTime = txTime,
                valid = true,
            )
        }
    }
}
