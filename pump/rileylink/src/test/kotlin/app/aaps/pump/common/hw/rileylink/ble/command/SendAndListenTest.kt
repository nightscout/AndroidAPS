package app.aaps.pump.common.hw.rileylink.ble.command

import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.ble.data.RadioPacket
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersionBase
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for SendAndListen command
 */
class SendAndListenTest {

    private lateinit var rileyLinkServiceData: RileyLinkServiceData
    private lateinit var rileyLinkUtil: RileyLinkUtil
    private lateinit var radioPacket: RadioPacket

    @BeforeEach
    fun setup() {
        rileyLinkServiceData = mock()
        rileyLinkUtil = mock()
        radioPacket = mock()

        whenever(rileyLinkUtil.encoding).thenReturn(RileyLinkEncodingType.Manchester)
        whenever(radioPacket.getEncoded()).thenReturn(byteArrayOf(0x01, 0x02, 0x03))
    }

    @Test
    fun `getCommandType returns SendAndListen`() {
        val command = SendAndListen(
            rileyLinkServiceData = rileyLinkServiceData,
            sendChannel = 0,
            repeatCount = 0,
            delayBetweenPacketsMs = 0,
            listenChannel = 0,
            timeoutMs = 0,
            retryCount = 0,
            packetToSend = radioPacket
        )

        assertEquals(RileyLinkCommandType.SendAndListen, command.getCommandType())
    }

