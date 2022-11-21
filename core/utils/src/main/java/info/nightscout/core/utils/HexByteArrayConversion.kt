package info.nightscout.core.utils

import java.util.Locale

private val HEX_CHARS = "0123456789abcdef".toCharArray()

fun ByteArray.toHex() : String{
    val result = StringBuffer()

    forEach {
        val octet = it.toInt()
        val firstIndex = (octet and 0xF0).ushr(4)
        val secondIndex = octet and 0x0F
        result.append(HEX_CHARS[firstIndex])
        result.append(HEX_CHARS[secondIndex])
    }

    return result.toString()
}

fun String.hexStringToByteArray(): ByteArray {

    val result = ByteArray(length / 2)

    val lowerCased = this.lowercase(Locale.getDefault())
    for (i in indices step 2) {
        val firstIndex = HEX_CHARS.indexOf(lowerCased[i])
        val secondIndex = HEX_CHARS.indexOf(lowerCased[i + 1])

        val octet = firstIndex.shl(4).or(secondIndex)
        result[i.shr(1)] = octet.toByte()
    }

    return result
}