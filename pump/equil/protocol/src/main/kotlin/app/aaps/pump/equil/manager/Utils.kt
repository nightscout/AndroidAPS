package app.aaps.pump.equil.manager

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.SecureRandom
import java.util.Locale

object Utils {

    fun generateRandomPassword(length: Int): ByteArray {
        val secureRandom = SecureRandom()
        val password = ByteArray(length)
        secureRandom.nextBytes(password)
        return password
    }

    fun bytesToInt(highByte: Byte, lowByte: Byte): Int {
        val highValue = (highByte.toInt() and 0xFF) shl 8
        val lowValue = lowByte.toInt() and 0xFF
        val value = highValue or lowValue
        if (value >= 0x8000) {
            return value - 0x8000
        }
        return value
    }

    fun internalDecodeSpeedToUH(i: Int): Float = BigDecimal(i).multiply(BigDecimal("0.00625")).toFloat()
    fun internalDecodeSpeedToUH2(i: Int): BigDecimal = BigDecimal(i).multiply(BigDecimal("0.00625"))
    fun decodeSpeedToUH(i: Int): Float = BigDecimal(i).multiply(BigDecimal("0.00625")).toFloat()
    fun decodeSpeedToUS(i: Int): Double = internalDecodeSpeedToUH2(i).divide(BigDecimal("3600"), 10, RoundingMode.DOWN).toDouble()
    fun decodeSpeedToUH(i: Double): Int {
        val a = BigDecimal(i.toString())
        val b = BigDecimal("0.00625")
        val c = a.divide(b)
        return c.toInt()
    }

    fun decodeSpeedToUHT(i: Double): Double {
        val a = BigDecimal(i.toString())
        val b = BigDecimal("0.00625")
        return a.divide(b).toDouble()
    }

    fun basalToByteArray(v: Double): ByteArray {
        val value = decodeSpeedToUH(v)
        val result = ByteArray(2)
        result[0] = ((value shr 8) and 0xFF).toByte() // 高位
        result[1] = (value and 0xFF).toByte() // 低位
        return result
    }

    fun basalToByteArray2(v: Double): ByteArray {
        val value = decodeSpeedToUH(v)
        val result = ByteArray(2)
        result[1] = ((value shr 8) and 0xFF).toByte() // 高位
        result[0] = (value and 0xFF).toByte() // 低位
        return result
    }

    private fun charToByte(c: Char): Byte {
        return "0123456789ABCDEF".indexOf(c).toByte()
    }

    fun hexStringToBytes(hex: String): ByteArray {
        val hexString = hex.uppercase(Locale.getDefault())
        val length = hexString.length / 2
        val hexChars = hexString.toCharArray()
        val d = ByteArray(length)
        for (i in 0 until length) {
            val pos = i * 2
            d[i] = (charToByte(hexChars[pos]).toInt() shl 4 or charToByte(hexChars[pos + 1]).toInt()).toByte()
        }
        return d
    }

    fun concat(vararg arrays: ByteArray): ByteArray {
        var length = 0
        for (array in arrays) {
            length += array.size
        }
        val result = ByteArray(length)
        var pos = 0
        for (array in arrays) {
            System.arraycopy(array, 0, result, pos, array.size)
            pos += array.size
        }
        return result
    }

    fun intToBytes(value: Int): ByteArray {
        val src = ByteArray(4)
        src[3] = ((value shr 24) and 0xFF).toByte()
        src[2] = ((value shr 16) and 0xFF).toByte()
        src[1] = ((value shr 8) and 0xFF).toByte()
        src[0] = (value and 0xFF).toByte()
        return src
    }

    fun bytes2Int(bytes: ByteArray): Int {
        val int1 = bytes[0].toInt() and 0xff
        val int2 = (bytes[1].toInt() and 0xff) shl 8
        val int3 = (bytes[2].toInt() and 0xff) shl 16
        val int4 = (bytes[3].toInt() and 0xff) shl 24
        return int1 or int2 or int3 or int4
    }

    fun intToTwoBytes(value: Int): ByteArray {
        val bytes = ByteArray(2)
        bytes[1] = ((value shr 8) and 0xFF).toByte()
        bytes[0] = (value and 0xFF).toByte()
        return bytes
    }

    fun convertByteArray(byteList: MutableList<Byte?>): ByteArray {
        val byteArray = ByteArray(byteList.size)
        for (i in byteList.indices) {
            byteArray[i] = byteList.get(i)!!
        }
        return byteArray
    }

    private val hexArray = "0123456789ABCDEF".toCharArray()

    fun bytesToHex(bytes: MutableList<Byte?>?): String {
        if (bytes == null) return "<empty>"
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes.get(j)!!.toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    fun bytesToHex(bytes: ByteArray?): String {
        if (bytes == null) return "<empty>"
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }
}
