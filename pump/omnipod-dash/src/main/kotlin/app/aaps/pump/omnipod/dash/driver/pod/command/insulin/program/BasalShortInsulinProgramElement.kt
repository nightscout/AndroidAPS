package app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program

import java.nio.ByteBuffer
import kotlin.experimental.and

class BasalShortInsulinProgramElement(
    private val numberOfSlots: Byte, // 4 bits
    private val pulsesPerSlot: Short, // 10 bits
    private val extraAlternatePulse: Boolean
) : ShortInsulinProgramElement {

    override val encoded: ByteArray
        get() {
            val firstByte = (
                numberOfSlots - 1 and 0x0f shl 4
                    or ((if (extraAlternatePulse) 1 else 0) shl 3)
                    or (pulsesPerSlot.toInt() ushr 8 and 0x03)
                ).toByte()
            return ByteBuffer.allocate(2)
                .put(firstByte)
                .put((pulsesPerSlot and 0xff).toByte())
                .array()
        }

    override fun toString(): String {
        return "ShortInsulinProgramElement{" +
            "numberOfSlotsMinusOne=" + numberOfSlots +
            ", pulsesPerSlot=" + pulsesPerSlot +
            ", extraAlternatePulse=" + extraAlternatePulse +
            '}'
    }
}
