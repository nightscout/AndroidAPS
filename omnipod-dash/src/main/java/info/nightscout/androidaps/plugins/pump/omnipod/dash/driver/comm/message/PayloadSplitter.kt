package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.packet.BlePacket
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.packet.FirstBlePacket
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.packet.LastBlePacket
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.packet.LastOptionalPlusOneBlePacket
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.packet.MiddleBlePacket
import java.lang.Integer.min
import java.util.zip.CRC32

internal class PayloadSplitter(private val payload: ByteArray) {

    fun splitInPackets(): List<BlePacket> {
        val ret = ArrayList<BlePacket>()
        val crc32 = payload.crc32()
        if (payload.size <= FirstBlePacket.CAPACITY_WITH_THE_OPTIONAL_PLUS_ONE_PACKET) {
            val end = min(FirstBlePacket.CAPACITY_WITHOUT_MIDDLE_PACKETS, payload.size)
            ret.add(FirstBlePacket(
                totalFragments = 0,
                payload = payload.copyOfRange(0, end),
                size = payload.size.toByte(),
                crc32 = crc32,
            ))
            if (payload.size > FirstBlePacket.CAPACITY_WITHOUT_MIDDLE_PACKETS) {
                ret.add(LastOptionalPlusOneBlePacket(
                    index = 1,
                    payload = payload.copyOfRange(end, payload.size),
                    size = (payload.size-end).toByte(),
                ))
            }
            return ret
        }
        val middleFragments = (payload.size - FirstBlePacket.CAPACITY_WITH_MIDDLE_PACKETS) / MiddleBlePacket.CAPACITY
        val rest = ((payload.size - middleFragments * MiddleBlePacket.CAPACITY) - FirstBlePacket.CAPACITY_WITH_MIDDLE_PACKETS).toByte()
        ret.add(FirstBlePacket(
            totalFragments = (middleFragments + 1).toByte(),
            payload = payload.copyOfRange(0, FirstBlePacket.CAPACITY_WITH_MIDDLE_PACKETS),
        ))
        for (i in 1..middleFragments) {
            val p = if (i == 1) {
                payload.copyOfRange(FirstBlePacket.CAPACITY_WITH_MIDDLE_PACKETS, FirstBlePacket.CAPACITY_WITH_MIDDLE_PACKETS + MiddleBlePacket.CAPACITY)
            } else {
                payload.copyOfRange(FirstBlePacket.CAPACITY_WITH_MIDDLE_PACKETS + (i - 1) * MiddleBlePacket.CAPACITY, FirstBlePacket.CAPACITY_WITH_MIDDLE_PACKETS + i * MiddleBlePacket.CAPACITY)
            }
            ret.add(MiddleBlePacket(
                index = i.toByte(),
                payload = p,
            ))
        }
        val end = min(LastBlePacket.CAPACITY, rest.toInt())
        ret.add(LastBlePacket(
            index = (middleFragments + 1).toByte(),
            size = rest,
            payload = payload.copyOfRange(middleFragments * MiddleBlePacket.CAPACITY + FirstBlePacket.CAPACITY_WITH_MIDDLE_PACKETS, middleFragments * MiddleBlePacket.CAPACITY + FirstBlePacket.CAPACITY_WITH_MIDDLE_PACKETS + end),
            crc32 = crc32,
        ))
        if (rest > LastBlePacket.CAPACITY) {
            ret.add(LastOptionalPlusOneBlePacket(
                index = (middleFragments + 2).toByte(),
                size = (rest-LastBlePacket.CAPACITY).toByte(),
                payload = payload.copyOfRange(middleFragments * MiddleBlePacket.CAPACITY + FirstBlePacket.CAPACITY_WITH_MIDDLE_PACKETS + LastBlePacket.CAPACITY, payload.size),
            ))
        }
        return ret
    }
}

private fun ByteArray.crc32(): Long {
    val crc = CRC32()
    crc.update(this)
    return crc.value
}
