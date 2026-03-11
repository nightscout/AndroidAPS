package app.aaps.pump.apex.connectivity.commands.pump

import app.aaps.pump.apex.utils.getUnsignedShort
import app.aaps.pump.apex.utils.hexAsDecToDec
import org.joda.time.DateTime

class TDDEntry(command: PumpCommand): PumpObjectModel() {
    /** TDD entry index */
    val index = command.objectData[1].toUByte().toInt()

    /** Bolus part of TDD */
    val bolus = getUnsignedShort(command.objectData, 2)

    /** Basal part of TDD */
    val basal = getUnsignedShort(command.objectData, 4)

    /** Temporary basal part of TDD */
    val temporaryBasal = getUnsignedShort(command.objectData, 6)

    /** TDD */
    val total = bolus + basal + temporaryBasal

    /** TDD entry date */
    val dateTime = DateTime(
        command.objectData[8].hexAsDecToDec() + 2000,
        command.objectData[9].hexAsDecToDec(),
        command.objectData[10].hexAsDecToDec(),
        0, 0
    )
}
