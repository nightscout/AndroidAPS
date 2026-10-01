package app.aaps.pump.equil.manager

import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer

/**
 * Shared packet framing logic for Equil BLE communication.
 *
 * Used by both the app-side commands (BaseCmd) and the pump emulator.
 * Handles building 16-byte BLE packets from encrypted payloads
 * and parsing received packets back into EquilCmdModel.
 */
object EquilPacketCodec {

    /**
     * Build BLE packets from an encrypted payload.
     *
     * Packet format (16 bytes):
     * - [0-1]: Header (0x00, 0x00)
     * - [2]: Packet length (0x10 for full, varies for last)
     * - [3]: Payload offset (10*i for packet i)
     * - [4]: Control byte (bit 7 = end flag, bits 0-5 = index)
     * - [5]: CRC8 Maxim over bytes 0-4
     * - [6-15]: Payload data (max 10 bytes)
     *
     * First packet embeds CRC16 of the full payload after the first 4 bytes.
     */
    fun buildPackets(equilCmdModel: EquilCmdModel, port: String?, reqIndex: Int, createTime: Long): EquilResponse {
        val allData = StringBuilder()
        allData.append(port)
        allData.append(equilCmdModel.tag)
        allData.append(equilCmdModel.iv)
        allData.append(equilCmdModel.ciphertext)
        var allByte = Utils.hexStringToBytes(allData.toString())
        val crc1 = Crc.getCRC(allByte)
        allByte = Utils.hexStringToBytes(allData.toString())
        var byteIndex = 0
        var lastLen = 0
        val index: Int = if ((allByte.size - 8) % 10 == 0) 1
        else 2

        val equilResponse = EquilResponse(createTime)
        val maxLen = (allByte.size - 8) / 10 + index
        for (i in 0 until maxLen) {
            var buffer = ByteBuffer.allocate(16)
            if (i > 0 && lastLen < 10) {
                buffer = ByteBuffer.allocate(6 + lastLen)
            }
            buffer.put(0x00.toByte())
            buffer.put(0x00.toByte())
            if (i == maxLen - 1) {
                buffer.put((6 + lastLen).toByte())
                buffer.put((10 * i).toByte())
                buffer.put(setEndBit(reqIndex.toByte()))
            } else {
                buffer.put(0x10.toByte())
                buffer.put((10 * i).toByte())
                buffer.put(clearEndBit(reqIndex.toByte()))
            }
            val crcArray = ByteArray(5)
            System.arraycopy(buffer.array(), 0, crcArray, 0, 5)
            buffer.put(Crc.crc8Maxim(crcArray).toByte())
            if (i == 0) {
                buffer.put(allByte[byteIndex++])
                buffer.put(allByte[byteIndex++])
                buffer.put(allByte[byteIndex++])
                buffer.put(allByte[byteIndex++])
                buffer.put(crc1[1])
                buffer.put(crc1[0])
                buffer.put(allByte[byteIndex++])
                buffer.put(allByte[byteIndex++])
                buffer.put(allByte[byteIndex++])
                buffer.put(allByte[byteIndex++])
            } else {
                if (lastLen < 10) {
                    (0 until lastLen).forEach { _ ->
                        buffer.put(allByte[byteIndex++])
                    }
                } else {
                    (0..9).forEach { _ ->
                        buffer.put(allByte[byteIndex++])
                    }
                }
            }
            lastLen = allByte.size - byteIndex
            equilResponse.add(buffer)
        }
        return equilResponse
    }

    /**
     * Parse an EquilCmdModel from reassembled BLE packets.
     *
     * Extracts tag (16 bytes), iv (12 bytes), ciphertext, and code from
     * the packet payloads stored in the response's send list.
     */
    fun parseModel(response: EquilResponse): EquilCmdModel {
        val equilCmdModel = EquilCmdModel()
        val list: MutableList<Byte?> = ArrayList()
        var index = 0
        for (b in response.send) {
            if (index == 0) {
                val bs = b.array()
                for (i in bs.size - 4 until bs.size) list.add(bs[i])
                val codeByte = byteArrayOf(bs[10], bs[11])
                equilCmdModel.code = Utils.bytesToHex(codeByte)
            } else {
                val bs = b.array()
                for (i in 6 until bs.size) {
                    list.add(bs[i])
                }
            }
            index++
        }
        val list1 = list.subList(0, 16)
        val list2 = list.subList(16, 12 + 16)
        val list3 = list.subList(12 + 16, list.size)
        equilCmdModel.iv = Utils.bytesToHex(list2).lowercase()
        equilCmdModel.tag = Utils.bytesToHex(list1).lowercase()
        equilCmdModel.ciphertext = Utils.bytesToHex(list3).lowercase()
        return equilCmdModel
    }

    /**
     * Validate a received BLE packet: check for duplicate index and CRC8.
     */
    fun validatePacket(data: ByteArray, response: EquilResponse): Boolean {
        if (response.send.isNotEmpty()) {
            val preData = response.send[response.send.size - 1].array()
            val index = data[3].toInt() and 0xff
            val preIndex = preData[3].toInt() and 0xff
            if (index == preIndex) return false
        }
        val crc = data[5].toInt() and 0xff
        val crc1 = Crc.crc8Maxim(data.copyOfRange(0, 5))
        return crc == crc1
    }

    /** Clear bit 7 — marks a non-final packet. */
    fun clearEndBit(number: Byte): Byte = (number.toInt() and (1 shl 7).inv()).toByte()

    /** Set bit 7 — marks the final packet. */
    fun setEndBit(number: Byte): Byte = (number.toInt() or (1 shl 7)).toByte()

    /** Check if bit 7 is set (end-of-message flag). */
    fun isEnd(b: Byte): Boolean = ((b.toInt() shr 7) and 0x1) == 1

    /** Extract the 6-bit packet index from the control byte. */
    fun getIndex(b: Byte): Int = b.toInt() and 63

    /** Ceiling integer division. */
    private fun ceilDiv(a: Int, b: Int): Int {
        val bg = BigDecimal(a.toDouble() / b)
        return bg.setScale(0, RoundingMode.UP).toInt()
    }
}
