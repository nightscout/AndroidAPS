package app.aaps.pump.common.hw.rileylink.ble.command

import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersionBase
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import org.apache.commons.lang3.NotImplementedException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for SetPreamble command
 */
class SetPreambleTest {

    @Test
    fun `getCommandType returns SetPreamble`() {
        val rileyLinkServiceData = mock<RileyLinkServiceData>()
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        val command = SetPreamble(rileyLinkServiceData, 1000)

        assertEquals(RileyLinkCommandType.SetPreamble, command.getCommandType())
    }

    @Test
    fun `constructor throws NotImplementedException for firmware v1`() {
        val rileyLinkServiceData = mock<RileyLinkServiceData>()
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_1_0)

        assertThrows(NotImplementedException::class.java) {
            SetPreamble(rileyLinkServiceData, 1000)
        }
    }

    @Test
    fun `constructor throws IllegalArgumentException for negative preamble`() {
        val rileyLinkServiceData = mock<RileyLinkServiceData>()
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        assertThrows(IllegalArgumentException::class.java) {
            SetPreamble(rileyLinkServiceData, -1)
        }
    }

    @Test
    fun `constructor throws IllegalArgumentException for preamble greater than 0xFFFF`() {
        val rileyLinkServiceData = mock<RileyLinkServiceData>()
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        assertThrows(IllegalArgumentException::class.java) {
            SetPreamble(rileyLinkServiceData, 0x10000)
        }
    }

    @Test
    fun `constructor accepts preamble value 0`() {
        val rileyLinkServiceData = mock<RileyLinkServiceData>()
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        val command = SetPreamble(rileyLinkServiceData, 0)

        assertEquals(RileyLinkCommandType.SetPreamble, command.getCommandType())
    }

    @Test
    fun `constructor accepts preamble value 0xFFFF`() {
        val rileyLinkServiceData = mock<RileyLinkServiceData>()
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        val command = SetPreamble(rileyLinkServiceData, 0xFFFF)

        assertEquals(RileyLinkCommandType.SetPreamble, command.getCommandType())
    }

    @Test
    fun `getRaw returns correct format for zero preamble`() {
        val rileyLinkServiceData = mock<RileyLinkServiceData>()
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        val command = SetPreamble(rileyLinkServiceData, 0)
        val raw = command.getRaw()

        // Format: [commandType, high byte, low byte]
        assertEquals(3, raw.size)
        assertEquals(RileyLinkCommandType.SetPreamble.code, raw[0])
        assertEquals(0x00.toByte(), raw[1]) // high byte
        assertEquals(0x00.toByte(), raw[2]) // low byte
    }

    @Test
    fun `getRaw returns correct format for small preamble value`() {
        val rileyLinkServiceData = mock<RileyLinkServiceData>()
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        val command = SetPreamble(rileyLinkServiceData, 255) // 0x00FF
        val raw = command.getRaw()

        assertEquals(3, raw.size)
        assertEquals(RileyLinkCommandType.SetPreamble.code, raw[0])
        assertEquals(0x00.toByte(), raw[1]) // high byte
        assertEquals(0xFF.toByte(), raw[2]) // low byte
    }

    @Test
    fun `getRaw returns correct format for medium preamble value`() {
        val rileyLinkServiceData = mock<RileyLinkServiceData>()
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        val command = SetPreamble(rileyLinkServiceData, 1000) // 0x03E8
        val raw = command.getRaw()

        assertEquals(3, raw.size)
        assertEquals(RileyLinkCommandType.SetPreamble.code, raw[0])
        assertEquals(0x03.toByte(), raw[1]) // high byte
        assertEquals(0xE8.toByte(), raw[2]) // low byte
    }

    @Test
    fun `getRaw returns correct format for large preamble value`() {
        val rileyLinkServiceData = mock<RileyLinkServiceData>()
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        val command = SetPreamble(rileyLinkServiceData, 0x1234)
        val raw = command.getRaw()

        assertEquals(3, raw.size)
        assertEquals(RileyLinkCommandType.SetPreamble.code, raw[0])
        assertEquals(0x12.toByte(), raw[1]) // high byte
        assertEquals(0x34.toByte(), raw[2]) // low byte
    }

    @Test
    fun `getRaw returns correct format for maximum preamble value`() {
        val rileyLinkServiceData = mock<RileyLinkServiceData>()
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        val command = SetPreamble(rileyLinkServiceData, 0xFFFF)
        val raw = command.getRaw()

        assertEquals(3, raw.size)
        assertEquals(RileyLinkCommandType.SetPreamble.code, raw[0])
        assertEquals(0xFF.toByte(), raw[1]) // high byte
        assertEquals(0xFF.toByte(), raw[2]) // low byte
    }

    @Test
    fun `getRaw with firmware v2_0`() {
        val rileyLinkServiceData = mock<RileyLinkServiceData>()
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        val command = SetPreamble(rileyLinkServiceData, 2000)
        val raw = command.getRaw()

        // 2000 = 0x07D0
        assertArrayEquals(
            byteArrayOf(
                RileyLinkCommandType.SetPreamble.code,
                0x07.toByte(),
                0xD0.toByte()
            ),
            raw
        )
    }

    @Test
    fun `getRaw with firmware v2_2`() {
        val rileyLinkServiceData = mock<RileyLinkServiceData>()
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_2)

        val command = SetPreamble(rileyLinkServiceData, 1500)
        val raw = command.getRaw()

        // 1500 = 0x05DC
        assertArrayEquals(
            byteArrayOf(
                RileyLinkCommandType.SetPreamble.code,
                0x05.toByte(),
                0xDC.toByte()
            ),
            raw
        )
    }

    @Test
    fun `getRaw with firmware v3`() {
        val rileyLinkServiceData = mock<RileyLinkServiceData>()
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_3_x)

        val command = SetPreamble(rileyLinkServiceData, 3000)
        val raw = command.getRaw()

        // 3000 = 0x0BB8
        assertEquals(3, raw.size)
        assertEquals(0x0B.toByte(), raw[1])
        assertEquals(0xB8.toByte(), raw[2])
    }

    @Test
    fun `realistic scenario - typical preamble extension`() {
        val rileyLinkServiceData = mock<RileyLinkServiceData>()
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_2)

        // Typical preamble extension for better range
        val command = SetPreamble(rileyLinkServiceData, 2500)
        val raw = command.getRaw()

        assertEquals(RileyLinkCommandType.SetPreamble.code, raw[0])
        assertEquals(3, raw.size)
    }

    @Test
    fun `realistic scenario - disable preamble extension`() {
        val rileyLinkServiceData = mock<RileyLinkServiceData>()
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        // Disable preamble extension
        val command = SetPreamble(rileyLinkServiceData, 0)
        val raw = command.getRaw()

        assertEquals(RileyLinkCommandType.SetPreamble.code, raw[0])
        assertEquals(0x00.toByte(), raw[1])
        assertEquals(0x00.toByte(), raw[2])
    }

    @Test
    fun `boundary value - exactly at maximum`() {
        val rileyLinkServiceData = mock<RileyLinkServiceData>()
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        val command = SetPreamble(rileyLinkServiceData, 65535) // 0xFFFF
        val raw = command.getRaw()

        assertEquals(0xFF.toByte(), raw[1])
        assertEquals(0xFF.toByte(), raw[2])
    }

    @Test
    fun `boundary value - one below minimum invalid`() {
        val rileyLinkServiceData = mock<RileyLinkServiceData>()
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        assertThrows(IllegalArgumentException::class.java) {
            SetPreamble(rileyLinkServiceData, -1)
        }
    }

    @Test
    fun `boundary value - one above maximum invalid`() {
        val rileyLinkServiceData = mock<RileyLinkServiceData>()
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        assertThrows(IllegalArgumentException::class.java) {
            SetPreamble(rileyLinkServiceData, 65536) // 0x10000
        }
    }

    @Test
    fun `works with all v2 and higher firmware versions`() {
        val firmwareVersions = listOf(
            RileyLinkFirmwareVersionBase.Version_2_0,
            RileyLinkFirmwareVersionBase.Version_2_2,
            RileyLinkFirmwareVersionBase.Version_2_x,
            RileyLinkFirmwareVersionBase.Version_3_x,
            RileyLinkFirmwareVersionBase.Version_4_x
        )

        firmwareVersions.forEach { version ->
            val rileyLinkServiceData = mock<RileyLinkServiceData>()
            whenever(rileyLinkServiceData.firmwareVersion).thenReturn(version)

            val command = SetPreamble(rileyLinkServiceData, 1000)
            val raw = command.getRaw()

            assertEquals(3, raw.size)
            assertEquals(RileyLinkCommandType.SetPreamble.code, raw[0])
        }
    }

    @Test
    fun `preamble value 256 encodes correctly`() {
        val rileyLinkServiceData = mock<RileyLinkServiceData>()
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        val command = SetPreamble(rileyLinkServiceData, 256) // 0x0100
        val raw = command.getRaw()

        assertEquals(0x01.toByte(), raw[1]) // high byte
        assertEquals(0x00.toByte(), raw[2]) // low byte
    }

    @Test
    fun `preamble value 257 encodes correctly`() {
        val rileyLinkServiceData = mock<RileyLinkServiceData>()
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        val command = SetPreamble(rileyLinkServiceData, 257) // 0x0101
        val raw = command.getRaw()

        assertEquals(0x01.toByte(), raw[1]) // high byte
        assertEquals(0x01.toByte(), raw[2]) // low byte
    }
}
