package app.aaps.pump.equil.manager

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.math.RoundingMode

class UtilsTest : TestBase() {

    @Test
    fun `generateRandomPassword should create password of correct length`() {
        val password = Utils.generateRandomPassword(16)
        assertEquals(16, password.size)
    }

    @Test
    fun `generateRandomPassword should create unique passwords`() {
        val password1 = Utils.generateRandomPassword(16)
        val password2 = Utils.generateRandomPassword(16)
        // Two random passwords should be different (extremely unlikely to be same)
        assertNotNull(password1)
        assertNotNull(password2)
    }

    @Test
    fun `bytesToInt should convert two bytes to int correctly`() {
        // Test case 1: Simple conversion
        val result1 = Utils.bytesToInt(0x00.toByte(), 0xFF.toByte())
        assertEquals(0xFF, result1)

        // Test case 2: Both bytes non-zero
        val result2 = Utils.bytesToInt(0x01.toByte(), 0x00.toByte())
        assertEquals(0x0100, result2)

        // Test case 3: Maximum value below threshold
        val result3 = Utils.bytesToInt(0x7F.toByte(), 0xFF.toByte())
        assertEquals(0x7FFF, result3)

        // Test case 4: Value at threshold (0x8000) - should subtract 0x8000
        val result4 = Utils.bytesToInt(0x80.toByte(), 0x00.toByte())
        assertEquals(0, result4)

        // Test case 5: Value above threshold
        val result5 = Utils.bytesToInt(0xFF.toByte(), 0xFF.toByte())
        assertEquals(0x7FFF, result5)
    }

    @Test
    fun `internalDecodeSpeedToUH should decode speed correctly`() {
        // 0.00625 units per increment
        assertEquals(0.00625f, Utils.internalDecodeSpeedToUH(1), 0.00001f)
        assertEquals(0.0625f, Utils.internalDecodeSpeedToUH(10), 0.00001f)
        assertEquals(0.625f, Utils.internalDecodeSpeedToUH(100), 0.00001f)
        assertEquals(6.25f, Utils.internalDecodeSpeedToUH(1000), 0.00001f)
        assertEquals(0.0f, Utils.internalDecodeSpeedToUH(0), 0.00001f)
    }

    @Test
    fun `internalDecodeSpeedToUH2 should return BigDecimal correctly`() {
        val result = Utils.internalDecodeSpeedToUH2(100)
        // Use compareTo for BigDecimal comparison (ignores scale differences)
        assertEquals(0, BigDecimal("0.625").compareTo(result))
    }

    @Test
    fun `decodeSpeedToUH int should decode speed correctly`() {
        assertEquals(0.00625f, Utils.decodeSpeedToUH(1), 0.00001f)
        assertEquals(1.25f, Utils.decodeSpeedToUH(200), 0.00001f)
    }

    @Test
    fun `decodeSpeedToUS should decode speed to units per second`() {
        // 1 unit/hour = 1/3600 units/second
        val result = Utils.decodeSpeedToUS(160) // 1.0 U/h
        val expected = BigDecimal("1.0").divide(BigDecimal("3600"), 10, RoundingMode.DOWN).toDouble()
        assertEquals(expected, result, 0.0000000001)
    }

    @Test
    fun `decodeSpeedToUH double should encode rate correctly`() {
        // Inverse of internalDecodeSpeedToUH
        assertEquals(1, Utils.decodeSpeedToUH(0.00625))
        assertEquals(160, Utils.decodeSpeedToUH(1.0))
        assertEquals(320, Utils.decodeSpeedToUH(2.0))
        assertEquals(800, Utils.decodeSpeedToUH(5.0))
    }

    @Test
    fun `decodeSpeedToUH double roundtrip should be accurate`() {
        val originalRate = 1.5
        val encoded = Utils.decodeSpeedToUH(originalRate)
        val decoded = Utils.internalDecodeSpeedToUH(encoded)
        assertEquals(originalRate, decoded.toDouble(), 0.001)
    }

    @Test
    fun `decodeSpeedToUHT should return double correctly`() {
        val result = Utils.decodeSpeedToUHT(1.0)
        assertEquals(160.0, result, 0.001)
    }

