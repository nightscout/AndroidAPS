package app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program

import java.nio.ByteBuffer

class BolusShortInsulinProgramElement(
    private val numberOfPulses: Short
) : ShortInsulinProgramElement {

    override val encoded: ByteArray
        get() = ByteBuffer.allocate(2).putShort(numberOfPulses).array()
}
