package info.nightscout.ui.extensions

import info.nightscout.interfaces.pump.Pump
import info.nightscout.interfaces.utils.DecimalFormatter

fun Double.toSignedString(pump: Pump, decimalFormatter: DecimalFormatter): String {
    val formatted = decimalFormatter.toPumpSupportedBolus(this, pump.pumpDescription.bolusStep)
    return if (this > 0) "+$formatted" else formatted
}

