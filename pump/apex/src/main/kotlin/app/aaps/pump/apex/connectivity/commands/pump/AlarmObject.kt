package app.aaps.pump.apex.connectivity.commands.pump

import app.aaps.pump.apex.utils.getUnsignedShort
import app.aaps.pump.apex.utils.hexAsDecToDec
import org.joda.time.DateTime

class AlarmObject(command: PumpCommand): PumpObjectModel() {
    /** Alarm entry index */
    val index = command.objectData[1].toUByte().toInt()

    /** Alarm date */
    val dateTime = DateTime(
        command.objectData[2].hexAsDecToDec() + 2000, // year
        command.objectData[3].hexAsDecToDec(), // day
        command.objectData[4].hexAsDecToDec(), // month
        command.objectData[5].hexAsDecToDec(), // hour
        command.objectData[6].hexAsDecToDec(), // minute
        command.objectData[7].hexAsDecToDec(), // second
    )

    /** Alarm type */
    val type = Alarm.entries.find { it.raw == (getUnsignedShort(command.objectData, 8) + 0x100) }
}