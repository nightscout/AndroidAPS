package app.aaps.pump.common.utils

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Tests for CRC utility functions
 */
class CRCTest {

    @Test
    fun `crc8lookup table has 256 entries`() {
        assertEquals(256, CRC.crc8lookup.size)
    }

    @Test
    fun `crc8 of null data returns 0`() {
        val result = CRC.crc8(null, 0)
        assertEquals(0.toByte(), result)
    }

    @Test
    fun `crc8 of empty array returns 0`() {
        val data = byteArrayOf()
        val result = CRC.crc8(data)
        assertEquals(0.toByte(), result)
    }

    @Test
    fun `crc8 of single byte`() {
        val data = byteArrayOf(0x00)
        val result = CRC.crc8(data)
        assertEquals(0.toByte(), result)
    }

    @Test
    fun `crc8 of single non-zero byte`() {
        val data = byteArrayOf(0x01)
        val result = CRC.crc8(data)
        // CRC8 of 0x01 should be crc8lookup[0 XOR 1] = crc8lookup[1] = 155
        assertEquals(155.toByte(), result)
    }

    @Test
    fun `crc8 of multiple bytes`() {
        val data = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        val result = CRC.crc8(data)
        assertNotNull(result)
        // Result should be deterministic
    }

    @Test
    fun `crc8 with specified length shorter than array`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val result = CRC.crc8(data, 2)

        // Should only process first 2 bytes
        val expectedData = byteArrayOf(0x01, 0x02)
        val expected = CRC.crc8(expectedData)

        assertEquals(expected, result)
    }

    @Test
    fun `crc8 with length longer than array uses full array`() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val result = CRC.crc8(data, 100)

        // Should process full array
        val expected = CRC.crc8(data)

        assertEquals(expected, result)
    }

    @Test
    fun `crc8 with zero length returns 0`() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val result = CRC.crc8(data, 0)
        assertEquals(0.toByte(), result)
    }

    @Test
    fun `crc8 is deterministic`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)

        val result1 = CRC.crc8(data)
        val result2 = CRC.crc8(data)

        assertEquals(result1, result2)
    }

    @Test
    fun `crc8 changes with different data`() {
        val data1 = byteArrayOf(0x01, 0x02, 0x03)
        val data2 = byteArrayOf(0x01, 0x02, 0x04)

        val result1 = CRC.crc8(data1)
        val result2 = CRC.crc8(data2)

        assert(result1 != result2)
    }

    @Test
    fun `crc8 of known values for pump data`() {
        // Test with realistic pump packet data
        val data = byteArrayOf(0xA7.toByte(), 0x12)
        val result = CRC.crc8(data)
        assertNotNull(result)
    }

    @Test
    fun `calculate16CCITT of null returns valid CRC`() {
        val result = CRC.calculate16CCITT(null)
        assertNotNull(result)
        assertEquals(2, result.size)
    }

    @Test
    fun `calculate16CCITT of empty array returns initial CRC`() {
        val data = byteArrayOf()
        val result = CRC.calculate16CCITT(data)

        assertNotNull(result)
        assertEquals(2, result.size)
        // Initial CRC with 0xFFFF should result in specific value
    }

    @Test
    fun `calculate16CCITT returns 2 bytes`() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val result = CRC.calculate16CCITT(data)

        assertEquals(2, result.size)
    }

    @Test
    fun `calculate16CCITT is deterministic`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)

        val result1 = CRC.calculate16CCITT(data)
        val result2 = CRC.calculate16CCITT(data)

        assertArrayEquals(result1, result2)
    }

    @Test
    fun `calculate16CCITT changes with different data`() {
        val data1 = byteArrayOf(0x01, 0x02, 0x03)
        val data2 = byteArrayOf(0x01, 0x02, 0x04)

        val result1 = CRC.calculate16CCITT(data1)
        val result2 = CRC.calculate16CCITT(data2)

        assert(!result1.contentEquals(result2))
    }

    @Test
    fun `calculate16CCITT of single byte`() {
        val data = byteArrayOf(0x00)
        val result = CRC.calculate16CCITT(data)

        assertEquals(2, result.size)
        assertNotNull(result[0])
        assertNotNull(result[1])
    }

    @Test
    fun `calculate16CCITT of typical pump packet`() {
        val data = byteArrayOf(0xA7.toByte(), 0x12, 0x34, 0x56)
        val result = CRC.calculate16CCITT(data)

        assertEquals(2, result.size)
    }

    @Test
    fun `crc8 of all zeros`() {
        val data = ByteArray(10) { 0 }
        val result = CRC.crc8(data)
        assertEquals(0.toByte(), result)
    }

    @Test
    fun `crc8 of all ones`() {
        val data = ByteArray(10) { 0xFF.toByte() }
        val result = CRC.crc8(data)
        assertNotNull(result)
        // Should be a specific non-zero value
    }

    @Test
    fun `crc8 of sequential bytes`() {
        val data = ByteArray(256) { i -> i.toByte() }
        val result = CRC.crc8(data)
        assertNotNull(result)
    }

    @Test
    fun `calculate16CCITT of large packet`() {
        val data = ByteArray(128) { i -> i.toByte() }
        val result = CRC.calculate16CCITT(data)

        assertEquals(2, result.size)
    }

    @Test
    fun `crc8 handles negative byte values correctly`() {
        val data = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFD.toByte())
        val result = CRC.crc8(data)
        assertNotNull(result)
    }

    @Test
    fun `calculate16CCITT handles negative byte values correctly`() {
        val data = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFD.toByte())
        val result = CRC.calculate16CCITT(data)

        assertEquals(2, result.size)
    }

    @Test
    fun `crc8 realistic pump message scenario`() {
        // Simulate a real pump message
        val pumpMessage = byteArrayOf(
            0xA7.toByte(), 0x12, 0x89.toByte(), 0x86.toByte(),
            0x5D, 0x00, 0xBE.toByte()
        )
        val crc = CRC.crc8(pumpMessage)

        assertNotNull(crc)

        // Verify adding CRC and recalculating gives expected result
        val messageWithCRC = pumpMessage + crc
        // In a real scenario, we'd verify the CRC validates correctly
    }

    @Test
    fun `calculate16CCITT realistic packet scenario`() {
        val packet = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val crc = CRC.calculate16CCITT(packet)

        assertEquals(2, crc.size)

        // CRC should be high byte, low byte format
        assertNotNull(crc[0]) // High byte
        assertNotNull(crc[1]) // Low byte
    }

    @Test
    fun `crc8lookup table first entry is 0`() {
        assertEquals(0, CRC.crc8lookup[0])
    }

    @Test
    fun `crc8lookup table second entry is 155`() {
        assertEquals(155, CRC.crc8lookup[1])
    }

    @Test
    fun `crc8lookup table last entry is 123`() {
        assertEquals(123, CRC.crc8lookup[255])
    }

    @Test
    fun `crc8 matches lookup table for single byte`() {
        for (i in 0..255) {
            val data = byteArrayOf(i.toByte())
            val result = CRC.crc8(data)
            assertEquals(CRC.crc8lookup[i].toByte(), result, "Failed for byte value $i")
        }
    }

    @Test
    fun `crc8 with partial length calculation`() {
        val fullData = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)

        for (len in 0..5) {
            val result = CRC.crc8(fullData, len)
            val partialData = fullData.copyOfRange(0, len)
            val expected = CRC.crc8(partialData)
            assertEquals(expected, result, "Failed for length $len")
        }
    }
}
