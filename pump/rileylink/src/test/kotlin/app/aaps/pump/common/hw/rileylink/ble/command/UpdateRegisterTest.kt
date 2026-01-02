package app.aaps.pump.common.hw.rileylink.ble.command

import app.aaps.pump.common.hw.rileylink.ble.defs.CC111XRegister
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for UpdateRegister command
 */
class UpdateRegisterTest {

    @Test
    fun `getCommandType returns UpdateRegister`() {
        val command = UpdateRegister(CC111XRegister.freq0, 0x00)

        assertEquals(RileyLinkCommandType.UpdateRegister, command.getCommandType())
    }

    @Test
    fun `getRaw returns correct format with freq0 register`() {
        val command = UpdateRegister(CC111XRegister.freq0, 0xAB.toByte())
        val raw = command.getRaw()

        assertArrayEquals(
            byteArrayOf(
                RileyLinkCommandType.UpdateRegister.code,
                CC111XRegister.freq0.value,
                0xAB.toByte()
            ),
            raw
        )
    }

    @Test
    fun `getRaw returns correct format with freq1 register`() {
        val command = UpdateRegister(CC111XRegister.freq1, 0x12)
        val raw = command.getRaw()

        assertArrayEquals(
            byteArrayOf(
                RileyLinkCommandType.UpdateRegister.code,
                CC111XRegister.freq1.value,
                0x12
            ),
            raw
        )
    }

    @Test
    fun `getRaw returns correct format with freq2 register`() {
        val command = UpdateRegister(CC111XRegister.freq2, 0x34)
        val raw = command.getRaw()

        assertArrayEquals(
            byteArrayOf(
                RileyLinkCommandType.UpdateRegister.code,
                CC111XRegister.freq2.value,
                0x34
            ),
            raw
        )
    }

    @Test
    fun `getRaw returns 3 bytes total`() {
        val command = UpdateRegister(CC111XRegister.sync0, 0xFF.toByte())
        val raw = command.getRaw()

        assertEquals(3, raw.size)
    }

    @Test
    fun `getRaw with zero value`() {
        val command = UpdateRegister(CC111XRegister.pktlen, 0x00)
        val raw = command.getRaw()

        assertEquals(RileyLinkCommandType.UpdateRegister.code, raw[0])
        assertEquals(CC111XRegister.pktlen.value, raw[1])
        assertEquals(0x00.toByte(), raw[2])
    }

    @Test
    fun `getRaw with maximum byte value`() {
        val command = UpdateRegister(CC111XRegister.mdmcfg0, 0xFF.toByte())
        val raw = command.getRaw()

        assertEquals(RileyLinkCommandType.UpdateRegister.code, raw[0])
        assertEquals(CC111XRegister.mdmcfg0.value, raw[1])
        assertEquals(0xFF.toByte(), raw[2])
    }

    @Test
    fun `getRaw with sync1 register`() {
        val command = UpdateRegister(CC111XRegister.sync1, 0xA5.toByte())
        val raw = command.getRaw()

        assertEquals(CC111XRegister.sync1.value, raw[1])
        assertEquals(0xA5.toByte(), raw[2])
    }

    @Test
    fun `getRaw with sync0 register`() {
        val command = UpdateRegister(CC111XRegister.sync0, 0x5A.toByte())
        val raw = command.getRaw()

        assertEquals(CC111XRegister.sync0.value, raw[1])
        assertEquals(0x5A.toByte(), raw[2])
    }

    @Test
    fun `getRaw with pktctrl1 register`() {
        val command = UpdateRegister(CC111XRegister.pktctrl1, 0x0C)
        val raw = command.getRaw()

        assertEquals(CC111XRegister.pktctrl1.value, raw[1])
        assertEquals(0x0C.toByte(), raw[2])
    }

    @Test
    fun `getRaw with pktctrl0 register`() {
        val command = UpdateRegister(CC111XRegister.pktctrl0, 0x45)
        val raw = command.getRaw()

        assertEquals(CC111XRegister.pktctrl0.value, raw[1])
        assertEquals(0x45.toByte(), raw[2])
    }

    @Test
    fun `getRaw with mdmcfg4 register`() {
        val command = UpdateRegister(CC111XRegister.mdmcfg4, 0x99.toByte())
        val raw = command.getRaw()

        assertEquals(CC111XRegister.mdmcfg4.value, raw[1])
        assertEquals(0x99.toByte(), raw[2])
    }

    @Test
    fun `getRaw with mdmcfg3 register`() {
        val command = UpdateRegister(CC111XRegister.mdmcfg3, 0x66)
        val raw = command.getRaw()

        assertEquals(CC111XRegister.mdmcfg3.value, raw[1])
        assertEquals(0x66.toByte(), raw[2])
    }

    @Test
    fun `getRaw with mdmcfg2 register`() {
        val command = UpdateRegister(CC111XRegister.mdmcfg2, 0x33)
        val raw = command.getRaw()

        assertEquals(CC111XRegister.mdmcfg2.value, raw[1])
        assertEquals(0x33.toByte(), raw[2])
    }

    @Test
    fun `getRaw with mdmcfg1 register`() {
        val command = UpdateRegister(CC111XRegister.mdmcfg1, 0x22)
        val raw = command.getRaw()

        assertEquals(CC111XRegister.mdmcfg1.value, raw[1])
        assertEquals(0x22.toByte(), raw[2])
    }

