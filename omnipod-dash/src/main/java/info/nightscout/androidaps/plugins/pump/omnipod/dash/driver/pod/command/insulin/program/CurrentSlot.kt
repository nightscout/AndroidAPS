package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program

class CurrentSlot(val index: Byte, val eighthSecondsRemaining: Short, val pulsesRemaining: Short) {

    override fun toString(): String = "CurrentSlot{" +
        "index=" + index +
        ", eighthSecondsRemaining=" + eighthSecondsRemaining +
        ", pulsesRemaining=" + pulsesRemaining +
        '}'
}