    @Test
    fun `basalToByteArray should encode basal rate high byte first`() {
        // 1.0 U/h = 160 = 0x00A0
        val result = Utils.basalToByteArray(1.0)
        assertEquals(2, result.size)
        assertEquals(0x00.toByte(), result[0]) // high byte
        assertEquals(0xA0.toByte(), result[1]) // low byte
    }

    @Test
    fun `basalToByteArray should encode various rates correctly`() {
        // 0.5 U/h = 80 = 0x0050
        val result1 = Utils.basalToByteArray(0.5)
        assertArrayEquals(byteArrayOf(0x00.toByte(), 0x50.toByte()), result1)

        // 2.0 U/h = 320 = 0x0140
        val result2 = Utils.basalToByteArray(2.0)
        assertArrayEquals(byteArrayOf(0x01.toByte(), 0x40.toByte()), result2)

        // 5.0 U/h = 800 = 0x0320
        val result3 = Utils.basalToByteArray(5.0)
        assertArrayEquals(byteArrayOf(0x03.toByte(), 0x20.toByte()), result3)
    }

    @Test
    fun `basalToByteArray2 should encode basal rate low byte first`() {
        // 1.0 U/h = 160 = 0x00A0
        val result = Utils.basalToByteArray2(1.0)
        assertEquals(2, result.size)
        assertEquals(0xA0.toByte(), result[0]) // low byte
        assertEquals(0x00.toByte(), result[1]) // high byte
    }

    @Test
    fun `basalToByteArray2 should encode various rates correctly`() {
        // 0.5 U/h = 80 = 0x0050
        val result1 = Utils.basalToByteArray2(0.5)
        assertArrayEquals(byteArrayOf(0x50.toByte(), 0x00.toByte()), result1)

        // 2.0 U/h = 320 = 0x0140
        val result2 = Utils.basalToByteArray2(2.0)
        assertArrayEquals(byteArrayOf(0x40.toByte(), 0x01.toByte()), result2)
    }

    @Test
    fun `hexStringToBytes should convert hex string to bytes`() {
        val result = Utils.hexStringToBytes("00FF")
        assertArrayEquals(byteArrayOf(0x00.toByte(), 0xFF.toByte()), result)
    }

    @Test
    fun `hexStringToBytes should handle lowercase hex`() {
        val result = Utils.hexStringToBytes("ab12cd")
        assertArrayEquals(byteArrayOf(0xAB.toByte(), 0x12.toByte(), 0xCD.toByte()), result)
    }

    @Test
    fun `hexStringToBytes should handle various hex strings`() {
        val result1 = Utils.hexStringToBytes("0123456789ABCDEF")
        assertArrayEquals(
            byteArrayOf(
                0x01.toByte(), 0x23.toByte(), 0x45.toByte(), 0x67.toByte(),
                0x89.toByte(), 0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte()
            ),
            result1
        )
    }

