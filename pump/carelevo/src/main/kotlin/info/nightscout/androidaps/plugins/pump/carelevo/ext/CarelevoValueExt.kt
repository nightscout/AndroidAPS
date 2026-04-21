package info.nightscout.androidaps.plugins.pump.carelevo.ext

import java.math.BigInteger
import java.util.Locale
import kotlin.experimental.xor

internal fun ByteArray.convertBytesToHex() : String {
    return StringBuilder().let {
        for(byte in this) {
            it.append(String.format("0x%02x", byte))
        }
        it
    }.toString()
}

internal fun String.convertHexToByteArray() : ByteArray {
    var returnData : List<Byte> = ArrayList()

    val str = toString().replace(" ", "")
    var hex = str.replace("0x", "")

    hex = hex.uppercase(Locale.getDefault())

    val intCount = (hex.length % 2) == 0
    if(intCount) {
        try {
            returnData = ArrayList()
            for (i in 0..hex.length step 2) {
                if(i < hex.length) {
                    val subString = hex.substring(i until i + 2)
                    returnData.add(BigInteger(subString, 16).toByte())
                }
            }
        } catch (e : Exception) {
            e.printStackTrace()
        }
    }

    return returnData.toByteArray()
}

internal fun ByteArray.checkSum(key : Int, result : Int) : Boolean {
    val checkResult = this.fold(key.toByte()) { acc, checkByte ->
        acc xor checkByte
    }
    return checkResult == result.toByte()
}

internal fun ByteArray.checkSumV2(key : Int) : Byte {
    val checkResult = this.fold(key.toByte()) { acc, checkByte ->
        acc xor checkByte
    }
    return checkResult
}