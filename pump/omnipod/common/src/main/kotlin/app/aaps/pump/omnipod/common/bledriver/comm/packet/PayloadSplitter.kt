package app.aaps.pump.omnipod.common.bledriver.comm.packet

import java.lang.Integer.min
import java.util.zip.CRC32

class PayloadSplitter(private val payload: ByteArray, private val layout: BlePacketLayout = BlePacketLayout.DASH) {

    fun splitInPackets(): List<BlePacket> {
        if (payload.size <= layout.firstPacketCapacityWithOptionalPlusOnePacket) {
            return splitInOnePacket()
        }
        val ret = ArrayList<BlePacket>()
        val crc32 = payload.crc32()
        val middleFragments = (payload.size - layout.firstPacketCapacityWithMiddlePackets) / layout.middlePacketCapacity
        val rest =
            (payload.size - middleFragments * layout.middlePacketCapacity) -
                layout.firstPacketCapacityWithMiddlePackets
        ret.add(
            FirstBlePacket(
                fullFragments = middleFragments + 1,
                payload = payload.copyOfRange(0, layout.firstPacketCapacityWithMiddlePackets)
            )
        )
        for (i in 1..middleFragments) {
            val p = payload.copyOfRange(
                layout.firstPacketCapacityWithMiddlePackets + (i - 1) * layout.middlePacketCapacity,
                layout.firstPacketCapacityWithMiddlePackets + i * layout.middlePacketCapacity
            )
            ret.add(
                MiddleBlePacket(
                    index = i.toByte(),
                    payload = p
                )
            )
        }
        val end = min(layout.lastPacketCapacity, rest)
        ret.add(
            LastBlePacket(
                index = (middleFragments + 1).toByte(),
                size = rest.toByte(),
                payload = payload.copyOfRange(
                    middleFragments * layout.middlePacketCapacity + layout.firstPacketCapacityWithMiddlePackets,
                    middleFragments * layout.middlePacketCapacity + layout.firstPacketCapacityWithMiddlePackets + end
                ),
                crc32 = crc32
            )
        )
        if (rest > layout.lastPacketCapacity) {
            ret.add(
                LastOptionalPlusOneBlePacket(
                    index = (middleFragments + 2).toByte(),
                    size = (rest - layout.lastPacketCapacity).toByte(),
                    payload = payload.copyOfRange(
                        middleFragments * layout.middlePacketCapacity +
                            layout.firstPacketCapacityWithMiddlePackets +
                            layout.lastPacketCapacity,
                        payload.size
                    )
                )
            )
        }
        return ret
    }

    private fun splitInOnePacket(): List<BlePacket> {
        val ret = ArrayList<BlePacket>()
        val crc32 = payload.crc32()
        val end = min(layout.firstPacketCapacityWithoutMiddlePackets, payload.size)
        ret.add(
            FirstBlePacket(
                fullFragments = 0,
                payload = payload.copyOfRange(0, end),
                size = payload.size.toByte(),
                crc32 = crc32
            )
        )
        if (payload.size > layout.firstPacketCapacityWithoutMiddlePackets) {
            ret.add(
                LastOptionalPlusOneBlePacket(
                    index = 1,
                    payload = payload.copyOfRange(end, payload.size),
                    size = (payload.size - end).toByte()
                )
            )
        }
        return ret
    }
}

internal fun ByteArray.crc32(): Long {
    val crc = CRC32()
    crc.update(this)
    return crc.value
}
