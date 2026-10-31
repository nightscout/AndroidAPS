package app.aaps.core.utils

private val HEX_CHARS = "0123456789abcdef".toCharArray()

fun ByteArray.toHex(): String {
    // StringBuilder, not StringBuffer: StringBuffer is a JVM class and its synchronization bought
    // nothing here - the builder never leaves this function.
    val result = StringBuilder()

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

    // Locale independent lowercase. The previous `lowercase(Locale.getDefault())` made hex parsing
    // depend on the phone's language, which is never what a wire format wants.
    val lowerCased = this.lowercase()
    for (i in indices step 2) {
        val firstIndex = HEX_CHARS.indexOf(lowerCased[i])
        val secondIndex = HEX_CHARS.indexOf(lowerCased[i + 1])

        val octet = firstIndex.shl(4).or(secondIndex)
        result[i.shr(1)] = octet.toByte()
    }

    return result
}
