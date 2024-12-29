package app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program

import java.io.Serializable

class CurrentSlot(
    val index: Byte,
    val eighthSecondsRemaining: Short,
    val pulsesRemaining: Short
) : Serializable {

    override fun toString(): String = "CurrentSlot{" +
        "index=" + index +
        ", eighthSecondsRemaining=" + eighthSecondsRemaining +
        ", pulsesRemaining=" + pulsesRemaining +
        '}'
}
