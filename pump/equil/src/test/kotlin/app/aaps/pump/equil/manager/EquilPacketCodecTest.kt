package app.aaps.pump.equil.manager

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EquilPacketCodecTest {

    // --- Bit manipulation (moved from BaseCmdTest) ---

    @Test
    fun `clearEndBit should clear bit 7`() {
        assertEquals(0x7F.toByte(), EquilPacketCodec.clearEndBit(0xFF.toByte()))
        assertEquals(0x00.toByte(), EquilPacketCodec.clearEndBit(0x80.toByte()))
        assertEquals(0x00.toByte(), EquilPacketCodec.clearEndBit(0x00.toByte()))
    }

    @Test
    fun `setEndBit should set bit 7`() {
        assertEquals(0x80.toByte(), EquilPacketCodec.setEndBit(0x00.toByte()))
        assertEquals(0xFF.toByte(), EquilPacketCodec.setEndBit(0x7F.toByte()))
        assertEquals(0xFF.toByte(), EquilPacketCodec.setEndBit(0xFF.toByte()))
    }

    @Test
    fun `setEndBit and clearEndBit should be inverse for bit 7`() {
        val original = 0x3F.toByte()
        val withEndBit = EquilPacketCodec.setEndBit(original)
        val cleared = EquilPacketCodec.clearEndBit(withEndBit)
        assertEquals(original, cleared)
    }

    @Test
    fun `bit manipulation should preserve other bits`() {
        val original = 0x3F.toByte() // 00111111
        val withBit7 = EquilPacketCodec.setEndBit(original)
        assertEquals(0xBF.toByte(), withBit7) // 10111111
        val cleared = EquilPacketCodec.clearEndBit(withBit7)
        assertEquals(0x3F.toByte(), cleared) // 00111111
    }

    @Test
    fun `isEnd should return true when bit 7 is set`() {
        assertTrue(EquilPacketCodec.isEnd(0x80.toByte()))
        assertTrue(EquilPacketCodec.isEnd(0xFF.toByte()))
        assertTrue(EquilPacketCodec.isEnd(0xC0.toByte()))
    }

    @Test
    fun `isEnd should return false when bit 7 is not set`() {
        assertFalse(EquilPacketCodec.isEnd(0x00.toByte()))
        assertFalse(EquilPacketCodec.isEnd(0x7F.toByte()))
        assertFalse(EquilPacketCodec.isEnd(0x3F.toByte()))
    }

    @Test
    fun `isEnd should correctly identify all end packets`() {
        for (i in 128..255) assertTrue(EquilPacketCodec.isEnd(i.toByte()))
        for (i in 0..127) assertFalse(EquilPacketCodec.isEnd(i.toByte()))
    }

    @Test
    fun `getIndex should extract lower 6 bits`() {
        assertEquals(0, EquilPacketCodec.getIndex(0x00.toByte()))
        assertEquals(1, EquilPacketCodec.getIndex(0x01.toByte()))
        assertEquals(63, EquilPacketCodec.getIndex(0x3F.toByte()))
        assertEquals(63, EquilPacketCodec.getIndex(0xFF.toByte()))
        assertEquals(0, EquilPacketCodec.getIndex(0x80.toByte()))
        assertEquals(15, EquilPacketCodec.getIndex(0x0F.toByte()))
    }

    @Test
    fun `getIndex should work for all 6-bit values`() {
        for (i in 0..63) assertEquals(i, EquilPacketCodec.getIndex(i.toByte()))
    }

    // --- Packet validation ---

    @Test
    fun `validatePacket should accept valid CRC8`() {
        val response = EquilResponse(0L)
        // Build a packet with valid CRC8
        val data = ByteArray(16)
        data[0] = 0x00
        data[1] = 0x00
        data[2] = 0x10
        data[3] = 0x00  // offset 0
        data[4] = 0x00  // index 0, not end
        data[5] = Crc.crc8Maxim(data.copyOfRange(0, 5)).toByte()
        assertTrue(EquilPacketCodec.validatePacket(data, response))
    }

    @Test
    fun `validatePacket should reject invalid CRC8`() {
        val response = EquilResponse(0L)
        val data = ByteArray(16)
        data[0] = 0x00
        data[1] = 0x00
        data[2] = 0x10
        data[3] = 0x00
        data[4] = 0x00
        data[5] = 0xFF.toByte() // wrong CRC
        assertFalse(EquilPacketCodec.validatePacket(data, response))
    }

    @Test
    fun `validatePacket should reject duplicate offset`() {
        val response = EquilResponse(0L)
        // Add a packet with offset 0
        val first = ByteArray(16)
        first[3] = 0x00  // offset 0
        response.add(java.nio.ByteBuffer.wrap(first))

        // Try to add another packet with the same offset
        val data = ByteArray(16)
        data[3] = 0x00  // same offset
        data[5] = Crc.crc8Maxim(data.copyOfRange(0, 5)).toByte()
        assertFalse(EquilPacketCodec.validatePacket(data, response))
    }

    @Test
    fun `validatePacket should accept different offset`() {
        val response = EquilResponse(0L)
        val first = ByteArray(16)
        first[3] = 0x00  // offset 0
        response.add(java.nio.ByteBuffer.wrap(first))

        val data = ByteArray(16)
        data[3] = 0x0A  // offset 10 (different)
        data[5] = Crc.crc8Maxim(data.copyOfRange(0, 5)).toByte()
        assertTrue(EquilPacketCodec.validatePacket(data, response))
    }

    // --- Build + Parse round-trip ---

    @Test
    fun `buildPackets should create valid packets with CRC8`() {
        val model = EquilCmdModel()
        model.tag = "00112233445566778899AABBCCDDEEFF"  // 16 bytes
        model.iv = "001122334455667788990011"            // 12 bytes
        model.ciphertext = "AABBCCDD"                    // 4 bytes
        val port = "0F0F0000"

        val response = EquilPacketCodec.buildPackets(model, port, 0, System.currentTimeMillis())

        assertNotNull(response)
        assertTrue(response.send.size > 0)

        // Verify CRC8 on every packet
        for (buf in response.send) {
            val bytes = buf.array()
            val expectedCrc = Crc.crc8Maxim(bytes.copyOfRange(0, 5))
            assertEquals(expectedCrc.toByte(), bytes[5], "CRC8 mismatch on packet")
        }

        // Last packet should have end bit set
        val lastPacket = response.send.last.array()
        assertTrue(EquilPacketCodec.isEnd(lastPacket[4]))

        // Non-last packets should not have end bit
        if (response.send.size > 1) {
            val firstPacket = response.send.first.array()
            assertFalse(EquilPacketCodec.isEnd(firstPacket[4]))
        }
    }

    @Test
    fun `buildPackets then parseModel should round-trip correctly`() {
        val original = EquilCmdModel()
        original.tag = "00112233445566778899AABBCCDDEEFF"   // 16 bytes
        original.iv = "001122334455667788990011"             // 12 bytes
        original.ciphertext = "AABBCCDD"                     // 4 bytes
        val port = "0F0F0000"

        val response = EquilPacketCodec.buildPackets(original, port, 0, System.currentTimeMillis())
        val parsed = EquilPacketCodec.parseModel(response)

        assertEquals(original.tag?.lowercase(), parsed.tag)
        assertEquals(original.iv?.lowercase(), parsed.iv)
        assertEquals(original.ciphertext?.lowercase(), parsed.ciphertext)
    }

    @Test
    fun `buildPackets with large ciphertext should create multiple packets`() {
        val model = EquilCmdModel()
        model.tag = "00112233445566778899AABBCCDDEEFF"  // 16 bytes
        model.iv = "001122334455667788990011"            // 12 bytes
        // Large ciphertext: 40 bytes = 80 hex chars
        model.ciphertext = "AA".repeat(40)
        val port = "0F0F0000"

        val response = EquilPacketCodec.buildPackets(model, port, 5, System.currentTimeMillis())

        assertTrue(response.send.size > 1, "Large payload should produce multiple packets")

        // All packets should have valid CRC8
        for (buf in response.send) {
            val bytes = buf.array()
            val expectedCrc = Crc.crc8Maxim(bytes.copyOfRange(0, 5))
            assertEquals(expectedCrc.toByte(), bytes[5])
        }

        // Parse should still work
        val parsed = EquilPacketCodec.parseModel(response)
        assertEquals(model.tag?.lowercase(), parsed.tag)
        assertEquals(model.iv?.lowercase(), parsed.iv)
        assertEquals(model.ciphertext?.lowercase(), parsed.ciphertext)
    }

    @Test
    fun `buildPackets with empty ciphertext should still produce valid packets`() {
        val model = EquilCmdModel()
        model.tag = "00112233445566778899AABBCCDDEEFF"
        model.iv = "001122334455667788990011"
        model.ciphertext = ""
        val port = "0F0F0000"

        val response = EquilPacketCodec.buildPackets(model, port, 0, System.currentTimeMillis())
        assertNotNull(response)
        assertTrue(response.send.size > 0)
    }
}
