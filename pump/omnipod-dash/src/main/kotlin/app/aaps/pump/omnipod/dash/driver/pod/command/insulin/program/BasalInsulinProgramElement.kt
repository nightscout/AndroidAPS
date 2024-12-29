package app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program

import app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program.util.ProgramBasalUtil
import app.aaps.pump.omnipod.dash.driver.pod.definition.Encodable
import java.io.Serializable
import java.nio.ByteBuffer

open class BasalInsulinProgramElement(
    val startSlotIndex: Byte,
    val numberOfSlots: Byte,
    val totalTenthPulses: Short
) : Encodable, Serializable {

    override val encoded: ByteArray
        get() = ByteBuffer.allocate(6)
            .putShort(totalTenthPulses)
            .putInt(if (totalTenthPulses.toInt() == 0) Int.MIN_VALUE or delayBetweenTenthPulsesInUsec else delayBetweenTenthPulsesInUsec)
            .array()
    val durationInSeconds: Short
        get() = (numberOfSlots * 1800).toShort()
    val delayBetweenTenthPulsesInUsec: Int
        get() = if (totalTenthPulses.toInt() == 0) {
            ProgramBasalUtil.MAX_DELAY_BETWEEN_TENTH_PULSES_IN_USEC_AND_USECS_IN_BASAL_SLOT
        } else (ProgramBasalUtil.MAX_DELAY_BETWEEN_TENTH_PULSES_IN_USEC_AND_USECS_IN_BASAL_SLOT.toLong() * numberOfSlots / totalTenthPulses.toDouble()).toInt()

    override fun toString(): String {
        return "LongInsulinProgramElement{" +
            "startSlotIndex=" + startSlotIndex +
            ", numberOfSlots=" + numberOfSlots +
            ", totalTenthPulses=" + totalTenthPulses +
            ", delayBetweenTenthPulsesInUsec=" + delayBetweenTenthPulsesInUsec +
            '}'
    }
}
