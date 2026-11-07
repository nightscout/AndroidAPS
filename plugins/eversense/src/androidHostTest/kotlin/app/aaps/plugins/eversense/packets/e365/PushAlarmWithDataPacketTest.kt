package app.aaps.plugins.eversense.packets.e365

import app.aaps.plugins.eversense.enums.EversenseAlarm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PushAlarmWithDataPacketTest {

    private fun makePacket(vararg bytes: Int): PushAlarmWithDataPacket {
        val packet = PushAlarmWithDataPacket()
        packet.appendData(bytes.map { it.toUByte() }.toUByteArray())
        return packet
    }

    @Test
    fun `data shorter than 12 bytes returns null`() {
        val packet = makePacket(0x44, 0x03, 14, 0x00)
        assertNull(packet.parseResponse())
    }

    @Test
    fun `alarm code comes from byte index 2, not the reserved byte at index 3`() {
        // [0]=0x44 header, [1]=0x03 type, [2]=14 (LOW_GLUCOSE, the real alarm code),
        // [3]=reserved (nonzero on purpose - must NOT be read as the code; empirically confirmed
        // constant/0 on real devices, but a nonzero value here proves this test doesn't pass by
        // coincidence), [4..11]=datetime. This is the exact byte position mistake that briefly
        // broke real alarm decoding (High Glucose etc. all showing as CRITICAL_FAULT) after an
        // earlier fix swapped these two indices based on a doc comment that turned out to be wrong.
        val packet = makePacket(0x44, 0x03, 14, 0x7F, 0, 0, 0, 0, 0, 0, 0, 0)
        assertEquals(EversenseAlarm.LOW_GLUCOSE, packet.parseResponse()?.alarm?.code)
    }

    @Test
    fun `unrecognized alarm code falls back to UNKNOWN`() {
        val packet = makePacket(0x44, 0x03, 254, 0x00, 0, 0, 0, 0, 0, 0, 0, 0)
        assertEquals(EversenseAlarm.UNKNOWN, packet.parseResponse()?.alarm?.code)
    }
}
