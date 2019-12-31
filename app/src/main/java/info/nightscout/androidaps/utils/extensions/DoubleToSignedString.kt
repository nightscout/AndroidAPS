package info.nightscout.androidaps.utils.extensions

import info.nightscout.androidaps.utils.DecimalFormatter

fun Double.toSignedString(): String {
    val formatted = DecimalFormatter.toPumpSupportedBolus(this)
    return if (this > 0) "+$formatted" else formatted
}

