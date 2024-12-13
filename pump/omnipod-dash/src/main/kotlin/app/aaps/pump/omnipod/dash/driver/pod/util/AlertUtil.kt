package app.aaps.pump.omnipod.dash.driver.pod.util

import app.aaps.pump.omnipod.dash.driver.pod.definition.AlertType
import java.util.*

object AlertUtil {

    fun decodeAlertSet(encoded: Byte): EnumSet<AlertType> {
        val encodedInt = encoded.toInt() and 0xff

        val alertList = AlertType.entries
            .filter { it != AlertType.UNKNOWN } // 0xff && <something> will always be true
            .filter { (it.value.toInt() and 0xff) and encodedInt != 0 }
            .toList()

        return if (alertList.isEmpty()) {
            EnumSet.noneOf(AlertType::class.java)
        } else {
            EnumSet.copyOf(alertList)
        }
    }

    fun encodeAlertSet(alertSet: EnumSet<AlertType>): Byte =
        alertSet.fold(0) { out, slot ->
            out or (slot.value.toInt() and 0xff)
        }.toByte()
}
