package app.aaps.plugins.dexcomoneplus.parse

/**
 * Control Calibrate request (opcode 0x34) — hands a fingerstick to the sensor's own algorithm.
 *
 * Layout (7 bytes, no CRC): opcode | glucose (u16 LE, mg/dL) | sensor time (u32 LE)
 *
 * This is the G7 layout used by the Dexcom G7 driver of AAPS PR 5397, which was tested on G7
 * sensors. The earlier layout of this file was xDrip's G6 message with a CRC on the end (9
 * bytes). A ONE+ answered that one with `34 00 02 00`, which the G7 layout reads as a refusal.
 * Whether the ONE+ takes the 7-byte form has to be seen on a real sensor: check the answer in
 * the log the first time.
 *
 * The time is the time given for the blood, in sensor seconds, not the moment the value is sent:
 * the sensor needs it to line the fingerstick up with its own history. The calibration dialog
 * gives the moment the user confirmed the value, which is a little after the finger prick. A
 * value whose time is wrong is worse than no calibration at all.
 *
 * Nothing here decides WHETHER to send. The sensor keeps a calibration for good — it cannot be
 * edited or deleted afterwards — so the gates live at the call site (range, age, stability).
 */
object OnePlusCalibrateTx {

    const val OPCODE: Byte = 0x34
    const val PACKET_LENGTH: Int = 7

    /** Range the sensor itself accepts, and the same one the official app and xDrip apply. */
    const val MIN_GLUCOSE_MGDL = 40
    const val MAX_GLUCOSE_MGDL = 400

    /**
     * @param glucoseMgdl fingerstick value in mg/dL, [MIN_GLUCOSE_MGDL]..[MAX_GLUCOSE_MGDL]
     * @param dexTimeSeconds sensor time of the FINGERSTICK (not of this call)
     * @return the packet, or null when the value is outside the range the sensor accepts
     */
    fun build(glucoseMgdl: Int, dexTimeSeconds: Int): ByteArray? {
        if (glucoseMgdl < MIN_GLUCOSE_MGDL || glucoseMgdl > MAX_GLUCOSE_MGDL) return null
        if (dexTimeSeconds < 0) return null
        val buf = OnePlusByteWriter(PACKET_LENGTH)
        buf.put(OPCODE)
        buf.putShort(glucoseMgdl.toShort())
        buf.putInt(dexTimeSeconds)
        return buf.array()
    }
}
