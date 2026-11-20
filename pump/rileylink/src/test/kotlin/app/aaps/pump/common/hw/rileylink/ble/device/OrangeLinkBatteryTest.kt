package app.aaps.pump.common.hw.rileylink.ble.device

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OrangeLinkBatteryTest {

    private fun bytes(hex: String): ByteArray =
        hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `reads millivolts from a status notification`() {
        val status = bytes("DD FE AA 03 02 01 01 0B 72 50 01 01 FF FF FF FF FF FF FF FF FF FF FF FF FF FF")
        assertEquals(2930, OrangeLinkImpl.batteryMillivolts(status))
    }

    @Test
    fun `reads fresh cells above the signed byte range`() {
        assertEquals(3200, OrangeLinkImpl.batteryMillivolts(bytes("DD FE AA 03 02 01 01 0C 80 64")))
    }

    @Test
    fun `ignores any other packet`() {
        assertNull(OrangeLinkImpl.batteryMillivolts(bytes("DD 01 02 03 04 05 06 0B 72 50")))
    }

    @Test
    fun `ignores a status notification too short to carry the voltage`() {
        assertNull(OrangeLinkImpl.batteryMillivolts(bytes("DD FE AA 03 02 01 01 0B")))
    }
}
