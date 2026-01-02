package app.aaps.pump.common.hw.rileylink.ble.data

import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.ble.data.encoding.Encoding4b6b
import app.aaps.pump.common.hw.rileylink.ble.data.encoding.Encoding4b6bGeoff
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.apache.commons.lang3.NotImplementedException

/**
 * Tests for RadioPacket class
 */
class RadioPacketTest {

    private lateinit var rileyLinkUtil: RileyLinkUtil
    private lateinit var encoding4b6b: Encoding4b6b

    @BeforeEach
    fun setup() {
        rileyLinkUtil = mock()
        encoding4b6b = mock()
        whenever(rileyLinkUtil.encoding4b6b).thenReturn(encoding4b6b)
    }

    @Test
    fun `getEncoded with Manchester encoding returns original packet`() {
        whenever(rileyLinkUtil.encoding).thenReturn(RileyLinkEncodingType.Manchester)

        val packetData = byteArrayOf(0x01, 0x02, 0x03)
        val radioPacket = RadioPacket(rileyLinkUtil, packetData)

        val encoded = radioPacket.getEncoded()
        assertArrayEquals(packetData, encoded)
    }

    @Test
    fun `getEncoded with FourByteSixByteRileyLink returns packet with CRC`() {
        whenever(rileyLinkUtil.encoding).thenReturn(RileyLinkEncodingType.FourByteSixByteRileyLink)

        val packetData = byteArrayOf(0x01, 0x02, 0x03)
        val radioPacket = RadioPacket(rileyLinkUtil, packetData)

        val encoded = radioPacket.getEncoded()

        // Should have original data plus CRC byte
        assert(encoded.size == 4)
        // First 3 bytes should match original
        assert(encoded[0] == packetData[0])
        assert(encoded[1] == packetData[1])
        assert(encoded[2] == packetData[2])
    }

    @Test
    fun `getEncoded with FourByteSixByteLocal encodes and adds terminator`() {
        whenever(rileyLinkUtil.encoding).thenReturn(RileyLinkEncodingType.FourByteSixByteLocal)

        val packetData = byteArrayOf(0x01, 0x02, 0x03)
        val expectedEncoded = byteArrayOf(0x55, 0x66, 0x77)

        // Mock the encoding4b6b to return expected encoded data
        whenever(encoding4b6b.encode4b6b(org.mockito.kotlin.any())).thenReturn(expectedEncoded)

        val radioPacket = RadioPacket(rileyLinkUtil, packetData)
        val encoded = radioPacket.getEncoded()

        // Should have encoded data plus terminator byte (0)
        assert(encoded.size == expectedEncoded.size + 1)
        assert(encoded[encoded.size - 1] == 0.toByte())
    }

    @Test
    fun `getEncoded with unsupported encoding throws NotImplementedException`() {
        whenever(rileyLinkUtil.encoding).thenReturn(RileyLinkEncodingType.None)

        val packetData = byteArrayOf(0x01, 0x02, 0x03)
        val radioPacket = RadioPacket(rileyLinkUtil, packetData)

        assertThrows(NotImplementedException::class.java) {
            radioPacket.getEncoded()
        }
    }

    @Test
    fun `radioPacket with empty data`() {
        whenever(rileyLinkUtil.encoding).thenReturn(RileyLinkEncodingType.Manchester)

        val packetData = byteArrayOf()
        val radioPacket = RadioPacket(rileyLinkUtil, packetData)

        val encoded = radioPacket.getEncoded()
        assertArrayEquals(byteArrayOf(), encoded)
    }

    @Test
    fun `radioPacket with single byte`() {
        whenever(rileyLinkUtil.encoding).thenReturn(RileyLinkEncodingType.Manchester)

        val packetData = byteArrayOf(0x42)
        val radioPacket = RadioPacket(rileyLinkUtil, packetData)

        val encoded = radioPacket.getEncoded()
        assertArrayEquals(packetData, encoded)
    }

    @Test
    fun `radioPacket with maximum size data`() {
        whenever(rileyLinkUtil.encoding).thenReturn(RileyLinkEncodingType.Manchester)

        val packetData = ByteArray(80) { i -> i.toByte() }
        val radioPacket = RadioPacket(rileyLinkUtil, packetData)

        val encoded = radioPacket.getEncoded()
        assertArrayEquals(packetData, encoded)
    }

    @Test
    fun `pkt property returns original data`() {
        whenever(rileyLinkUtil.encoding).thenReturn(RileyLinkEncodingType.Manchester)

        val packetData = byteArrayOf(0x01, 0x02, 0x03)
        val radioPacket = RadioPacket(rileyLinkUtil, packetData)

        assertArrayEquals(packetData, radioPacket.pkt)
    }
}
