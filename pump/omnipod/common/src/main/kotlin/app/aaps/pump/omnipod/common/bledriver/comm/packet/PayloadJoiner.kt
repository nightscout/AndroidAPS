package app.aaps.pump.omnipod.common.bledriver.comm.packet

import app.aaps.pump.omnipod.common.bledriver.comm.message.CrcMismatchException
import app.aaps.pump.omnipod.common.bledriver.comm.message.IncorrectPacketException
import java.nio.ByteBuffer
import java.util.LinkedList

class PayloadJoiner(firstPacket: ByteArray, private val layout: BlePacketLayout = BlePacketLayout.DASH) {

    var oneExtraPacket: Boolean
    val fullFragments: Int
    var crc: Long = 0
    private var expectedIndex = 0
    private val fragments: MutableList<BlePacket> = LinkedList<BlePacket>()

    init {
        val firstPacket = FirstBlePacket.parse(firstPacket, layout)
        fragments.add(firstPacket)
        fullFragments = firstPacket.fullFragments
        crc = firstPacket.crc32 ?: 0
        oneExtraPacket = firstPacket.oneExtraPacket
    }

    fun accumulate(packet: ByteArray) {
        if (packet.size < 3) { // idx, size, at least 1 byte of payload
            throw IncorrectPacketException(packet, (expectedIndex + 1).toByte())
        }
        val idx = packet[0].toUnsignedInt()
        if (idx != expectedIndex + 1) {
            throw IncorrectPacketException(packet, (expectedIndex + 1).toByte())
        }
        expectedIndex++
        when {
            idx < fullFragments                        -> {
                fragments.add(MiddleBlePacket.parse(packet, layout))
            }

            idx == fullFragments                       -> {
                val lastPacket = LastBlePacket.parse(packet, layout)
                fragments.add(lastPacket)
                crc = lastPacket.crc32
                oneExtraPacket = lastPacket.oneExtraPacket
            }

            idx == fullFragments + 1 && oneExtraPacket -> {
                fragments.add(LastOptionalPlusOneBlePacket.parse(packet, layout))
            }

            idx > fullFragments                        -> {
                throw IncorrectPacketException(packet, idx.toByte())
            }
        }
    }

    fun finalize(): ByteArray {
        val payloads = fragments.map { x -> x.payload }
        val totalLen = payloads.fold(0, { acc, elem -> acc + elem.size })
        val bb = ByteBuffer.allocate(totalLen)
        payloads.map { p -> bb.put(p) }
        bb.flip()
        val bytes = bb.array()
        if (bytes.crc32() != crc) {
            throw CrcMismatchException(bytes.crc32(), crc, bytes)
        }
        return bytes.copyOfRange(0, bytes.size)
    }
}

internal fun Int.toUnsignedLong() = this.toLong() and 0xffffffffL
