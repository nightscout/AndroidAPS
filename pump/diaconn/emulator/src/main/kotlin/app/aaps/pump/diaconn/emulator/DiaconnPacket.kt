package app.aaps.pump.diaconn.emulator

import java.io.ByteArrayOutputStream

/**
 * The framing of a Diaconn G8 packet: start byte, message type, sequence number, a "more follows" byte, the data, 0xFF
 * up to the last byte, and a CRC-8 as the last byte. All numbers are little-endian.
 *
 * Written here again on purpose, not taken from the driver: a framing fault in the driver must make a test fail. With
 * one codec for both sides, both would agree on the fault.
 */
internal object DiaconnPacket {

    /** Start byte of a normal packet, always [SIZE] bytes. */
    const val SOP = 0xEF

    /** Start byte of a big packet, always [SIZE_BIG] bytes. Only the pump sends them. */
    const val SOP_BIG = 0xED
    const val SIZE = 20
    const val SIZE_BIG = 182

    /** Where the data starts. In an answer the first data byte is the result. */
    const val DATA = 4

    /** The top two bits of the message type. */
    const val TYPE_SETTING = 0
    const val TYPE_INQUIRY = 1

    private const val PAD = 0xFF
    private const val POLYNOMIAL = 0x25

    /** CRC-8 with polynomial 0x25, start value 0, not reflected, no final XOR. */
    private val crcTable = IntArray(256) { index ->
        var crc = index
        repeat(8) { crc = if (crc and 0x80 != 0) ((crc shl 1) xor POLYNOMIAL) and 0xFF else (crc shl 1) and 0xFF }
        crc
    }

    /** The CRC of the first [length] bytes of [bytes]. */
    fun crc(bytes: ByteArray, length: Int): Byte {
        var crc = 0
        for (i in 0 until length) crc = crcTable[(crc xor bytes[i].toInt()) and 0xFF]
        return crc.toByte()
    }

    /** 0 setting, 1 inquiry, 2 answer, 3 report. */
    fun type(msgType: Int): Int = (msgType and 0xC0) shr 6

    /** The answer to a request: a setting 0x07 is answered with 0x87, an inquiry 0x6E with 0xAE. */
    fun answerType(requestType: Int): Int = (requestType and 0x3F) or 0x80

    /** A packet with [msgType], [seq] and [data], "nothing more follows", 0xFF after the data and the CRC at the end. */
    fun build(msgType: Int, seq: Int, data: ByteArray, big: Boolean = false): ByteArray {
        val size = if (big) SIZE_BIG else SIZE
        require(data.size <= size - DATA - 1) { "${data.size} data bytes do not fit in a packet of $size" }
        val packet = ByteArray(size) { PAD.toByte() }
        packet[0] = (if (big) SOP_BIG else SOP).toByte()
        packet[1] = msgType.toByte()
        packet[2] = seq.toByte()
        packet[3] = 0
        data.copyInto(packet, DATA)
        packet[size - 1] = crc(packet, size - 1)
        return packet
    }
}

/** The data of a packet, written little-endian. */
internal class PacketData {

    private val out = ByteArrayOutputStream()

    fun u8(value: Int): PacketData = apply { out.write(value and 0xFF) }
    fun u16(value: Int): PacketData = apply { u8(value).u8(value shr 8) }
    fun u32(value: Long): PacketData = apply { u16((value and 0xFFFF).toInt()).u16(((value shr 16) and 0xFFFF).toInt()) }
    fun bytes(): ByteArray = out.toByteArray()
}

internal fun ByteArray.u8(offset: Int): Int = this[offset].toInt() and 0xFF
internal fun ByteArray.u16(offset: Int): Int = u8(offset) or (u8(offset + 1) shl 8)
internal fun ByteArray.i32(offset: Int): Int = u16(offset) or (u16(offset + 2) shl 16)
