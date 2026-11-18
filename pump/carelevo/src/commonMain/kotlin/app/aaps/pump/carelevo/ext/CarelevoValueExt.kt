package app.aaps.pump.carelevo.ext

import kotlin.experimental.xor

internal fun ByteArray.convertBytesToHex(): String = joinToString(separator = "") { it.toHexText() }

/** One byte as `0x` and two lower case hex digits, as `String.format("0x%02x", byte)` wrote it. */
internal fun Byte.toHexText(): String = "0x" + (toInt() and 0xFF).toString(16).padStart(2, '0')

internal fun String.convertHexToByteArray(): ByteArray {
    var returnData: List<Byte> = ArrayList()

    val str = replace(" ", "")
    var hex = str.replace("0x", "")

    hex = hex.uppercase()

    val intCount = (hex.length % 2) == 0
    if (intCount) {
        try {
            returnData = ArrayList()
            for (i in 0..hex.length step 2) {
                if (i < hex.length) {
                    val subString = hex.substring(i until i + 2)
                    returnData.add(subString.toInt(16).toByte())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    return returnData.toByteArray()
}

internal fun ByteArray.checkSum(key: Int, result: Int): Boolean {
    val checkResult = this.fold(key.toByte()) { acc, checkByte ->
        acc xor checkByte
    }
    return checkResult == result.toByte()
}

internal fun ByteArray.checkSumV2(key: Int): Byte {
    val checkResult = this.fold(key.toByte()) { acc, checkByte ->
        acc xor checkByte
    }
    return checkResult
}