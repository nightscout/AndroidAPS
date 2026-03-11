package app.aaps.pump.apex.connectivity.commands.pump

import app.aaps.pump.apex.utils.getUnsignedShort

class BasalProfile(command: PumpCommand): PumpObjectModel() {
    /** Basal profile index */
    val index = command.objectData[1].toUByte().toInt()

    /** Basal profile rates, in 0.025U steps, for every 30 minutes */
    val rates: List<Int> = List(48) {
        getUnsignedShort(command.objectData, 2 + it * 2)
    }
}
