package app.aaps.pump.aaruy.common.util

object AaruyConst {

    /**
     * The low byte of a pump message whose high byte is 0, the answers to a motor command.
     * Errors (high byte 0x80) and alarms (0x81) are in `AlarmState`.
     */
    object errCodeType {

        /** Prime with the piston already in place has ended. */
        const val DISCHARGE_LIQUID: Byte = 16

        /** The piston is back: a rewind has ended. Seen on a real pump with one beep. */
        const val PUSH_BACK_DONE: Byte = 17

        /** The piston has found the reservoir. Seen on a real pump after each press of Prime. */
        const val FLAG_LOCATE_POSITION: Byte = 22

        /** The piston has found the reservoir and the pump saw liquid come out. UNVERIFIED on a real pump. */
        const val FLAG_LOCATE_POS_LIQUID_DISCHARGE: Byte = 25
    }
}
