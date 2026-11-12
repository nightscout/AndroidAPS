package app.aaps.plugins.dexcomoneplus.parse

/**
 * What the sensor answered to a calibration.
 *
 * There are two shapes.
 *
 * [Form.Echo] is the G7 answer, and what a Dexcom ONE+ sends: 4 bytes or more, `34 | info |
 * status (u16 LE)`. Status 1 means the sensor took the value; anything else is a refusal. This is
 * how the Dexcom G7 driver of AAPS PR 5397 reads it, tested on G7 sensors. The two public ONE+
 * captures, `34 00 01 00` and `34 00 02 00`, are then one acceptance and one refusal.
 *
 * [Form.Status] is the G5/G6 answer xDrip documents: opcode 0x35, 5 bytes `opcode | info | result |
 * crc16`. Kept in case a sensor answers that way.
 *
 * Provenance: xDrip `CalibrateRxMessage` (GPL-3.0) for the 0x35 shape; AAPS PR 5397
 * (`G7CalibrateRxMessage`) for the echo.
 */
data class OnePlusCalibrateRx(
    val info: Int,
    val result: Int,
    val valid: Boolean,
    val form: Form = Form.Status,
    /** The bytes exactly as they arrived, so an unread answer can still be reported and studied. */
    val rawHex: String = "",
) {

    enum class Form {

        /** Opcode 0x35, result byte readable against the code table below. */
        Status,

        /** The G7 / ONE+ answer: opcode 0x34 again, then a status where 1 means taken. */
        Echo
    }

    enum class Outcome {

        /** The sensor said it took the value. */
        ACCEPTED,

        /** The sensor said no, and said why. */
        REFUSED,

        /** Something came back that we cannot read. It may or may not have been taken. */
        UNKNOWN
    }

    fun outcome(): Outcome = when {
        !valid     -> Outcome.UNKNOWN
        accepted() -> Outcome.ACCEPTED
        else       -> Outcome.REFUSED
    }

    /**
     * The sensor took the value. For an echo that is status [ECHO_ACCEPTED]. For the 0x35 answer,
     * [SECOND_NEEDED] and [DUPLICATE] are accepted outcomes too.
     */
    fun accepted(): Boolean = valid && when (form) {
        Form.Echo   -> result == ECHO_ACCEPTED
        Form.Status -> result == OK || result == SECOND_NEEDED || result == DUPLICATE
    }

    /** The sensor took it and asks for a second fingerstick before it fully trusts itself again. */
    fun wantsSecondCalibration(): Boolean = valid && form == Form.Status && result == SECOND_NEEDED

    fun message(): String = when {
        !valid                  -> "Unable to decode"
        form == Form.Echo       -> if (result == ECHO_ACCEPTED) "Accepted ($rawHex)" else "Refused, status $result ($rawHex)"
        result == OK            -> "OK"
        result == 0x01          -> "Code 1"
        result == SECOND_NEEDED -> "Second calibration needed"
        result == REJECTED      -> "Rejected"
        result == STOPPED       -> "Sensor stopped"
        result == DUPLICATE     -> "Duplicate"
        result == NOT_READY     -> "Not ready to calibrate"
        else                    -> "Unknown code: $result"
    }

    companion object {

        const val OPCODE: Byte = 0x35
        const val PACKET_LENGTH: Int = 5

        /** A G7 / ONE+ answers by echoing the request opcode instead of using [OPCODE]. */
        const val ECHO_OPCODE: Byte = OnePlusCalibrateTx.OPCODE
        const val ECHO_PACKET_LENGTH: Int = 4

        /** Echo status for "taken". */
        const val ECHO_ACCEPTED = 1

        const val OK = 0x00
        const val SECOND_NEEDED = 0x06
        const val REJECTED = 0x08
        const val STOPPED = 0x0B
        const val DUPLICATE = 0x0D
        const val NOT_READY = 0x0E

        /**
         * Read an answer to a calibration, or null when the packet is something else entirely.
         *
         * Only ever called while a calibration reply is being waited for, so a 0x34-prefixed packet
         * here is an answer to our own request and not a stray command.
         */
        fun parse(packet: ByteArray): OnePlusCalibrateRx? {
            if (packet.size >= ECHO_PACKET_LENGTH && packet[0] == ECHO_OPCODE) {
                return OnePlusCalibrateRx(
                    info = packet[1].toInt() and 0xff,
                    result = (packet[2].toInt() and 0xff) or ((packet[3].toInt() and 0xff) shl 8),
                    valid = true,
                    form = Form.Echo,
                    rawHex = packet.toHex(),
                )
            }
            if (packet.size != PACKET_LENGTH) return null
            if (packet[0] != OPCODE) return null
            if (!OnePlusFastCrc16.check(packet)) return null
            val data = OnePlusByteReader(packet)
            data.skip(1) // opcode
            val info = data.u8()
            val result = data.u8()
            return OnePlusCalibrateRx(
                info = info,
                result = result,
                valid = true,
                form = Form.Status,
                rawHex = packet.toHex(),
            )
        }

        private fun ByteArray.toHex(): String = onePlusToHex()
    }
}
