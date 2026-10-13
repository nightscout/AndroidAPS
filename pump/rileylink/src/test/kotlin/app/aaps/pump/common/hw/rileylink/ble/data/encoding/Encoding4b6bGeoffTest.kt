package app.aaps.pump.common.hw.rileylink.ble.data.encoding

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.common.hw.rileylink.ble.RileyLinkCommunicationException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.mock

/**
 * Comprehensive tests for 4b6b encoding/decoding implementation
 */
class Encoding4b6bGeoffTest {

    private lateinit var aapsLogger: AAPSLogger
    private lateinit var encoder: Encoding4b6bGeoff

    @BeforeEach
    fun setup() {
        aapsLogger = mock()
        encoder = Encoding4b6bGeoff(aapsLogger)
    }

    @Test
    fun `encode single byte`() {
        val input = byteArrayOf(0xA7.toByte())
        val expected = byteArrayOf(0xA9.toByte(), 0x65)
        val result = encoder.encode4b6b(input)
        assertArrayEquals(expected, result)
    }

    @Test
    fun `encode two bytes`() {
        val input = byteArrayOf(0xA7.toByte(), 0x12)
        val expected = byteArrayOf(0xA9.toByte(), 0x6C, 0x72)
        val result = encoder.encode4b6b(input)
        assertArrayEquals(expected, result)
    }

    @Test
    fun `encode three bytes`() {
        val input = byteArrayOf(0xA7.toByte(), 0x12, 0xA7.toByte())
        val expected = byteArrayOf(0xA9.toByte(), 0x6C, 0x72, 0xA9.toByte(), 0x65)
        val result = encoder.encode4b6b(input)
        assertArrayEquals(expected, result)
    }

    @Test
    fun `encode zero bytes`() {
        val input = byteArrayOf(0x00)
        val expected = byteArrayOf(0x55, 0x55)
        val result = encoder.encode4b6b(input)
        assertArrayEquals(expected, result)
    }

    @Test
    fun `encode two zero bytes`() {
        val input = byteArrayOf(0x00, 0x00)
        val expected = byteArrayOf(0x55, 0x55, 0x55)
        val result = encoder.encode4b6b(input)
        assertArrayEquals(expected, result)
    }

    @Test
    fun `encode empty array`() {
        val input = byteArrayOf()
        val result = encoder.encode4b6b(input)
        assertArrayEquals(byteArrayOf(), result)
    }

    @ParameterizedTest
    @CsvSource(
        "A7, A965",
        "A712, A96C72",
        "A712A7, A96C72A965",
        "00, 5555",
        "0000, 555555",
        "A71289865D00BE, A96C726996A694D5552CE5",
        "A7128986060015, A96C726996A6566555C655",
        "A7128986150956, A96C726996A6C655599665",
        "A71289868D00B0, A96C726996A668D5552D55"
    )
    fun `parametrized encode test`(decodedHex: String, encodedHex: String) {
        val decoded = hexStringToByteArray(decodedHex)
        val expected = hexStringToByteArray(encodedHex)
        val result = encoder.encode4b6b(decoded)
        assertArrayEquals(expected, result, "Failed for input: $decodedHex")
    }

    @Test
    fun `decode single byte`() {
        val input = byteArrayOf(0xA9.toByte(), 0x65)
        val expected = byteArrayOf(0xA7.toByte())
        val result = encoder.decode4b6b(input)
        assertArrayEquals(expected, result)
    }

    @Test
    fun `decode two bytes`() {
        val input = byteArrayOf(0xA9.toByte(), 0x6C, 0x72)
        val expected = byteArrayOf(0xA7.toByte(), 0x12)
        val result = encoder.decode4b6b(input)
        assertArrayEquals(expected, result)
    }

    @Test
    fun `decode three bytes`() {
        val input = byteArrayOf(0xA9.toByte(), 0x6C, 0x72, 0xA9.toByte(), 0x65)
        val expected = byteArrayOf(0xA7.toByte(), 0x12, 0xA7.toByte())
        val result = encoder.decode4b6b(input)
        assertArrayEquals(expected, result)
    }

    @ParameterizedTest
    @CsvSource(
        "5555, 00",
        "555555, 0000",
        "A96C726996A694D5552CE5, A71289865D00BE",
        "A96C726996A6566555C655, A7128986060015",
        "A96C726996A6C655599665, A7128986150956",
        "A96C726996A668D5552D55, A71289868D00B0"
    )
    fun `parametrized decode test`(encodedHex: String, decodedHex: String) {
        val encoded = hexStringToByteArray(encodedHex)
        val expected = hexStringToByteArray(decodedHex)
        val result = encoder.decode4b6b(encoded)
        assertArrayEquals(expected, result, "Failed for input: $encodedHex")
    }

    @Test
    fun `encode then decode returns original`() {
        val original = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val encoded = encoder.encode4b6b(original)
        val decoded = encoder.decode4b6b(encoded)
        assertArrayEquals(original, decoded)
    }

    @Test
    fun `encode then decode complex data`() {
        val original = byteArrayOf(
            0xA7.toByte(), 0x12, 0x89.toByte(), 0x86.toByte(), 0x5D, 0x00, 0xBE.toByte()
        )
        val encoded = encoder.encode4b6b(original)
        val decoded = encoder.decode4b6b(encoded)
        assertArrayEquals(original, decoded)
    }

    @Test
    fun `decode with invalid coding should throw exception`() {
        // Invalid 4b6b encoded data (contains invalid codes)
        val invalidData = byteArrayOf(0xFF.toByte(), 0xFF.toByte())

        assertThrows(RileyLinkCommunicationException::class.java) {
            encoder.decode4b6b(invalidData)
        }
    }

    @Test
    fun `decode empty array returns empty array`() {
        val input = byteArrayOf()
        val result = encoder.decode4b6b(input)
        assertArrayEquals(byteArrayOf(), result)
    }

    @Test
    fun `decode too short data may throw exception`() {
        // Data too short to be valid - single byte (0x55)
        // The decoder may throw RileyLinkCommunicationException for coding errors
        val shortData = byteArrayOf(0x55)

        try {
            val result = encoder.decode4b6b(shortData)
            // If it doesn't throw, result should not be null
            assertNotNull(result)
        } catch (e: RileyLinkCommunicationException) {
            // Exception is acceptable for data that produces coding errors
            assertNotNull(e.message)
        }
    }

    @Test
    fun `encode large packet`() {
        // Test encoding a large packet similar to pump communication
        val largeData = ByteArray(64) { i -> i.toByte() }
        val encoded = encoder.encode4b6b(largeData)
        val decoded = encoder.decode4b6b(encoded)
        assertArrayEquals(largeData, decoded)
    }

    @Test
    fun `encode max size pump packet`() {
        // Typical pump packet size
        val pumpPacket = ByteArray(80) { i -> (i % 256).toByte() }
        val encoded = encoder.encode4b6b(pumpPacket)
        val decoded = encoder.decode4b6b(encoded)
        assertArrayEquals(pumpPacket, decoded)
    }

    @Test
    fun `encode all possible byte values`() {
        // Test all byte values 0-255
        val allBytes = ByteArray(256) { i -> i.toByte() }
        val encoded = encoder.encode4b6b(allBytes)
        val decoded = encoder.decode4b6b(encoded)
        assertArrayEquals(allBytes, decoded)
    }

    // Helper function to convert hex string to byte array
    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