    @Test
    fun `getRaw with agcctrl2 register`() {
        val command = UpdateRegister(CC111XRegister.agcctrl2, 0x43)
        val raw = command.getRaw()

        assertEquals(CC111XRegister.agcctrl2.value, raw[1])
        assertEquals(0x43.toByte(), raw[2])
    }

    @Test
    fun `getRaw with paTable0 register`() {
        val command = UpdateRegister(CC111XRegister.paTable0, 0xC0.toByte())
        val raw = command.getRaw()

        assertEquals(CC111XRegister.paTable0.value, raw[1])
        assertEquals(0xC0.toByte(), raw[2])
    }

    @Test
    fun `getRaw with deviatn register`() {
        val command = UpdateRegister(CC111XRegister.deviatn, 0x15)
        val raw = command.getRaw()

        assertEquals(CC111XRegister.deviatn.value, raw[1])
        assertEquals(0x15.toByte(), raw[2])
    }

    @Test
    fun `getRaw with test0 register`() {
        val command = UpdateRegister(CC111XRegister.test0, 0x09)
        val raw = command.getRaw()

        assertEquals(CC111XRegister.test0.value, raw[1])
        assertEquals(0x09.toByte(), raw[2])
    }

    @Test
    fun `getRaw with test1 register`() {
        val command = UpdateRegister(CC111XRegister.test1, 0x35)
        val raw = command.getRaw()

        assertEquals(CC111XRegister.test1.value, raw[1])
        assertEquals(0x35.toByte(), raw[2])
    }

    @Test
    fun `realistic scenario - update frequency register for 916 MHz`() {
        // Typical frequency setting for US pump (916.5 MHz)
        val command = UpdateRegister(CC111XRegister.freq2, 0x23)
        val raw = command.getRaw()

        assertEquals(RileyLinkCommandType.UpdateRegister.code, raw[0])
        assertEquals(CC111XRegister.freq2.value, raw[1])
        assertEquals(0x23.toByte(), raw[2])
    }

    @Test
    fun `realistic scenario - update sync word`() {
        // Common sync word pattern for Medtronic
        val command = UpdateRegister(CC111XRegister.sync1, 0xFF.toByte())
        val raw = command.getRaw()

        assertEquals(0xFF.toByte(), raw[2])
    }

    @Test
    fun `realistic scenario - update packet length`() {
        // Set packet length to 80 bytes
        val command = UpdateRegister(CC111XRegister.pktlen, 80)
        val raw = command.getRaw()

        assertEquals(80.toByte(), raw[2])
    }

    @Test
    fun `realistic scenario - update power amplifier`() {
        // Maximum power setting
        val command = UpdateRegister(CC111XRegister.paTable0, 0xFF.toByte())
        val raw = command.getRaw()

        assertEquals(CC111XRegister.paTable0.value, raw[1])
        assertEquals(0xFF.toByte(), raw[2])
    }

    @Test
    fun `all registers can be updated`() {
        val registers = listOf(
            CC111XRegister.sync1,
            CC111XRegister.sync0,
            CC111XRegister.pktlen,
            CC111XRegister.pktctrl1,
            CC111XRegister.pktctrl0,
            CC111XRegister.fsctrl1,
            CC111XRegister.freq2,
            CC111XRegister.freq1,
            CC111XRegister.freq0,
            CC111XRegister.mdmcfg4,
            CC111XRegister.mdmcfg3,
            CC111XRegister.mdmcfg2,
            CC111XRegister.mdmcfg1,
            CC111XRegister.mdmcfg0,
            CC111XRegister.deviatn,
            CC111XRegister.mcsm0,
            CC111XRegister.foccfg,
            CC111XRegister.agcctrl2,
            CC111XRegister.agcctrl1,
            CC111XRegister.agcctrl0,
            CC111XRegister.frend1,
            CC111XRegister.frend0,
            CC111XRegister.fscal3,
            CC111XRegister.fscal2,
            CC111XRegister.fscal1,
            CC111XRegister.fscal0,
            CC111XRegister.test1,
            CC111XRegister.test0,
            CC111XRegister.paTable0
        )

        registers.forEach { register ->
            val command = UpdateRegister(register, 0x42)
            val raw = command.getRaw()

            assertEquals(3, raw.size)
            assertEquals(RileyLinkCommandType.UpdateRegister.code, raw[0])
            assertEquals(register.value, raw[1])
            assertEquals(0x42.toByte(), raw[2])
        }
    }

    @Test
    fun `register property is accessible`() {
        val command = UpdateRegister(CC111XRegister.freq0, 0x12)

        assertEquals(CC111XRegister.freq0, command.register)
    }

    @Test
    fun `registerValue property is accessible`() {
        val command = UpdateRegister(CC111XRegister.freq0, 0x12)

        assertEquals(0x12.toByte(), command.registerValue)
    }

    @Test
    fun `command format is consistent`() {
        val testValues = listOf<Byte>(0x00, 0x01, 0x7F, 0x80.toByte(), 0xFF.toByte())

        testValues.forEach { value ->
            val command = UpdateRegister(CC111XRegister.freq0, value)
            val raw = command.getRaw()

            assertEquals(3, raw.size)
            assertEquals(RileyLinkCommandType.UpdateRegister.code, raw[0])
            assertEquals(value, raw[2])
        }
    }
}
