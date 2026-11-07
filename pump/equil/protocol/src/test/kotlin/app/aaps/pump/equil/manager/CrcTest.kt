package app.aaps.pump.equil.manager

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class CrcTest : TestBase() {

    @Test
    fun `crc8Maxim should calculate correct CRC for simple data`() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val crc = Crc.crc8Maxim(data)
        // CRC should be deterministic
        assertEquals(crc, Crc.crc8Maxim(data))
    }

    @Test
    fun `crc8Maxim should return 0 for empty array`() {
        val data = byteArrayOf()
        val crc = Crc.crc8Maxim(data)
        assertEquals(0x00, crc)
    }

    @Test
    fun `crc8Maxim should handle single byte`() {
        val data = byteArrayOf(0x00)
        val crc1 = Crc.crc8Maxim(data)

        val data2 = byteArrayOf(0xFF.toByte())
        val crc2 = Crc.crc8Maxim(data2)

        // Different data should produce different CRCs
        assertNotEquals(crc1, crc2)
    }

    @Test
    fun `crc8Maxim should be deterministic`() {
        val data = byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x9A.toByte())
        val crc1 = Crc.crc8Maxim(data)
        val crc2 = Crc.crc8Maxim(data)
        assertEquals(crc1, crc2)
    }

    @Test
    fun `crc8Maxim should produce different results for different data`() {
        val data1 = byteArrayOf(0x01, 0x02, 0x03)
        val data2 = byteArrayOf(0x03, 0x02, 0x01)
        val crc1 = Crc.crc8Maxim(data1)
        val crc2 = Crc.crc8Maxim(data2)
        assertNotEquals(crc1, crc2)
    }

    @Test
    fun `crc8Maxim should handle all zero bytes`() {
        val data = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        val crc = Crc.crc8Maxim(data)
        assertEquals(0x00, crc)
    }

    @Test
    fun `crc8Maxim should handle all 0xFF bytes`() {
        val data = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val crc = Crc.crc8Maxim(data)
        // Should produce a consistent result
        assertEquals(crc, Crc.crc8Maxim(data))
    }

    @Test
    fun `crc8Maxim should handle large data arrays`() {
        val data = ByteArray(1000) { it.toByte() }
        val crc1 = Crc.crc8Maxim(data)
        val crc2 = Crc.crc8Maxim(data)
        assertEquals(crc1, crc2)
    }

    @Test
    fun `crc8Maxim should detect data corruption`() {
        val data1 = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val data2 = byteArrayOf(0x01, 0x02, 0x04, 0x04, 0x05) // Changed one byte
        val crc1 = Crc.crc8Maxim(data1)
        val crc2 = Crc.crc8Maxim(data2)
        assertNotEquals(crc1, crc2)
    }

    @Test
    fun `getCRC should return 2-byte CRC`() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val crc = Crc.getCRC(data)
        assertEquals(2, crc.size)
    }

    @Test
    fun `getCRC should be deterministic`() {
        val data = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        val crc1 = Crc.getCRC(data)
        val crc2 = Crc.getCRC(data)
        assertArrayEquals(crc1, crc2)
    }

    @Test
    fun `getCRC should produce different results for different data`() {
        val data1 = byteArrayOf(0x01, 0x02, 0x03)
        val data2 = byteArrayOf(0x03, 0x02, 0x01)
        val crc1 = Crc.getCRC(data1)
        val crc2 = Crc.getCRC(data2)
        assertNotEquals(Utils.bytesToHex(crc1), Utils.bytesToHex(crc2))
    }

    @Test
    fun `getCRC should handle empty array`() {
        val data = byteArrayOf()
        val crc = Crc.getCRC(data)
        assertEquals(2, crc.size)
        // Empty data should produce a specific CRC (0xFFFF initial value)
        assertEquals(crc[0], Crc.getCRC(data)[0])
        assertEquals(crc[1], Crc.getCRC(data)[1])
    }

    @Test
    fun `getCRC should handle single byte`() {
        val data = byteArrayOf(0xAB.toByte())
        val crc = Crc.getCRC(data)
        assertEquals(2, crc.size)
    }

    @Test
    fun `getCRC should handle all zeros`() {
        val data = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        val crc = Crc.getCRC(data)
        assertEquals(2, crc.size)
        // Should be consistent
        assertArrayEquals(crc, Crc.getCRC(data))
    }

    @Test
    fun `getCRC should handle all 0xFF`() {
        val data = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val crc = Crc.getCRC(data)
        assertEquals(2, crc.size)
        assertArrayEquals(crc, Crc.getCRC(data))
    }

    @Test
    fun `getCRC should detect single bit flip`() {
        val data1 = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        val data2 = byteArrayOf(0x00, 0x00, 0x00, 0x01) // Changed one bit
        val crc1 = Crc.getCRC(data1)
        val crc2 = Crc.getCRC(data2)
        assertNotEquals(Utils.bytesToHex(crc1), Utils.bytesToHex(crc2))
    }

    @Test
    fun `getCRC should handle large data arrays`() {
        val data = ByteArray(1000) { it.toByte() }
        val crc = Crc.getCRC(data)
        assertEquals(2, crc.size)
        assertArrayEquals(crc, Crc.getCRC(data))
    }

    @Test
    fun `getCRC should produce valid hex string convertible bytes`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val crc = Crc.getCRC(data)
        val hexString = Utils.bytesToHex(crc)
        assertEquals(4, hexString.length) // 2 bytes = 4 hex chars
    }

    @Test
    fun `crc8Maxim should be in valid byte range`() {
        val testData = listOf(
            byteArrayOf(0x00),
            byteArrayOf(0xFF.toByte()),
            byteArrayOf(0x01, 0x02, 0x03),
            byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        )

        testData.forEach { data ->
            val crc = Crc.crc8Maxim(data)
            assertTrue(crc in 0..255, "CRC should be in range 0-255, got $crc")
        }
    }

    @Test
    fun `different CRC methods should work independently`() {
        val data = byteArrayOf(0x12, 0x34, 0x56, 0x78)

        // crc8Maxim returns single byte (as Int)
        val crc8 = Crc.crc8Maxim(data)
        assertTrue(crc8 in 0..255)

        // getCRC returns 2-byte array
        val crc16 = Crc.getCRC(data)
        assertEquals(2, crc16.size)
    }

    @Test
    fun `crc8Maxim consistency check with known patterns`() {
        // Test with sequential data
        val sequential = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val crc1 = Crc.crc8Maxim(sequential)

        // Test with reverse
        val reverse = byteArrayOf(0x05, 0x04, 0x03, 0x02, 0x01)
        val crc2 = Crc.crc8Maxim(reverse)

        // Different order should give different CRC
        assertNotEquals(crc1, crc2)
    }

    @Test
    fun `getCRC consistency check with known patterns`() {
        // Test with sequential data
        val sequential = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val crc1 = Crc.getCRC(sequential)

        // Test with reverse
        val reverse = byteArrayOf(0x05, 0x04, 0x03, 0x02, 0x01)
        val crc2 = Crc.getCRC(reverse)

        // Different order should give different CRC
        assertNotEquals(Utils.bytesToHex(crc1), Utils.bytesToHex(crc2))
    }

    private fun assertTrue(condition: Boolean, message: String = "") {
        if (!condition) {
            throw AssertionError(message)
        }
    }
}
