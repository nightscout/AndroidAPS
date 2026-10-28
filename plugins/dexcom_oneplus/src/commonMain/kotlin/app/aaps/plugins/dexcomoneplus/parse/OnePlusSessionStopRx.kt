package app.aaps.plugins.dexcomoneplus.parse

/**
 * Control SessionStop response (opcode 0x29).
 *
 * Provenance: xDrip `SessionStopRxMessage` at pin
 * `1e86d9a2a52577ed2c30fbf7b69d75fd56e6918f` (GPL-3.0).
 */
data class OnePlusSessionStopRx(
    val status: Int,
    val received: Int,
    val sessionStopTime: Int,
    val sessionStartTime: Int,
    val transmitterTime: Int,
    val valid: Boolean,
) {

    fun isOkay(): Boolean = valid && status == 0x00

    companion object {
        const val OPCODE: Byte = 0x29
        const val PACKET_LENGTH: Int = 17

        fun parse(packet: ByteArray): OnePlusSessionStopRx? {
            if (packet.size != PACKET_LENGTH) return null
            if (packet[0] != OPCODE) return null
            if (!OnePlusFastCrc16.check(packet)) return null
            val data = OnePlusByteReader(packet)
            data.skip(1) // opcode
            val status = data.u8()
            val received = data.u8()
            val stop = data.int()
            val start = data.int()
            val txTime = data.int()
            return OnePlusSessionStopRx(
                status = status,
                received = received,
                sessionStopTime = stop,
                sessionStartTime = start,
                transmitterTime = txTime,
                valid = true,
            )
        }
    }
}
