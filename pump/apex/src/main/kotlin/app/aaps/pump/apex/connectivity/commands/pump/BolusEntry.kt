package app.aaps.pump.apex.connectivity.commands.pump

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.apex.R
import app.aaps.pump.apex.utils.getUnsignedShort
import app.aaps.pump.apex.utils.hexAsDecToDec
import org.joda.time.DateTime

class BolusEntry(command: PumpCommand): PumpObjectModel() {
    /** Bolus entry index */
    val index = command.objectData[1].toUByte().toInt()

    /** Bolus date */
    val dateTime = DateTime(
        command.objectData[2].hexAsDecToDec() + 2000, // year
        command.objectData[3].hexAsDecToDec(), // day
        command.objectData[4].hexAsDecToDec(), // month
        command.objectData[5].hexAsDecToDec(), // hour
        command.objectData[6].hexAsDecToDec(), // minute
        command.objectData[7].hexAsDecToDec(), // second
    )

    /** Standard bolus requested dose */
    val standardDose = getUnsignedShort(command.objectData, 8)

    /** Standard bolus actual dose */
    val standardPerformed = getUnsignedShort(command.objectData, 10)

    /** Extended bolus requested dose */
    val extendedDose = getUnsignedShort(command.objectData, 12)

    /** Extended bolus actual dose */
    val extendedPerformed = getUnsignedShort(command.objectData, 14)

    fun toShortLocalString(rh: ResourceHelper): String {
        val diff = System.currentTimeMillis() - dateTime.millis
        if (diff >= 60 * 60 * 1000) {
            return rh.gs(R.string.overview_pump_last_bolus_h, standardPerformed * 0.025, diff / 60 / 60 / 1000, (diff / 60 / 1000) % 60)
        } else {
            return rh.gs(R.string.overview_pump_last_bolus_min, standardPerformed * 0.025, diff / 60 / 1000)
        }
    }
}
