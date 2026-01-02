package app.aaps.pump.equil.manager.command

import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.EquilResponse
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class BaseCmdTest : TestBaseWithProfile() {

    @Mock
    private lateinit var equilManager: EquilManager

    private lateinit var testCmd: TestCmd

    // Concrete implementation of BaseCmd for testing
    private inner class TestCmd(createTime: Long = System.currentTimeMillis()) :
        BaseCmd(createTime, aapsLogger, preferences, equilManager) {

        override fun getEquilResponse(): EquilResponse? = null
        override fun getNextEquilResponse(): EquilResponse? = null
        override fun decodeEquilPacket(data: ByteArray): EquilResponse? = null
        override fun decode(): EquilResponse? = null
        override fun decodeConfirm(): EquilResponse? = null
        override fun getEventType(): EquilHistoryRecord.EventType? = null
    }

    @BeforeEach
    fun setUp() {
        testCmd = TestCmd()
    }

    @Test
    fun `toNewStart should clear bit 7`() {
        // Test clearing the 7th bit (MSB)
        val input = 0xFF.toByte() // 11111111
        val result = testCmd.toNewStart(input)
        assertEquals(0x7F.toByte(), result) // 01111111

        val input2 = 0x80.toByte() // 10000000
        val result2 = testCmd.toNewStart(input2)
        assertEquals(0x00.toByte(), result2) // 00000000

        val input3 = 0x00.toByte() // 00000000
        val result3 = testCmd.toNewStart(input3)
        assertEquals(0x00.toByte(), result3) // 00000000
    }

    @Test
    fun `toNewEndConf should set bit 7`() {
        // Test setting the 7th bit (MSB)
        val input = 0x00.toByte() // 00000000
        val result = testCmd.toNewEndConf(input)
        assertEquals(0x80.toByte(), result) // 10000000

        val input2 = 0x7F.toByte() // 01111111
        val result2 = testCmd.toNewEndConf(input2)
        assertEquals(0xFF.toByte(), result2) // 11111111

        val input3 = 0xFF.toByte() // 11111111
        val result3 = testCmd.toNewEndConf(input3)
        assertEquals(0xFF.toByte(), result3) // 11111111
    }

    @Test
    fun `isEnd should return true when bit 7 is set`() {
        assertTrue(testCmd.isEnd(0x80.toByte())) // 10000000
        assertTrue(testCmd.isEnd(0xFF.toByte())) // 11111111
        assertTrue(testCmd.isEnd(0xC0.toByte())) // 11000000
    }

    @Test
    fun `isEnd should return false when bit 7 is not set`() {
        assertFalse(testCmd.isEnd(0x00.toByte())) // 00000000
        assertFalse(testCmd.isEnd(0x7F.toByte())) // 01111111
        assertFalse(testCmd.isEnd(0x3F.toByte())) // 00111111
    }

    @Test
    fun `getIndex should extract lower 6 bits`() {
        // Extract bits 0-5
        assertEquals(0, testCmd.getIndex(0x00.toByte())) // 00000000
        assertEquals(1, testCmd.getIndex(0x01.toByte())) // 00000001
        assertEquals(63, testCmd.getIndex(0x3F.toByte())) // 00111111
        assertEquals(63, testCmd.getIndex(0xFF.toByte())) // 11111111 -> lower 6 bits = 111111 = 63
        assertEquals(0, testCmd.getIndex(0x80.toByte())) // 10000000 -> lower 6 bits = 000000 = 0
        assertEquals(15, testCmd.getIndex(0x0F.toByte())) // 00001111
    }

    @Test
    fun `getBit should return correct bit value`() {
        val byte = 0xAA.toByte() // 10101010

        assertEquals(0, testCmd.getBit(byte, 0)) // bit 0 = 0
        assertEquals(1, testCmd.getBit(byte, 1)) // bit 1 = 1
        assertEquals(0, testCmd.getBit(byte, 2)) // bit 2 = 0
        assertEquals(1, testCmd.getBit(byte, 3)) // bit 3 = 1
        assertEquals(0, testCmd.getBit(byte, 4)) // bit 4 = 0
        assertEquals(1, testCmd.getBit(byte, 5)) // bit 5 = 1
        assertEquals(0, testCmd.getBit(byte, 6)) // bit 6 = 0
        assertEquals(1, testCmd.getBit(byte, 7)) // bit 7 = 1
    }

    @Test
    fun `getBit should work with all zeros`() {
        val byte = 0x00.toByte()
        for (i in 0..7) {
            assertEquals(0, testCmd.getBit(byte, i))
        }
    }

    @Test
    fun `getBit should work with all ones`() {
        val byte = 0xFF.toByte()
        for (i in 0..7) {
            assertEquals(1, testCmd.getBit(byte, i))
        }
    }

    @Test
    fun `convertString should prepend 0 to each character`() {
        assertEquals("0A0B0C", testCmd.convertString("ABC"))
        assertEquals("01020304", testCmd.convertString("1234"))
        assertEquals("", testCmd.convertString(""))
        assertEquals("0 ", testCmd.convertString(" "))
    }

    @Test
    fun `convertString should handle special characters`() {
        assertEquals("0!0@0#", testCmd.convertString("!@#"))
        assertEquals("0a0b0c", testCmd.convertString("abc"))
    }

    @Test
    fun `up1 should round up to nearest integer`() {
        assertEquals(1, testCmd.up1(0.1))
        assertEquals(1, testCmd.up1(0.9))
        assertEquals(1, testCmd.up1(1.0))
        assertEquals(2, testCmd.up1(1.1))
        assertEquals(2, testCmd.up1(1.9))
        assertEquals(5, testCmd.up1(4.5))
        assertEquals(10, testCmd.up1(9.99))
    }

    @Test
    fun `up1 should handle zero and negative values`() {
        assertEquals(0, testCmd.up1(0.0))
        // RoundingMode.UP rounds away from zero, so negative values round down
        assertEquals(-1, testCmd.up1(-0.5))
        assertEquals(-2, testCmd.up1(-1.5))
    }

    @Test
    fun `up1 should handle large values`() {
        assertEquals(100, testCmd.up1(99.01))
        assertEquals(1000, testCmd.up1(999.001))
    }

    @Test
    fun `isPairStep should return false by default`() {
        assertFalse(testCmd.isPairStep())
    }

    @Test
    fun `toNewStart and toNewEndConf should be inverse for bit 7`() {
        val original = 0x3F.toByte() // 00111111 (bit 7 not set)
        val withEndBit = testCmd.toNewEndConf(original) // Should set bit 7
        val cleared = testCmd.toNewStart(withEndBit) // Should clear bit 7
        assertEquals(original, cleared)
    }

    @Test
    fun `getIndex should work with various byte values`() {
        // Test all possible 6-bit values (0-63)
        for (i in 0..63) {
            val byte = i.toByte()
            assertEquals(i, testCmd.getIndex(byte))
        }
    }

    @Test
    fun `isEnd should correctly identify end packets`() {
        // Packets with bit 7 set are end packets
        for (i in 128..255) {
            assertTrue(testCmd.isEnd(i.toByte()), "Byte $i should be identified as end")
        }

        // Packets with bit 7 not set are not end packets
        for (i in 0..127) {
            assertFalse(testCmd.isEnd(i.toByte()), "Byte $i should not be identified as end")
        }
    }

    @Test
    fun `bit manipulation should preserve other bits`() {
        val original = 0x3F.toByte() // 00111111

        // Setting bit 7 should preserve other bits
        val withBit7 = testCmd.toNewEndConf(original)
        assertEquals(0xBF.toByte(), withBit7) // 10111111

        // Clearing bit 7 should preserve other bits
        val cleared = testCmd.toNewStart(withBit7)
        assertEquals(0x3F.toByte(), cleared) // 00111111
    }

    @Test
    fun `convertString with single character`() {
        assertEquals("0X", testCmd.convertString("X"))
    }

    @Test
    fun `convertString with long string`() {
        val input = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val expected = "0A0B0C0D0E0F0G0H0I0J0K0L0M0N0O0P0Q0R0S0T0U0V0W0X0Y0Z"
        assertEquals(expected, testCmd.convertString(input))
    }

    @Test
    fun `up1 with exact integers`() {
        assertEquals(5, testCmd.up1(5.0))
        assertEquals(10, testCmd.up1(10.0))
        assertEquals(100, testCmd.up1(100.0))
    }

    @Test
    fun `companion object values should be initialized`() {
        assertEquals("0F0F", BaseCmd.DEFAULT_PORT)
    }
}
