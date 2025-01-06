package app.aaps.pump.omnipod.dash.driver.comm.packet

import app.aaps.pump.omnipod.dash.driver.comm.message.CrcMismatchException
import app.aaps.pump.omnipod.dash.driver.comm.message.IncorrectPacketException
import java.nio.ByteBuffer
import java.util.*

class PayloadJoiner(firstPacket: ByteArray) {

    var oneExtraPacket: Boolean
    val fullFragments: Int
    var crc: Long = 0
    private var expectedIndex = 0
    private val fragments: MutableList<BlePacket> = LinkedList<BlePacket>()

    init {
        val firstPacket = FirstBlePacket.parse(firstPacket)
        fragments.add(firstPacket)
        fullFragments = firstPacket.fullFragments
        crc = firstPacket.crc32 ?: 0
        oneExtraPacket = firstPacket.oneExtraPacket
    }

    fun accumulate(packet: ByteArray) {
        if (packet.size < 3) { // idx, size, at least 1 byte of payload
            throw IncorrectPacketException(packet, (expectedIndex + 1).toByte())
        }
        val idx = packet[0].toInt()
        if (idx != expectedIndex + 1) {
            throw IncorrectPacketException(packet, (expectedIndex + 1).toByte())
        }
        expectedIndex++
        when {
            idx < fullFragments                        -> {
                fragments.add(MiddleBlePacket.parse(packet))
            }

            idx == fullFragments                       -> {
                val lastPacket = LastBlePacket.parse(packet)
                fragments.add(lastPacket)
                crc = lastPacket.crc32
                oneExtraPacket = lastPacket.oneExtraPacket
            }

            idx == fullFragments + 1 && oneExtraPacket -> {
                fragments.add(LastOptionalPlusOneBlePacket.parse(packet))
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
