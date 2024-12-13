package app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program

import java.io.Serializable

class CurrentBasalInsulinProgramElement(
    val index: Byte,
    val delayUntilNextTenthPulseInUsec: Int,
    val remainingTenthPulses: Short
) : Serializable {

    override fun toString(): String = "CurrentLongInsulinProgramElement{" +
        "index=" + index +
        ", delayUntilNextTenthPulseInUsec=" + delayUntilNextTenthPulseInUsec +
        ", remainingTenthPulses=" + remainingTenthPulses +
        '}'
}
