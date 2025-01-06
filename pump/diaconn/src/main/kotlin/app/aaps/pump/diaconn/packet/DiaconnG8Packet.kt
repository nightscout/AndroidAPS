package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import dagger.android.HasAndroidInjector
import java.lang.StringBuilder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

@Suppress("SpellCheckingInspection")
open class DiaconnG8Packet(protected val injector: HasAndroidInjector) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var dateUtil: DateUtil

    private var received = false
    var failed: Boolean = false
    var msgType: Byte = 0
    open val friendlyName = "UNKNOWN_PACKET"

    init {
        injector.androidInjector().inject(this)
    }

    fun success(): Boolean = !failed
    fun setReceived() {
        received = true
    }

    // 패킷 인코딩 앞부분
    fun prefixEncode(msgType: Byte, msgSeq: Int, msgConEnd: Byte): ByteBuffer {
        val buffer = ByteBuffer.allocate(MSG_LEN)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(SOP)
        buffer.put(msgType)
        buffer.put(msgSeq.toByte())
        buffer.put(msgConEnd)
        return buffer
    }

    // 패킷 인코딩 뒷부분
    fun suffixEncode(buffer: ByteBuffer): ByteArray {
        val remainSize = MSG_LEN - buffer.position() - 1
        (0 until remainSize).forEach { buffer.put(MSG_PAD) }
        val crc = getCRC(buffer.array(), MSG_LEN - 1)
        buffer.put(crc)
        return buffer.array()
    }

    fun getType(bytes: ByteArray): Int {
        return (bytes[MSG_TYPE_LOC].toInt() and 0xC0) shr 6
    } //상위 2비트 획득

    fun getCmd(bytes: ByteArray): Int {
        return bytes[MSG_TYPE_LOC].toInt()
    }

    fun getSeq(bytes: ByteArray): Int {
        return bytes[MSG_SEQ_LOC].toInt()
    }

    open fun encode(msgSeq: Int): ByteArray {
        return ByteArray(0)
    }

    open fun handleMessage(data: ByteArray) {
    }

    fun isSuccInquireResponseResult(result: Int): Boolean {
        var isSuccess = false
        when (result) {
            16   -> isSuccess = true
            17   -> aapsLogger.error(LTag.PUMPCOMM, "Packet CRC error")
            18   -> aapsLogger.error(LTag.PUMPCOMM, "Parameter error.")
            19   -> aapsLogger.error(LTag.PUMPCOMM, "Protocol specification error.")
            else -> aapsLogger.error(LTag.PUMPCOMM, "System error.")
        }
        return isSuccess
    }

    fun isSuccSettingResponseResult(result: Int): Boolean {
        var isSuccess = false
        when (result) {
            0    -> isSuccess = true
            1    -> aapsLogger.error(LTag.PUMPCOMM, "Packet CRC error")
            2    -> aapsLogger.error(LTag.PUMPCOMM, "Parameter error.")
            3    -> aapsLogger.error(LTag.PUMPCOMM, "Protocol specification error.")
            4    -> aapsLogger.error(LTag.PUMPCOMM, "Eating timeout, not injectable.")
            6    -> aapsLogger.error(LTag.PUMPCOMM, "Pump canceled it.")
            7    -> aapsLogger.error(LTag.PUMPCOMM, "In the midst of other operations, limited app setup capabilities")
            8    -> aapsLogger.error(LTag.PUMPCOMM, "During another bolus injection, injection is restricted")
            9    -> aapsLogger.error(LTag.PUMPCOMM, "Basal release is required.")
            10   -> aapsLogger.error(LTag.PUMPCOMM, "Canceled due to the opt number did not match.")
            11   -> aapsLogger.error(LTag.PUMPCOMM, "Injection is not possible due to low battery.")
            12   -> aapsLogger.error(LTag.PUMPCOMM, "Injection is not possible due to low insulin. ")
            13   -> aapsLogger.error(LTag.PUMPCOMM, "Can't inject due to 1 time limit exceeded.")
            14   -> aapsLogger.error(LTag.PUMPCOMM, "It cannot be injected due to an excess of injection volume today")
            15   -> aapsLogger.error(LTag.PUMPCOMM, "After base setting is completed, base injection can be made.")
            32   -> aapsLogger.error(LTag.PUMPCOMM, "During LGS running, injection is restricted")
            33   -> aapsLogger.error(LTag.PUMPCOMM, "LGS status is ON, ON Command is declined.")
            34   -> aapsLogger.error(LTag.PUMPCOMM, "LGS status is OFF, OFF Command is declined.")
            35   -> aapsLogger.error(LTag.PUMPCOMM, "Tempbasal start is rejected  when tempbasal is running")
            36   -> aapsLogger.error(LTag.PUMPCOMM, "Tempbasal stop is rejected  when tempbasal is not running")
            else -> aapsLogger.error(LTag.PUMPCOMM, "It cannot be set to a system error.")
        }
        return isSuccess
    }

    companion object {

        const val MSG_LEN: Int = 20 // 메시지 길이(20바이트 패킷)
        const val MSG_LEN_BIG: Int = 182 // 메시지 길이(182바이트 대량패킷)
        const val SOP: Byte = 0xef.toByte() // 패킷 시작 바이트(20바이트 패킷)
        const val SOP_BIG: Byte = 0xed.toByte() // 대량 패킷 시작 바이트(182바이트 대량패킷)
        const val MSG_TYPE_LOC: Int = 1 // 메시지 종류 위치
        const val MSG_SEQ_LOC: Int = 2 // 메시지 시퀀스번호 위치
        const val BT_MSG_DATA_LOC: Byte = 4 // 데이터 위치
        const val MSG_PAD: Byte = 0xff.toByte() // 메시지 뒷부분 빈공간을 채우는 값
        const val MSG_CON_END: Byte = 0x00.toByte() // 패킷 내용 끝
        const val MSG_CON_CONTINUE: Byte = 0x01.toByte() // 패킷 내용 계속

        /**
         * CRC 정보
         */
        private val crc_table = byteArrayOf(
            0x00.toByte(), 0x25.toByte(), 0x4A.toByte(), 0x6F.toByte(), 0x94.toByte(), 0xB1.toByte(), 0xDE.toByte(), 0xFB.toByte(),
            0x0D.toByte(), 0x28.toByte(), 0x47.toByte(), 0x62.toByte(), 0x99.toByte(), 0xBC.toByte(), 0xD3.toByte(), 0xF6.toByte(),
            0x1A.toByte(), 0x3F.toByte(), 0x50.toByte(), 0x75.toByte(), 0x8E.toByte(), 0xAB.toByte(), 0xC4.toByte(), 0xE1.toByte(),
            0x17.toByte(), 0x32.toByte(), 0x5D.toByte(), 0x78.toByte(), 0x83.toByte(), 0xA6.toByte(), 0xC9.toByte(), 0xEC.toByte(),
            0x34.toByte(), 0x11.toByte(), 0x7E.toByte(), 0x5B.toByte(), 0xA0.toByte(), 0x85.toByte(), 0xEA.toByte(), 0xCF.toByte(),
            0x39.toByte(), 0x1C.toByte(), 0x73.toByte(), 0x56.toByte(), 0xAD.toByte(), 0x88.toByte(), 0xE7.toByte(), 0xC2.toByte(),
            0x2E.toByte(), 0x0B.toByte(), 0x64.toByte(), 0x41.toByte(), 0xBA.toByte(), 0x9F.toByte(), 0xF0.toByte(), 0xD5.toByte(),
            0x23.toByte(), 0x06.toByte(), 0x69.toByte(), 0x4C.toByte(), 0xB7.toByte(), 0x92.toByte(), 0xFD.toByte(), 0xD8.toByte(),
            0x68.toByte(), 0x4D.toByte(), 0x22.toByte(), 0x07.toByte(), 0xFC.toByte(), 0xD9.toByte(), 0xB6.toByte(), 0x93.toByte(),
            0x65.toByte(), 0x40.toByte(), 0x2F.toByte(), 0x0A.toByte(), 0xF1.toByte(), 0xD4.toByte(), 0xBB.toByte(), 0x9E.toByte(),
            0x72.toByte(), 0x57.toByte(), 0x38.toByte(), 0x1D.toByte(), 0xE6.toByte(), 0xC3.toByte(), 0xAC.toByte(), 0x89.toByte(),
            0x7F.toByte(), 0x5A.toByte(), 0x35.toByte(), 0x10.toByte(), 0xEB.toByte(), 0xCE.toByte(), 0xA1.toByte(), 0x84.toByte(),
            0x5C.toByte(), 0x79.toByte(), 0x16.toByte(), 0x33.toByte(), 0xC8.toByte(), 0xED.toByte(), 0x82.toByte(), 0xA7.toByte(),
            0x51.toByte(), 0x74.toByte(), 0x1B.toByte(), 0x3E.toByte(), 0xC5.toByte(), 0xE0.toByte(), 0x8F.toByte(), 0xAA.toByte(),
            0x46.toByte(), 0x63.toByte(), 0x0C.toByte(), 0x29.toByte(), 0xD2.toByte(), 0xF7.toByte(), 0x98.toByte(), 0xBD.toByte(),
            0x4B.toByte(), 0x6E.toByte(), 0x01.toByte(), 0x24.toByte(), 0xDF.toByte(), 0xFA.toByte(), 0x95.toByte(), 0xB0.toByte(),
            0xD0.toByte(), 0xF5.toByte(), 0x9A.toByte(), 0xBF.toByte(), 0x44.toByte(), 0x61.toByte(), 0x0E.toByte(), 0x2B.toByte(),
            0xDD.toByte(), 0xF8.toByte(), 0x97.toByte(), 0xB2.toByte(), 0x49.toByte(), 0x6C.toByte(), 0x03.toByte(), 0x26.toByte(),
            0xCA.toByte(), 0xEF.toByte(), 0x80.toByte(), 0xA5.toByte(), 0x5E.toByte(), 0x7B.toByte(), 0x14.toByte(), 0x31.toByte(),
            0xC7.toByte(), 0xE2.toByte(), 0x8D.toByte(), 0xA8.toByte(), 0x53.toByte(), 0x76.toByte(), 0x19.toByte(), 0x3C.toByte(),
            0xE4.toByte(), 0xC1.toByte(), 0xAE.toByte(), 0x8B.toByte(), 0x70.toByte(), 0x55.toByte(), 0x3A.toByte(), 0x1F.toByte(),
            0xE9.toByte(), 0xCC.toByte(), 0xA3.toByte(), 0x86.toByte(), 0x7D.toByte(), 0x58.toByte(), 0x37.toByte(), 0x12.toByte(),
            0xFE.toByte(), 0xDB.toByte(), 0xB4.toByte(), 0x91.toByte(), 0x6A.toByte(), 0x4F.toByte(), 0x20.toByte(), 0x05.toByte(),
            0xF3.toByte(), 0xD6.toByte(), 0xB9.toByte(), 0x9C.toByte(), 0x67.toByte(), 0x42.toByte(), 0x2D.toByte(), 0x08.toByte(),
            0xB8.toByte(), 0x9D.toByte(), 0xF2.toByte(), 0xD7.toByte(), 0x2C.toByte(), 0x09.toByte(), 0x66.toByte(), 0x43.toByte(),
            0xB5.toByte(), 0x90.toByte(), 0xFF.toByte(), 0xDA.toByte(), 0x21.toByte(), 0x04.toByte(), 0x6B.toByte(), 0x4E.toByte(),
            0xA2.toByte(), 0x87.toByte(), 0xE8.toByte(), 0xCD.toByte(), 0x36.toByte(), 0x13.toByte(), 0x7C.toByte(), 0x59.toByte(),
            0xAF.toByte(), 0x8A.toByte(), 0xE5.toByte(), 0xC0.toByte(), 0x3B.toByte(), 0x1E.toByte(), 0x71.toByte(), 0x54.toByte(),
            0x8C.toByte(), 0xA9.toByte(), 0xC6.toByte(), 0xE3.toByte(), 0x18.toByte(), 0x3D.toByte(), 0x52.toByte(), 0x77.toByte(),
            0x81.toByte(), 0xA4.toByte(), 0xCB.toByte(), 0xEE.toByte(), 0x15.toByte(), 0x30.toByte(), 0x5F.toByte(), 0x7A.toByte(),
            0x96.toByte(), 0xB3.toByte(), 0xDC.toByte(), 0xF9.toByte(), 0x02.toByte(), 0x27.toByte(), 0x48.toByte(), 0x6D.toByte(),
            0x9B.toByte(), 0xBE.toByte(), 0xD1.toByte(), 0xF4.toByte(), 0x0F.toByte(), 0x2A.toByte(), 0x45.toByte(), 0x60.toByte()
        )

        // 패킷 디코딩 앞부분
        fun prefixDecode(bytes: ByteArray): ByteBuffer {
            val buffer = ByteBuffer.wrap(bytes)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(BT_MSG_DATA_LOC.toInt())
            return buffer
        }

        fun getByteToInt(buffer: ByteBuffer): Int {
            return buffer.get().toInt() and 0xff
        }

        fun getShortToInt(buffer: ByteBuffer): Int {
            return buffer.getShort().toInt() and 0xffff
        }

        fun getIntToInt(buffer: ByteBuffer): Int {
            return buffer.getInt()
        }

        // CRC 체크
        fun getCRC(data: ByteArray, length: Int): Byte {
            var length = length
            var i = 0
            var crc: Byte = 0
            while (length-- != 0) {
                crc = crc_table[(crc.toInt() xor data[i].toInt()) and 0xFF]
                i++
            }
            return crc
        }

        // 패킷 결함 체크
        fun defect(bytes: ByteArray): Int {
            var result = 0
            if (bytes[0] != SOP && bytes[0] != SOP_BIG) {
                // Start Code Check
                result = 98
            } else if ((bytes[0] == SOP && bytes.size != MSG_LEN) ||
                (bytes[0] == SOP_BIG && bytes.size != MSG_LEN_BIG)
            ) {
                // 패킷 길이 체크
                result = 97
            } else if (bytes[bytes.size - 1] != getCRC(bytes, bytes.size - 1)) {
                // CRC 체크
                result = 99
            }
            return result
        }

        fun toHex(bytes: ByteArray): String {
            val sb = StringBuilder()
            for (b in bytes) sb.append(String.format("%02x ", b.toInt() and 0xff))
            return sb.toString()
        }

        fun toNarrowHex(packet: ByteArray): String {
            val sb = StringBuilder()
            for (b in packet) sb.append(String.format("%02x", b.toInt() and 0xff))
            return sb.toString()
        }
    }
}