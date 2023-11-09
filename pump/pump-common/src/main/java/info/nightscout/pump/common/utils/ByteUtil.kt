package info.nightscout.pump.common.utils

import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.min

/**
 * Created by geoff on 4/28/15.
 */
object ByteUtil {

    private val HEX_DIGITS = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    )
    private const val HEX_DIGITS_STR = "0123456789ABCDEF"

    /** @noinspection SpellCheckingInspection
     */
    fun asUINT8(b: Byte): Int {
        return if (b < 0) b + 256 else b.toInt()
    }

    fun getBytesFromInt16(value: Int): ByteArray {
        val array = getBytesFromInt(value)
        return byteArrayOf(array[2], array[3])
    }

    fun getBytesFromInt(value: Int): ByteArray {
        return ByteBuffer.allocate(4).putInt(value).array()
    }

    /* For Reference: static void System.arraycopy(Object src, int srcPos, Object dest, int destPos, int length) */
    fun concat(a: ByteArray, b: ByteArray?): ByteArray {
        if (b == null) {
            return a
        }
        val aLen = a.size
        val bLen = b.size
        val c = ByteArray(aLen + bLen)
        System.arraycopy(a, 0, c, 0, aLen)
        System.arraycopy(b, 0, c, aLen, bLen)
        return c
    }

    fun concat(a: ByteArray, b: Byte): ByteArray {
        val aLen = a.size
        val c = ByteArray(aLen + 1)
        System.arraycopy(a, 0, c, 0, aLen)
        c[aLen] = b
        return c
    }

    fun substring(a: ByteArray, start: Int, len: Int): ByteArray {
        val rVal = ByteArray(len)
        System.arraycopy(a, start, rVal, 0, len)
        return rVal
    }

    fun substring(a: List<Byte>, start: Int, len: Int): ByteArray {
        val rVal = ByteArray(len)
        var i = start
        var j = 0
        while (i < start + len) {
            rVal[j] = a[i]
            i++
            j++
        }
        return rVal
    }

    fun substring(a: ByteArray, start: Int): ByteArray {
        val len = a.size - start
        val rVal = ByteArray(len)
        System.arraycopy(a, start, rVal, 0, len)
        return rVal
    }

    fun shortHexString(ra: ByteArray?): String {
        val rVal = StringBuilder()
        if (ra == null) {
            return rVal.toString()
        }
        if (ra.isEmpty()) {
            return rVal.toString()
        }
        for (i in ra.indices) {
            rVal.append(HEX_DIGITS[ra[i].toInt() and 0xF0 shr 4])
            rVal.append(HEX_DIGITS[ra[i].toInt() and 0x0F])
            if (i < ra.size - 1) {
                rVal.append(" ")
            }
        }
        return rVal.toString()
    }

    fun shortHexString(list: List<Byte>): String {
        val aByte0 = getByteArrayFromList(list)
        return shortHexString(aByte0)
    }

    fun shortHexString(`val`: Byte): String {
        return getHexCompact(`val`)
    }

    fun shortHexStringWithoutSpaces(byteArray: ByteArray?): String {
        val hexString = StringBuilder()
        if (byteArray == null) {
            return hexString.toString()
        }
        for (b in byteArray) {
            hexString.append(HEX_DIGITS[b.toInt() and 0xF0 shr 4])
            hexString.append(HEX_DIGITS[b.toInt() and 0x0F])
        }
        return hexString.toString()
    }

    fun fromHexString(src: String): ByteArray? {
        val s = src.uppercase(Locale.getDefault())
        var rVal = byteArrayOf()
        if (s.length % 2 != 0) {
            // invalid hex string!
            return null
        }
        var i = 0
        while (i < s.length) {
            val highNibbleOrd = HEX_DIGITS_STR.indexOf(s[i])
            if (highNibbleOrd < 0) {
                // Not a hex digit.
                return null
            }
            val lowNibbleOrd = HEX_DIGITS_STR.indexOf(s[i + 1])
            if (lowNibbleOrd < 0) {
                // Not a hex digit
                return null
            }
            rVal = concat(rVal, (highNibbleOrd * 16 + lowNibbleOrd).toByte())
            i += 2
        }
        return rVal
    }

    fun getListFromByteArray(array: ByteArray): List<Byte> {
        val listOut: MutableList<Byte> = ArrayList()
        for (`val` in array) {
            listOut.add(`val`)
        }
        return listOut
    }

    fun getByteArrayFromList(list: List<Byte>): ByteArray {
        val out = ByteArray(list.size)
        for (i in list.indices) {
            out[i] = list[i]
        }
        return out
    }

    // compares byte strings like strcmp
    fun compare(s1: ByteArray, s2: ByteArray): Int {
        val len1 = s1.size
        val len2 = s2.size
        if (len1 > len2) {
            return 1
        }
        if (len2 > len1) {
            return -1
        }
        var acc = 0
        var i = 0
        while (i < len1) {
            acc += s1[i].toInt()
            acc -= s2[i].toInt()
            if (acc != 0) {
                return acc
            }
            i++
        }
        return 0
    }

    /**
     * Converts 4 (or less) ints into int. (Shorts are objects, so you can send null if you have less parameters)
     *
     * @param b1   short 1
     * @param b2   short 2
     * @param b3   short 3
     * @param b4   short 4
     * @param flag Conversion Flag (Big Endian, Little endian)
     * @return int value
     */
    fun toInt(b1: Int, b2: Int?, b3: Int?, b4: Int?, flag: BitConversion?): Int {
        return when (flag) {
            BitConversion.LITTLE_ENDIAN -> {
                if (b4 != null) {
                    b4 and 0xff shl 24 or (b3!! and 0xff shl 16) or (b2!! and 0xff shl 8) or (b1 and 0xff)
                } else if (b3 != null) {
                    b3 and 0xff shl 16 or (b2!! and 0xff shl 8) or (b1 and 0xff)
                } else if (b2 != null) {
                    b2 and 0xff shl 8 or (b1 and 0xff)
                } else {
                    b1 and 0xff
                }
            }

            BitConversion.BIG_ENDIAN    -> {
                if (b4 != null) {
                    b1 and 0xff shl 24 or (b2!! and 0xff shl 16) or (b3!! and 0xff shl 8) or (b4 and 0xff)
                } else if (b3 != null) {
                    b1 and 0xff shl 16 or (b2!! and 0xff shl 8) or (b3 and 0xff)
                } else if (b2 != null) {
                    b1 and 0xff shl 8 or (b2 and 0xff)
                } else {
                    b1 and 0xff
                }
            }

            else                        -> {
                if (b4 != null) {
                    b1 and 0xff shl 24 or (b2!! and 0xff shl 16) or (b3!! and 0xff shl 8) or (b4 and 0xff)
                } else if (b3 != null) {
                    b1 and 0xff shl 16 or (b2!! and 0xff shl 8) or (b3 and 0xff)
                } else if (b2 != null) {
                    b1 and 0xff shl 8 or (b2 and 0xff)
                } else {
                    b1 and 0xff
                }
            }
        }
    }

    /**
     * Converts 4 (or less) ints into int. (Shorts are objects, so you can send null if you have less parameters)
     *
     * @param b1   short 1
     * @param b2   short 2
     * @param b3   short 3
     * @param b4   short 4
     * @param flag Conversion Flag (Big Endian, Little endian)
     * @return int value
     */
    fun toInt(b1: Byte, b2: Byte?, b3: Byte?, b4: Byte?, flag: BitConversion?): Int {
        return when (flag) {
            BitConversion.LITTLE_ENDIAN -> {
                if (b4 != null) {
                    b4.toInt() and 0xff shl 24 or (b3!!.toInt() and 0xff shl 16) or (b2!!.toInt() and 0xff shl 8) or (b1.toInt() and 0xff)
                } else if (b3 != null) {
                    b3.toInt() and 0xff shl 16 or (b2!!.toInt() and 0xff shl 8) or (b1.toInt() and 0xff)
                } else if (b2 != null) {
                    b2.toInt() and 0xff shl 8 or (b1.toInt() and 0xff)
                } else {
                    b1.toInt() and 0xff
                }
            }

            BitConversion.BIG_ENDIAN    -> {
                if (b4 != null) {
                    b1.toInt() and 0xff shl 24 or (b2!!.toInt() and 0xff shl 16) or (b3!!.toInt() and 0xff shl 8) or (b4.toInt() and 0xff)
                } else if (b3 != null) {
                    b1.toInt() and 0xff shl 16 or (b2!!.toInt() and 0xff shl 8) or (b3.toInt() and 0xff)
                } else if (b2 != null) {
                    b1.toInt() and 0xff shl 8 or (b2.toInt() and 0xff)
                } else {
                    b1.toInt() and 0xff
                }
            }

            else                        -> {
                if (b4 != null) {
                    b1.toInt() and 0xff shl 24 or (b2!!.toInt() and 0xff shl 16) or (b3!!.toInt() and 0xff shl 8) or (b4.toInt() and 0xff)
                } else if (b3 != null) {
                    b1.toInt() and 0xff shl 16 or (b2!!.toInt() and 0xff shl 8) or (b3.toInt() and 0xff)
                } else if (b2 != null) {
                    b1.toInt() and 0xff shl 8 or (b2.toInt() and 0xff)
                } else {
                    b1.toInt() and 0xff
                }
            }
        }
    }

    fun toInt(b1: Int, b2: Int): Int {
        return toInt(b1, b2, null, null, BitConversion.BIG_ENDIAN)
    }

    fun toInt(b1: Int, b2: Int, flag: BitConversion?): Int {
        return toInt(b1, b2, null, null, flag)
    }

    fun toInt(b1: Byte, b2: Byte): Int {
        return toInt(b1, b2, null, null, BitConversion.BIG_ENDIAN)
    }

    fun toInt(b1: Int, b2: Int, b3: Int): Int {
        return toInt(b1, b2, b3, null, BitConversion.BIG_ENDIAN)
    }

    fun toInt(b1: Byte, b2: Byte, b3: Byte): Int {
        return toInt(b1, b2, b3, null, BitConversion.BIG_ENDIAN)
    }

    fun makeUnsignedShort(i: Int, j: Int): Int {
        return i and 0xff shl 8 or (j and 0xff)
    }

    fun getCorrectHexValue(inp: Byte): String? {
        val hx = Integer.toHexString(Char(inp.toUShort()).code)
        when (hx.length) {
            1    -> return "0$hx"
            2    -> return hx
            4    -> return hx.substring(2)

            else -> {
                println("Hex Error: $inp")
            }
        }
        return null
    }

    fun getHex(aByte0: ByteArray?): String? {
        return if (aByte0 != null) getHex(aByte0, aByte0.size) else null
    }

    fun getHex(aByte0: ByteArray?, i: Int): String {
        var index = i
        val sb = StringBuilder()
        if (aByte0 != null) {
            index = min(index, aByte0.size)
            for (j in 0 until index) {
                sb.append(shortHexString(aByte0[j]))
                if (j < index - 1) {
                    sb.append(" ")
                }
            }
        }
        return sb.toString()
    }

    fun getHex(list: List<Byte>): String {
        val aByte0 = getByteArrayFromList(list)
        return getHex(aByte0, aByte0.size)
    }

    fun getHex(byte0: Byte): String {
        val s = if (byte0.toInt() != -1) "0x" else ""
        return s + getHexCompact(byte0)
    }

    fun getHexCompact(byte0: Byte): String {
        val i = if (byte0.toInt() != -1) convertUnsignedByteToInt(byte0) else byte0.toInt()
        return getHexCompact(i)
    }

    fun convertUnsignedByteToInt(data: Byte): Int {
        return data.toInt() and 0xff
    }

    fun getHexCompact(l: Int): String {
        val s = java.lang.Long.toHexString(l.toLong()).uppercase(Locale.getDefault())
        val s1 = if (isOdd(s.length)) "0" else ""
        return if (l.toLong() != -1L) s1 + s else "-1"
    }

    fun isEven(i: Int): Boolean {
        return i % 2 == 0
    }

    fun isOdd(i: Int): Boolean {
        return !isEven(i)
    }

    // 00 03 00 05 01 00 C8 00 A0
    fun createByteArrayFromString(dataFull: String): ByteArray {
        val data = dataFull.replace(" ", "")
        return createByteArrayFromCompactString(data, 0, data.length)
    }

    fun createByteArrayFromHexString(dataFull: String): ByteArray {
        var data = dataFull.replace(" 0x", "")
        data = data.replace("0x", "")
        return createByteArrayFromCompactString(data, 0, data.length)
    }

    // 000300050100C800A0
    @JvmOverloads
    fun createByteArrayFromCompactString(dataFull: String, startIndex: Int = 0, length: Int = dataFull.length): ByteArray {
        var data = dataFull.substring(startIndex)
        data = data.substring(0, length)
        val len = data.length
        val outArray = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            outArray[i / 2] = ((data[i].digitToInt(16) shl 4) + data[i + 1].digitToInt(16)).toByte()
            i += 2
        }
        return outArray
    }

    enum class BitConversion {
        LITTLE_ENDIAN,  // 20 0 0 0 = reverse
        BIG_ENDIAN // 0 0 0 20 = normal - java
    }
}