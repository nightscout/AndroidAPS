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
    fun `isEnd should return true when bit 7 is set`() {
        assertTrue(testCmd.isEnd(0x80.toByte()))
        assertTrue(testCmd.isEnd(0xFF.toByte()))
        assertTrue(testCmd.isEnd(0xC0.toByte()))
    }

    @Test
    fun `isEnd should return false when bit 7 is not set`() {
        assertFalse(testCmd.isEnd(0x00.toByte()))
        assertFalse(testCmd.isEnd(0x7F.toByte()))
        assertFalse(testCmd.isEnd(0x3F.toByte()))
    }

    @Test
    fun `getIndex should extract lower 6 bits`() {
        assertEquals(0, testCmd.getIndex(0x00.toByte()))
        assertEquals(1, testCmd.getIndex(0x01.toByte()))
        assertEquals(63, testCmd.getIndex(0x3F.toByte()))
        assertEquals(63, testCmd.getIndex(0xFF.toByte()))
        assertEquals(0, testCmd.getIndex(0x80.toByte()))
        assertEquals(15, testCmd.getIndex(0x0F.toByte()))
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
    fun `isPairStep should return false by default`() {
        assertFalse(testCmd.isPairStep())
    }

    @Test
    fun `isEnd should correctly identify end packets`() {
        for (i in 128..255) {
            assertTrue(testCmd.isEnd(i.toByte()), "Byte $i should be identified as end")
        }
        for (i in 0..127) {
            assertFalse(testCmd.isEnd(i.toByte()), "Byte $i should not be identified as end")
        }
    }

    @Test
    fun `getIndex should work with various byte values`() {
        for (i in 0..63) {
            val byte = i.toByte()
            assertEquals(i, testCmd.getIndex(byte))
        }
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
    fun `companion object values should be initialized`() {
        assertEquals("0F0F", BaseCmd.DEFAULT_PORT)
    }
}
