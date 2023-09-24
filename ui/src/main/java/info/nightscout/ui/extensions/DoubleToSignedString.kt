package info.nightscout.ui.extensions

import app.aaps.interfaces.pump.Pump
import app.aaps.interfaces.utils.DecimalFormatter

fun Double.toSignedString(pump: Pump, decimalFormatter: DecimalFormatter): String {
    val formatted = decimalFormatter.toPumpSupportedBolus(this, pump.pumpDescription.bolusStep)
    return if (this > 0) "+$formatted" else formatted
}

