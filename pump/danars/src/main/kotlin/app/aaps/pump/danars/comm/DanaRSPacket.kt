package app.aaps.pump.danars.comm

import app.aaps.pump.danars.encryption.BleEncryption
import org.joda.time.DateTime
import org.joda.time.IllegalInstantException
import java.nio.charset.StandardCharsets

open class DanaRSPacket() {

    var isReceived = false
        private set
    var failed = false
    var type = BleEncryption.DANAR_PACKET__TYPE_RESPONSE // most of the messages, should be changed for others
        protected set
    var opCode = 0
        protected set

    fun success(): Boolean = !failed

    fun setReceived() {
        isReceived = true
    }

    val command: Int
        get() = (type and 0xFF shl 8) + (opCode and 0xFF)

    open fun getRequestParams(): ByteArray = ByteArray(0)

    fun getCommand(data: ByteArray): Int {
        val type = byteArrayToInt(getBytes(data, TYPE_START, 1))
        val opCode = byteArrayToInt(getBytes(data, OPCODE_START, 1))
        return (type and 0xFF shl 8) + (opCode and 0xFF)
    }

    open fun handleMessage(data: ByteArray) {}
    open fun handleMessageNotReceived() {
        failed = true
    }

    open val friendlyName: String = "UNKNOWN_PACKET"

    protected fun getBytes(data: ByteArray, srcStart: Int, srcLength: Int): ByteArray {
        val ret = ByteArray(srcLength)
        System.arraycopy(data, srcStart, ret, 0, srcLength)
        return ret
    }

    fun dateFromBuff(buff: ByteArray, offset: Int): Long =
        DateTime(
            2000 + byteArrayToInt(getBytes(buff, offset, 1)),
            byteArrayToInt(getBytes(buff, offset + 1, 1)),
            byteArrayToInt(getBytes(buff, offset + 2, 1)),
            0,
            0
        ).millis

    protected fun byteArrayToInt(b: ByteArray): Int =
        when (b.size) {
            1    -> b[0].toInt() and 0xFF
            2    -> (b[1].toInt() and 0xFF shl 8) + (b[0].toInt() and 0xFF)
            3    -> (b[2].toInt() and 0xFF shl 16) + (b[1].toInt() and 0xFF shl 8) + (b[0].toInt() and 0xFF)
            4    -> (b[3].toInt() and 0xFF shl 24) + (b[2].toInt() and 0xFF shl 16) + (b[1].toInt() and 0xFF shl 8) + (b[0].toInt() and 0xFF)
            else -> -1
        }

    @Synchronized
    fun dateTimeSecFromBuff(buff: ByteArray, offset: Int): Long =
        try {
            DateTime(
                2000 + intFromBuff(buff, offset, 1),
                intFromBuff(buff, offset + 1, 1),
                intFromBuff(buff, offset + 2, 1),
                intFromBuff(buff, offset + 3, 1),
                intFromBuff(buff, offset + 4, 1),
                intFromBuff(buff, offset + 5, 1)
            ).millis
        } catch (_: IllegalInstantException) {
            // expect
            // org.joda.time.IllegalInstantException: Illegal instant due to time zone offset transition (daylight savings time 'gap')
            // add 1 hour
            DateTime(
                2000 + intFromBuff(buff, offset, 1),
                intFromBuff(buff, offset + 1, 1),
                intFromBuff(buff, offset + 2, 1),
                intFromBuff(buff, offset + 3, 1) + 1,
                intFromBuff(buff, offset + 4, 1),
                intFromBuff(buff, offset + 5, 1)
            ).millis
        }

    protected fun intFromBuff(b: ByteArray, srcStart: Int, srcLength: Int): Int =
        when (srcLength) {
            1    -> b[DATA_START + srcStart + 0].toInt() and 0xFF
            2    -> (b[DATA_START + srcStart + 1].toInt() and 0xFF shl 8) + (b[DATA_START + srcStart + 0].toInt() and 0xFF)
            3    -> (b[DATA_START + srcStart + 2].toInt() and 0xFF shl 16) + (b[DATA_START + srcStart + 1].toInt() and 0xFF shl 8) + (b[DATA_START + srcStart + 0].toInt() and 0xFF)
            4    -> (b[DATA_START + srcStart + 3].toInt() and 0xFF shl 24) + (b[DATA_START + srcStart + 2].toInt() and 0xFF shl 16) + (b[DATA_START + srcStart + 1].toInt() and 0xFF shl 8) + (b[DATA_START + srcStart + 0].toInt() and 0xFF)
            else -> -1
        }

    protected fun intFromBuffMsbLsb(b: ByteArray, srcStart: Int, srcLength: Int): Int =
        when (srcLength) {
            1    -> b[DATA_START + srcStart].toInt() and 0xFF
            2    -> (b[DATA_START + srcStart].toInt() and 0xFF shl 8) + (b[DATA_START + srcStart + 1].toInt() and 0xFF)
            3    -> (b[DATA_START + srcStart].toInt() and 0xFF shl 16) + (b[DATA_START + srcStart + 1].toInt() and 0xFF shl 8) + (b[DATA_START + srcStart + 2].toInt() and 0xFF)
            4    -> (b[DATA_START + srcStart].toInt() and 0xFF shl 24) + (b[DATA_START + srcStart + 1].toInt() and 0xFF shl 16) + (b[DATA_START + srcStart + 2].toInt() and 0xFF shl 8) + (b[DATA_START + srcStart + 3].toInt() and 0xFF)
            else -> -1
        }

    fun stringFromBuff(buff: ByteArray, offset: Int, length: Int): String {
        val stringBuff = ByteArray(length)
        System.arraycopy(buff, offset, stringBuff, 0, length)
        return String(stringBuff, StandardCharsets.UTF_8)
    }

    companion object {

        private const val TYPE_START = 0
        private const val OPCODE_START = 1
        const val DATA_START = 2

        fun asciiStringFromBuff(buff: ByteArray, offset: Int, length: Int): String {
            val stringBuff = ByteArray(length)
            System.arraycopy(buff, offset, stringBuff, 0, length)
            return String(stringBuff, StandardCharsets.UTF_8)
        }

        fun toHexString(buff: ByteArray?): String {
            if (buff == null) return "null"
            val sb = StringBuilder()
            for ((count, element) in buff.withIndex()) {
                sb.append(String.format("%02X ", element))
                if ((count + 1) % 4 == 0) sb.append(" ")
            }
            return sb.toString()
        }

        private val hexArray = "0123456789ABCDEF".toCharArray()
        fun bytesToHex(bytes: ByteArray): String {
            val hexChars = CharArray(bytes.size * 2)
            for (j in bytes.indices) {
                val v: Int = bytes[j].toInt() and 0xFF
                hexChars[j * 2] = hexArray[v ushr 4]
                hexChars[j * 2 + 1] = hexArray[v and 0x0F]
            }
            return String(hexChars)
        }

        fun hexToBytes(s: String): ByteArray {
            val len = s.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] = ((Character.digit(s[i], 16) shl 4)
                    + Character.digit(s[i + 1], 16)).toByte()
                i += 2
            }
            return data
        }
    }
}