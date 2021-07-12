package info.nightscout.androidaps.utils.extensions

import info.nightscout.androidaps.interfaces.Pump
import info.nightscout.androidaps.utils.DecimalFormatter

fun Double.toSignedString(pump: Pump): String {
    val formatted = DecimalFormatter.toPumpSupportedBolus(this, pump)
    return if (this > 0) "+$formatted" else formatted
}

