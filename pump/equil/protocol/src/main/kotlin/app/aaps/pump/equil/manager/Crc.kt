package app.aaps.pump.equil.manager

import java.util.Locale

object Crc {

    fun crc8Maxim(source: ByteArray): Int {
        val offset = 0
        val length = source.size
        var wCRCin = 0x00
        // Integer.reverse(0x31) >>> 24
        val wCPoly = 0x8C
        var i = offset
        val cnt = offset + length
        while (i < cnt) {
            wCRCin = (wCRCin.toLong() xor (source[i].toLong() and 0xFFL)).toInt()
            (0..7).forEach { j ->
                if ((wCRCin and 0x01) != 0) {
                    wCRCin = wCRCin shr 1
                    wCRCin = wCRCin xor wCPoly
                } else {
                    wCRCin = wCRCin shr 1
                }
            }
            i++
        }
        wCRCin = wCRCin xor 0x00
        return wCRCin
    }

    fun getCRC(bytes: ByteArray): ByteArray {
        var crc = 0x0000ffff
        val polynomial = 0x0000a001
        var j: Int
        var i: Int = 0
        while (i < bytes.size) {
            crc = crc xor (bytes[i].toInt() and 0x000000ff)
            j = 0
            while (j < 8) {
                if ((crc and 0x00000001) != 0) {
                    crc = crc shr 1
                    crc = crc xor polynomial
                } else {
                    crc = crc shr 1
                }
                j++
            }
            i++
        }
        var result = Integer.toHexString(crc).uppercase(Locale.getDefault())
        if (result.length != 4) {
            val sb = StringBuffer("0000")
            result = sb.replace(4 - result.length, 4, result).toString()
        }
        return Utils.hexStringToBytes(result)
    }
}