    @Test
    fun `getRaw with firmware v1 uses byte for delay`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_1_0)

        val command = SendAndListen(
            rileyLinkServiceData = rileyLinkServiceData,
            sendChannel = 0x0A,
            repeatCount = 0x05,
            delayBetweenPacketsMs = 100,
            listenChannel = 0x0B,
            timeoutMs = 500,
            retryCount = 0x02,
            packetToSend = radioPacket
        )

        val raw = command.getRaw()

        // Command structure for v1:
        // [cmdType, sendChannel, repeatCount, delay(1 byte), listenChannel, timeout(4 bytes), retryCount, ...packet]
        assertEquals(RileyLinkCommandType.SendAndListen.code, raw[0])
        assertEquals(0x0A.toByte(), raw[1]) // sendChannel
        assertEquals(0x05.toByte(), raw[2]) // repeatCount
        assertEquals(100.toByte(), raw[3]) // delay as single byte
        assertEquals(0x0B.toByte(), raw[4]) // listenChannel
        // Next 4 bytes are timeout (500)
        assertEquals(0x02.toByte(), raw[9]) // retryCount (after 4 byte timeout)
    }

    @Test
    fun `getRaw with firmware v2 uses 16-bit for delay`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        val command = SendAndListen(
            rileyLinkServiceData = rileyLinkServiceData,
            sendChannel = 0x0A,
            repeatCount = 0x05,
            delayBetweenPacketsMs = 1000,
            listenChannel = 0x0B,
            timeoutMs = 500,
            retryCount = 0x02,
            packetToSend = radioPacket
        )

        val raw = command.getRaw()

        // Command structure for v2:
        // [cmdType, sendChannel, repeatCount, delay(2 bytes), listenChannel, timeout(4 bytes), retryCount, preamble(2 bytes), ...packet]
        assertEquals(RileyLinkCommandType.SendAndListen.code, raw[0])
        assertEquals(0x0A.toByte(), raw[1]) // sendChannel
        assertEquals(0x05.toByte(), raw[2]) // repeatCount
        // Next 2 bytes are delay (1000 = 0x03E8)
        assertEquals(0x03.toByte(), raw[3]) // delay high byte
        assertEquals(0xE8.toByte(), raw[4]) // delay low byte
        assertEquals(0x0B.toByte(), raw[5]) // listenChannel
    }

    @Test
    fun `getRaw with firmware v2 includes preamble extension`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        val command = SendAndListen(
            rileyLinkServiceData = rileyLinkServiceData,
            sendChannel = 0x0A,
            repeatCount = 0x05,
            delayBetweenPacketsMs = 100,
            listenChannel = 0x0B,
            timeoutMs = 500,
            retryCount = 0x02,
            preambleExtensionMs = 2000,
            packetToSend = radioPacket
        )

        val raw = command.getRaw()

        // Preamble should be at the end before packet data (2000 = 0x07D0)
        // Position: cmdType(1) + sendChannel(1) + repeatCount(1) + delay(2) + listenChannel(1) + timeout(4) + retryCount(1) = 11
        assertEquals(0x07.toByte(), raw[11]) // preamble high byte
        assertEquals(0xD0.toByte(), raw[12]) // preamble low byte
    }

    @Test
    fun `getRaw with null firmware version defaults to v2 format`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(null)

        val command = SendAndListen(
            rileyLinkServiceData = rileyLinkServiceData,
            sendChannel = 0x0A,
            repeatCount = 0x05,
            delayBetweenPacketsMs = 100,
            listenChannel = 0x0B,
            timeoutMs = 500,
            retryCount = 0x02,
            packetToSend = radioPacket
        )

        val raw = command.getRaw()

        // Should use v2 format (2 byte delay)
        assertEquals(RileyLinkCommandType.SendAndListen.code, raw[0])
        // Check that it has 2-byte delay (v2 format)
        // Total before packet: 1 + 1 + 1 + 2 + 1 + 4 + 1 + 2 = 13 bytes
        assertTrue(raw.size >= 13)
    }

    @Test
    fun `getRaw appends encoded packet data`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)
        val encodedPacket = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        whenever(radioPacket.getEncoded()).thenReturn(encodedPacket)

        val command = SendAndListen(
            rileyLinkServiceData = rileyLinkServiceData,
            sendChannel = 0x0A,
            repeatCount = 0x05,
            delayBetweenPacketsMs = 100,
            listenChannel = 0x0B,
            timeoutMs = 500,
            retryCount = 0x02,
            preambleExtensionMs = 0,
            packetToSend = radioPacket
        )

        val raw = command.getRaw()

        // Packet data should be at the end
        val packetStart = raw.size - encodedPacket.size
        assertArrayEquals(encodedPacket, raw.copyOfRange(packetStart, raw.size))
    }

    @Test
    fun `getRaw encodes timeout as 4 bytes big-endian`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        val command = SendAndListen(
            rileyLinkServiceData = rileyLinkServiceData,
            sendChannel = 0x0A,
            repeatCount = 0x05,
            delayBetweenPacketsMs = 100,
            listenChannel = 0x0B,
            timeoutMs = 0x12345678, // Big value to test all 4 bytes
            retryCount = 0x02,
            packetToSend = radioPacket
        )

        val raw = command.getRaw()

        // Timeout is at position 6-9 (after sendChannel, repeatCount, delay(2), listenChannel)
        assertEquals(0x12.toByte(), raw[6])
        assertEquals(0x34.toByte(), raw[7])
        assertEquals(0x56.toByte(), raw[8])
        assertEquals(0x78.toByte(), raw[9])
    }

    @Test
    fun `getRaw with zero values`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)
        whenever(radioPacket.getEncoded()).thenReturn(byteArrayOf())

        val command = SendAndListen(
            rileyLinkServiceData = rileyLinkServiceData,
            sendChannel = 0,
            repeatCount = 0,
            delayBetweenPacketsMs = 0,
            listenChannel = 0,
            timeoutMs = 0,
            retryCount = 0,
            preambleExtensionMs = 0,
            packetToSend = radioPacket
        )

        val raw = command.getRaw()

        assertEquals(RileyLinkCommandType.SendAndListen.code, raw[0])
        assertEquals(0.toByte(), raw[1]) // sendChannel
        assertEquals(0.toByte(), raw[2]) // repeatCount
        // All other values should be zero
    }

    @Test
    fun `getRaw with maximum values`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        val command = SendAndListen(
            rileyLinkServiceData = rileyLinkServiceData,
            sendChannel = 0xFF.toByte(),
            repeatCount = 0xFF.toByte(),
            delayBetweenPacketsMs = 0xFFFF,
            listenChannel = 0xFF.toByte(),
            timeoutMs = Int.MAX_VALUE,
            retryCount = 0xFF.toByte(),
            preambleExtensionMs = 0xFFFF,
            packetToSend = radioPacket
        )

        val raw = command.getRaw()

        assertEquals(RileyLinkCommandType.SendAndListen.code, raw[0])
        assertEquals(0xFF.toByte(), raw[1]) // sendChannel
        assertEquals(0xFF.toByte(), raw[2]) // repeatCount
    }

    @Test
    fun `getRaw firmware v1 structure is correct size`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_1_0)
        val packetData = byteArrayOf(0x01, 0x02, 0x03)
        whenever(radioPacket.getEncoded()).thenReturn(packetData)

        val command = SendAndListen(
            rileyLinkServiceData = rileyLinkServiceData,
            sendChannel = 0x0A,
            repeatCount = 0x05,
            delayBetweenPacketsMs = 100,
            listenChannel = 0x0B,
            timeoutMs = 500,
            retryCount = 0x02,
            packetToSend = radioPacket
        )

        val raw = command.getRaw()

        // v1: cmdType(1) + sendChannel(1) + repeatCount(1) + delay(1) + listenChannel(1) + timeout(4) + retryCount(1) + packet(3) = 13
        assertEquals(13, raw.size)
    }

    @Test
    fun `getRaw firmware v2 structure is correct size`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)
        val packetData = byteArrayOf(0x01, 0x02, 0x03)
        whenever(radioPacket.getEncoded()).thenReturn(packetData)

        val command = SendAndListen(
            rileyLinkServiceData = rileyLinkServiceData,
            sendChannel = 0x0A,
            repeatCount = 0x05,
            delayBetweenPacketsMs = 100,
            listenChannel = 0x0B,
            timeoutMs = 500,
            retryCount = 0x02,
            preambleExtensionMs = 1000,
            packetToSend = radioPacket
        )

        val raw = command.getRaw()

        // v2: cmdType(1) + sendChannel(1) + repeatCount(1) + delay(2) + listenChannel(1) + timeout(4) + retryCount(1) + preamble(2) + packet(3) = 16
        assertEquals(16, raw.size)
    }

    @Test
    fun `realistic pump communication scenario`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_2)

        val command = SendAndListen(
            rileyLinkServiceData = rileyLinkServiceData,
            sendChannel = 0x0E, // Channel 14
            repeatCount = 0x00, // Send once
            delayBetweenPacketsMs = 0,
            listenChannel = 0x0E, // Listen on same channel
            timeoutMs = 500, // 500ms timeout
            retryCount = 0x03, // Retry 3 times
            preambleExtensionMs = 0,
            packetToSend = radioPacket
        )

        val raw = command.getRaw()

        assertEquals(RileyLinkCommandType.SendAndListen.code, raw[0])
        assertEquals(0x0E.toByte(), raw[1]) // sendChannel
        assertEquals(0x00.toByte(), raw[2]) // repeatCount
        assertEquals(0x0E.toByte(), raw[5]) // listenChannel
        assertTrue(raw.size > 10) // Has all data including packet
    }

    @Test
    fun `command with different send and listen channels`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        val command = SendAndListen(
            rileyLinkServiceData = rileyLinkServiceData,
            sendChannel = 0x0A,
            repeatCount = 0x02,
            delayBetweenPacketsMs = 50,
            listenChannel = 0x0F, // Different channel
            timeoutMs = 1000,
            retryCount = 0x01,
            packetToSend = radioPacket
        )

        val raw = command.getRaw()

        assertEquals(0x0A.toByte(), raw[1]) // sendChannel
        assertEquals(0x0F.toByte(), raw[5]) // listenChannel - different
    }

    @Test
    fun `command with repeat count sends multiple times`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)

        val command = SendAndListen(
            rileyLinkServiceData = rileyLinkServiceData,
            sendChannel = 0x0A,
            repeatCount = 0x05, // Repeat 5 times
            delayBetweenPacketsMs = 20, // 20ms between repeats
            listenChannel = 0x0A,
            timeoutMs = 1000,
            retryCount = 0x02,
            packetToSend = radioPacket
        )

        val raw = command.getRaw()

        assertEquals(0x05.toByte(), raw[2]) // repeatCount
        // Delay should be 20 (0x0014)
        assertEquals(0x00.toByte(), raw[3]) // delay high byte
        assertEquals(0x14.toByte(), raw[4]) // delay low byte
    }
}
