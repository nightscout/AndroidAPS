package info.nightscout.androidaps.utils.extensions

import info.nightscout.androidaps.interfaces.PumpInterface
import info.nightscout.androidaps.utils.DecimalFormatter

fun Double.toSignedString(pump: PumpInterface): String {
    val formatted = DecimalFormatter.toPumpSupportedBolus(this, pump)
    return if (this > 0) "+$formatted" else formatted
}

