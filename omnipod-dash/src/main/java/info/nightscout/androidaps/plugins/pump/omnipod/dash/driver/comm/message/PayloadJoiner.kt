package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.CrcMismatchException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.IncorrectPacketException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.crc32
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.packet.BlePacket
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.packet.FirstBlePacket
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.packet.LastBlePacket
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.packet.LastOptionalPlusOneBlePacket
import java.lang.Integer.min
import java.nio.ByteBuffer
import java.util.*

class PayloadJoiner(private val firstPacket: ByteArray) {

    var oneExtra: Boolean = false
    val fullFragments: Int
    var crc: Long = 0
    private var expectedIndex = 0
    private val fragments: LinkedList<ByteArray> = LinkedList<ByteArray>()

    init {
        if (firstPacket.size < 2) {
            throw IncorrectPacketException(0, firstPacket)
        }
        fullFragments = firstPacket[1].toInt()
        when {
            // Without middle packets
            firstPacket.size < FirstBlePacket.HEADER_SIZE_WITHOUT_MIDDLE_PACKETS ->
                throw IncorrectPacketException(0, firstPacket)

            fullFragments == 0                                                   -> {
                crc = ByteBuffer.wrap(firstPacket.copyOfRange(2, 6)).int.toUnsignedLong()
                val rest = firstPacket[6]
                val end = min(rest + 7, BlePacket.MAX_LEN)
                oneExtra = rest + 7 > end
                fragments.add(firstPacket.copyOfRange(FirstBlePacket.HEADER_SIZE_WITHOUT_MIDDLE_PACKETS, end))
                if (end > firstPacket.size) {
                    throw IncorrectPacketException(0, firstPacket)
                }
            }

            // With middle packets
            firstPacket.size < BlePacket.MAX_LEN                                 ->
                throw IncorrectPacketException(0, firstPacket)

            else                                                                 -> {
                fragments.add(firstPacket.copyOfRange(FirstBlePacket.HEADER_SIZE_WITH_MIDDLE_PACKETS, BlePacket.MAX_LEN))
            }
        }
    }

    fun accumulate(packet: ByteArray) {
        if (packet.size < 3) { // idx, size, at least 1 byte of payload
            throw IncorrectPacketException((expectedIndex + 1).toByte(), packet)
        }
        val idx = packet[0].toInt()
        if (idx != expectedIndex + 1) {
            throw IncorrectPacketException((expectedIndex + 1).toByte(), packet)
        }
        expectedIndex++
        when {
            idx < fullFragments  -> { // this is a middle fragment
                if (packet.size < BlePacket.MAX_LEN) {
                    throw IncorrectPacketException(idx.toByte(), packet)
                }
                fragments.add(packet.copyOfRange(1, BlePacket.MAX_LEN))
            }

            idx == fullFragments -> { // this is the last fragment
                if (packet.size < LastBlePacket.HEADER_SIZE) {
                    throw IncorrectPacketException(idx.toByte(), packet)
                }
                crc = ByteBuffer.wrap(packet.copyOfRange(2, 6)).int.toUnsignedLong()
                val rest = packet[1].toInt()
                val end = min(rest, BlePacket.MAX_LEN)
                if (packet.size < end) {
                    throw IncorrectPacketException(idx.toByte(), packet)
                }
                oneExtra = rest + LastBlePacket.HEADER_SIZE > end
                fragments.add(packet.copyOfRange(LastBlePacket.HEADER_SIZE, BlePacket.MAX_LEN))
            }

            idx > fullFragments  -> { // this is the extra fragment
                val size = packet[1].toInt()
                if (packet.size < LastOptionalPlusOneBlePacket.HEADER_SIZE + size) {
                    throw IncorrectPacketException(idx.toByte(), packet)
                }

                fragments.add(packet.copyOfRange(LastOptionalPlusOneBlePacket.HEADER_SIZE, LastOptionalPlusOneBlePacket.HEADER_SIZE + size))
            }
        }
    }

    fun finalize(): ByteArray {
        val totalLen = fragments.fold(0, { acc, elem -> acc + elem.size })
        val bb = ByteBuffer.allocate(totalLen)
        fragments.map { fragment -> bb.put(fragment) }
        bb.flip()
        val bytes = bb.array()
        if (bytes.crc32() != crc) {
            throw CrcMismatchException(bytes.crc32(), crc, bytes)
        }
        return bytes.copyOfRange(0, bytes.size)
    }

}

private fun Int.toUnsignedLong() = this.toLong() and 0xffffffffL