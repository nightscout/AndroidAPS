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
        if (payload.size <= 18) {
            val end = min(14, payload.size)
            ret.add(FirstBlePacket(
                totalFragments = 0,
                payload = payload.copyOfRange(0, end),
                size = payload.size.toByte(),
                crc32 = crc32,
            ))
            if (payload.size > 14) {
                ret.add(LastOptionalPlusOneBlePacket(
                    index = 1,
                    payload = payload.copyOfRange(end, payload.size),
                ))
            }
            return ret
        }
        val middleFragments = (payload.size - 18) / 19
        val rest = ((payload.size - middleFragments.toInt() * 19) - 18).toByte()
        ret.add(FirstBlePacket(
            totalFragments = (middleFragments + 1).toByte(),
            payload = payload.copyOfRange(0, 18),
        ))
        for (i in 1..middleFragments) {
            val p = if (i == 1) {
                payload.copyOfRange(18, 37)
            } else {
                payload.copyOfRange((i - 1) * 19 + 18, (i - 1) * 19 + 18 + 19)
            }
            ret.add(MiddleBlePacket(
                index = i.toByte(),
                payload = p,
            ))
        }
        val end = min(14, rest.toInt())
        ret.add(LastBlePacket(
            index = (middleFragments + 1).toByte(),
            size = rest,
            payload = payload.copyOfRange(middleFragments * 19 + 18, middleFragments * 19 + 18 + end),
            crc32 = crc32,
        ))
        if (rest > 14) {
            ret.add(LastOptionalPlusOneBlePacket(
                index = (middleFragments + 2).toByte(),
                payload = payload.copyOfRange(middleFragments * 19 + 18 + 14, payload.size),
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