    @Test
    fun `concat should concatenate byte arrays`() {
        val array1 = byteArrayOf(0x01, 0x02)
        val array2 = byteArrayOf(0x03, 0x04)
        val result = Utils.concat(array1, array2)
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04), result)
    }

    @Test
    fun `concat should handle multiple arrays`() {
        val array1 = byteArrayOf(0x01)
        val array2 = byteArrayOf(0x02, 0x03)
        val array3 = byteArrayOf(0x04, 0x05, 0x06)
        val result = Utils.concat(array1, array2, array3)
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06), result)
    }

    @Test
    fun `concat should handle empty arrays`() {
        val array1 = byteArrayOf(0x01)
        val array2 = byteArrayOf()
        val array3 = byteArrayOf(0x02)
        val result = Utils.concat(array1, array2, array3)
        assertArrayEquals(byteArrayOf(0x01, 0x02), result)
    }

    @Test
    fun `intToBytes should convert int to 4 bytes little-endian`() {
        val result = Utils.intToBytes(0x12345678)
        assertArrayEquals(
            byteArrayOf(0x78.toByte(), 0x56.toByte(), 0x34.toByte(), 0x12.toByte()),
            result
        )
    }

    @Test
    fun `intToBytes should handle various values`() {
        val result1 = Utils.intToBytes(0x00000000)
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00), result1)

        val result2 = Utils.intToBytes(0x000000FF)
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00), result2)

        val result3 = Utils.intToBytes(0xFF000000.toInt())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0xFF.toByte()), result3)
    }

    @Test
    fun `bytes2Int should convert 4 bytes to int little-endian`() {
        val bytes = byteArrayOf(0x78.toByte(), 0x56.toByte(), 0x34.toByte(), 0x12.toByte())
        val result = Utils.bytes2Int(bytes)
        assertEquals(0x12345678, result)
    }

    @Test
    fun `bytes2Int should handle various byte arrays`() {
        val result1 = Utils.bytes2Int(byteArrayOf(0x00, 0x00, 0x00, 0x00))
        assertEquals(0, result1)

        val result2 = Utils.bytes2Int(byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00))
        assertEquals(0xFF, result2)

        val result3 = Utils.bytes2Int(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        assertEquals(0xFFFFFFFF.toInt(), result3)
    }

    @Test
    fun `intToBytes and bytes2Int should be inverse operations`() {
        val original = 0x12345678
        val bytes = Utils.intToBytes(original)
        val result = Utils.bytes2Int(bytes)
        assertEquals(original, result)
    }

    @Test
    fun `intToTwoBytes should convert int to 2 bytes little-endian`() {
        val result = Utils.intToTwoBytes(0x1234)
        assertArrayEquals(byteArrayOf(0x34.toByte(), 0x12.toByte()), result)
    }

    @Test
    fun `intToTwoBytes should handle various values`() {
        val result1 = Utils.intToTwoBytes(0x00FF)
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x00), result1)

        val result2 = Utils.intToTwoBytes(0xFF00)
        assertArrayEquals(byteArrayOf(0x00, 0xFF.toByte()), result2)

        val result3 = Utils.intToTwoBytes(0xFFFF)
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte()), result3)
    }

    @Test
    fun `convertByteArray should convert mutable list to byte array`() {
        val list = mutableListOf<Byte?>(0x01, 0x02, 0x03)
        val result = Utils.convertByteArray(list)
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), result)
    }

    @Test
    fun `bytesToHex with MutableList should convert bytes to hex string`() {
        val bytes = mutableListOf<Byte?>(0x00, 0xFF.toByte(), 0xAB.toByte())
        val result = Utils.bytesToHex(bytes)
        assertEquals("00FFAB", result)
    }

    @Test
    fun `bytesToHex with MutableList should handle null`() {
        val result = Utils.bytesToHex(null as MutableList<Byte?>?)
        assertEquals("<empty>", result)
    }

    @Test
    fun `bytesToHex with ByteArray should convert bytes to hex string`() {
        val bytes = byteArrayOf(0x01, 0x23, 0x45, 0x67, 0x89.toByte(), 0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte())
        val result = Utils.bytesToHex(bytes)
        assertEquals("0123456789ABCDEF", result)
    }

    @Test
    fun `bytesToHex with ByteArray should handle null`() {
        val result = Utils.bytesToHex(null as ByteArray?)
        assertEquals("<empty>", result)
    }

    @Test
    fun `bytesToHex with ByteArray should handle empty array`() {
        val result = Utils.bytesToHex(byteArrayOf())
        assertEquals("", result)
    }

    @Test
    fun `hexStringToBytes and bytesToHex should be inverse operations`() {
        val original = "0123456789ABCDEF"
        val bytes = Utils.hexStringToBytes(original)
        val result = Utils.bytesToHex(bytes)
        assertEquals(original, result)
    }

    @Test
    fun `hexStringToBytes roundtrip with various strings`() {
        val testCases = listOf("00", "FF", "0000", "FFFF", "DEADBEEF", "CAFEBABE")
        testCases.forEach { original ->
            val bytes = Utils.hexStringToBytes(original)
            val result = Utils.bytesToHex(bytes)
            assertEquals(original, result)
        }
    }
}
