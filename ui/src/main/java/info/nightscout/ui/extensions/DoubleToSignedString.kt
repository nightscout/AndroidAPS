package info.nightscout.ui.extensions

import info.nightscout.interfaces.pump.Pump
import info.nightscout.interfaces.utils.DecimalFormatter

fun Double.toSignedString(pump: Pump): String {
    val formatted = DecimalFormatter.toPumpSupportedBolus(this, pump)
    return if (this > 0) "+$formatted" else formatted
}

