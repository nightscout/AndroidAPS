package app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program

import java.nio.ByteBuffer

class TempBasalInsulinProgramElement(
    startSlotIndex: Byte,
    numberOfSlots: Byte,
    totalTenthPulses: Short
) : BasalInsulinProgramElement(startSlotIndex, numberOfSlots, totalTenthPulses) {

    override val encoded: ByteArray
        get() {
            val buffer = ByteBuffer.allocate(6)
            if (totalTenthPulses.toInt() == 0) {
                val i = (durationInSeconds.toDouble() * 1000000.0 / numberOfSlots.toDouble()).toInt() or Int.MIN_VALUE
                buffer.putShort(numberOfSlots.toShort())
                    .putInt(i)
            } else {
                buffer.putShort(totalTenthPulses)
                    .putInt(delayBetweenTenthPulsesInUsec)
            }
            return buffer.array()
        }
}
