package app.aaps.pump.common.hw.rileylink.ble.data

import app.aaps.pump.common.hw.rileylink.ble.command.RileyLinkCommand
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import javax.inject.Provider

/**
 * Tests for RFSpyResponse
 */
class RFSpyResponseTest {

    private lateinit var rfSpyResponse: RFSpyResponse
    private lateinit var radioResponseProvider: Provider<RadioResponse>
    private lateinit var mockRadioResponse: RadioResponse

    @BeforeEach
    fun setup() {
        mockRadioResponse = mock()
        radioResponseProvider = Provider { mockRadioResponse }
        rfSpyResponse = RFSpyResponse(radioResponseProvider)
    }

    @Test
    fun `wasNoResponseFromRileyLink returns true for empty array`() {
        rfSpyResponse.with(null, byteArrayOf())

        assertTrue(rfSpyResponse.wasNoResponseFromRileyLink())
    }

    @Test
    fun `wasNoResponseFromRileyLink returns false for non-empty array`() {
        rfSpyResponse.with(null, byteArrayOf(0x01))

        assertFalse(rfSpyResponse.wasNoResponseFromRileyLink())
    }

    @Test
    fun `wasTimeout returns true for 0xaa single byte`() {
        rfSpyResponse.with(null, byteArrayOf(0xAA.toByte()))

        assertTrue(rfSpyResponse.wasTimeout())
    }

    @Test
    fun `wasTimeout returns true for 0xaa two bytes`() {
        rfSpyResponse.with(null, byteArrayOf(0xAA.toByte(), 0x00))

        assertTrue(rfSpyResponse.wasTimeout())
    }

    @Test
    fun `wasTimeout returns false for other single byte`() {
        rfSpyResponse.with(null, byteArrayOf(0xBB.toByte()))

        assertFalse(rfSpyResponse.wasTimeout())
    }

    @Test
    fun `wasTimeout returns false for more than 2 bytes`() {
        rfSpyResponse.with(null, byteArrayOf(0xAA.toByte(), 0x00, 0x00))

        assertFalse(rfSpyResponse.wasTimeout())
    }

    @Test
    fun `wasInterrupted returns true for 0xbb single byte`() {
        rfSpyResponse.with(null, byteArrayOf(0xBB.toByte()))

        assertTrue(rfSpyResponse.wasInterrupted())
    }

    @Test
    fun `wasInterrupted returns true for 0xbb two bytes`() {
        rfSpyResponse.with(null, byteArrayOf(0xBB.toByte(), 0x00))

        assertTrue(rfSpyResponse.wasInterrupted())
    }

    @Test
    fun `wasInterrupted returns false for other single byte`() {
        rfSpyResponse.with(null, byteArrayOf(0xAA.toByte()))

        assertFalse(rfSpyResponse.wasInterrupted())
    }

    @Test
    fun `wasInterrupted returns false for more than 2 bytes`() {
        rfSpyResponse.with(null, byteArrayOf(0xBB.toByte(), 0x00, 0x00))

        assertFalse(rfSpyResponse.wasInterrupted())
    }

    @Test
    fun `isInvalidParam returns true for 0x11 single byte`() {
        rfSpyResponse.with(null, byteArrayOf(0x11))

        assertTrue(rfSpyResponse.isInvalidParam())
    }

    @Test
    fun `isInvalidParam returns true for 0x11 two bytes`() {
        rfSpyResponse.with(null, byteArrayOf(0x11, 0x00))

        assertTrue(rfSpyResponse.isInvalidParam())
    }

    @Test
    fun `isInvalidParam returns false for other byte`() {
        rfSpyResponse.with(null, byteArrayOf(0xDD.toByte()))

        assertFalse(rfSpyResponse.isInvalidParam())
    }

    @Test
    fun `isUnknownCommand returns true for 0x22 single byte`() {
        rfSpyResponse.with(null, byteArrayOf(0x22))

        assertTrue(rfSpyResponse.isUnknownCommand())
    }

    @Test
    fun `isUnknownCommand returns true for 0x22 two bytes`() {
        rfSpyResponse.with(null, byteArrayOf(0x22, 0x00))

        assertTrue(rfSpyResponse.isUnknownCommand())
    }

    @Test
    fun `isUnknownCommand returns false for other byte`() {
        rfSpyResponse.with(null, byteArrayOf(0xDD.toByte()))

        assertFalse(rfSpyResponse.isUnknownCommand())
    }

    @Test
    fun `isOK returns true for 0x01 OldSuccess`() {
        rfSpyResponse.with(null, byteArrayOf(0x01))

        assertTrue(rfSpyResponse.isOK())
    }

    @Test
    fun `isOK returns true for 0xDD Success`() {
        rfSpyResponse.with(null, byteArrayOf(0xDD.toByte()))

        assertTrue(rfSpyResponse.isOK())
    }

    @Test
    fun `isOK returns true for 0x01 with two bytes`() {
        rfSpyResponse.with(null, byteArrayOf(0x01, 0x00))

        assertTrue(rfSpyResponse.isOK())
    }

    @Test
    fun `isOK returns true for 0xDD with two bytes`() {
        rfSpyResponse.with(null, byteArrayOf(0xDD.toByte(), 0x00))

        assertTrue(rfSpyResponse.isOK())
    }

    @Test
    fun `isOK returns false for other codes`() {
        rfSpyResponse.with(null, byteArrayOf(0xAA.toByte()))

        assertFalse(rfSpyResponse.isOK())
    }

    @Test
    fun `isOK returns false for more than 2 bytes`() {
        rfSpyResponse.with(null, byteArrayOf(0x01, 0x00, 0x00))

        assertFalse(rfSpyResponse.isOK())
    }

    @Test
    fun `looksLikeRadioPacket returns false for empty array`() {
        rfSpyResponse.with(null, byteArrayOf())

        assertFalse(rfSpyResponse.looksLikeRadioPacket())
    }

    @Test
    fun `looksLikeRadioPacket returns false for 1 byte`() {
        rfSpyResponse.with(null, byteArrayOf(0x01))

        assertFalse(rfSpyResponse.looksLikeRadioPacket())
    }

    @Test
    fun `looksLikeRadioPacket returns false for 2 bytes`() {
        rfSpyResponse.with(null, byteArrayOf(0x01, 0x02))

        assertFalse(rfSpyResponse.looksLikeRadioPacket())
    }

    @Test
    fun `looksLikeRadioPacket returns true for 3 bytes`() {
        rfSpyResponse.with(null, byteArrayOf(0x01, 0x02, 0x03))

        assertTrue(rfSpyResponse.looksLikeRadioPacket())
    }

    @Test
    fun `looksLikeRadioPacket returns true for many bytes`() {
        rfSpyResponse.with(null, ByteArray(100))

        assertTrue(rfSpyResponse.looksLikeRadioPacket())
    }

    @Test
    fun `getRadioResponse returns RadioResponse for radio packet`() {
        val command = mock<RileyLinkCommand>()
        whenever(mockRadioResponse.with(command)).thenReturn(mockRadioResponse)

        rfSpyResponse.with(command, byteArrayOf(0x00, 0x42, 0x01, 0xAA.toByte()))

        val result = rfSpyResponse.getRadioResponse()

        assertNotNull(result)
    }

    @Test
    fun `getRadioResponse returns RadioResponse for non-radio packet`() {
        rfSpyResponse.with(null, byteArrayOf(0xDD.toByte()))

        val result = rfSpyResponse.getRadioResponse()

        assertNotNull(result)
    }

    @Test
    fun `toString returns Radio packet for data longer than 2 bytes`() {
        rfSpyResponse.with(null, byteArrayOf(0x01, 0x02, 0x03))

        val result = rfSpyResponse.toString()

        assertEquals("Radio packet", result)
    }

    @Test
    fun `toString returns response description for timeout`() {
        rfSpyResponse.with(null, byteArrayOf(0xAA.toByte()))

        val result = rfSpyResponse.toString()

        assertEquals("Timeout", result)
    }

    @Test
    fun `toString returns response description for interrupted`() {
        rfSpyResponse.with(null, byteArrayOf(0xBB.toByte()))

        val result = rfSpyResponse.toString()

        assertEquals("Interrupted", result)
    }

    @Test
    fun `toString returns response description for success`() {
        rfSpyResponse.with(null, byteArrayOf(0xDD.toByte()))

        val result = rfSpyResponse.toString()

        assertEquals("Success", result)
    }

    @Test
    fun `toString returns response description for old success`() {
        rfSpyResponse.with(null, byteArrayOf(0x01))

        val result = rfSpyResponse.toString()

        assertEquals("OldSuccess", result)
    }

    @Test
    fun `toString returns empty for unknown response code`() {
        rfSpyResponse.with(null, byteArrayOf(0x99.toByte()))

        val result = rfSpyResponse.toString()

        assertEquals("", result)
    }

    @Test
    fun `with sets command and raw data`() {
        val command = mock<RileyLinkCommand>()
        val data = byteArrayOf(0x01, 0x02, 0x03)

        val result = rfSpyResponse.with(command, data)

        assertEquals(rfSpyResponse, result)
        assertEquals(data, rfSpyResponse.raw)
    }

    @Test
    fun `with returns self for chaining`() {
        val result = rfSpyResponse.with(null, byteArrayOf(0x01))

        assertEquals(rfSpyResponse, result)
    }

    @Test
    fun `multiple response code checks are mutually exclusive for timeout`() {
        rfSpyResponse.with(null, byteArrayOf(0xAA.toByte()))

        assertTrue(rfSpyResponse.wasTimeout())
        assertFalse(rfSpyResponse.wasInterrupted())
        assertFalse(rfSpyResponse.isInvalidParam())
        assertFalse(rfSpyResponse.isUnknownCommand())
        assertFalse(rfSpyResponse.isOK())
    }

    @Test
    fun `multiple response code checks are mutually exclusive for interrupted`() {
        rfSpyResponse.with(null, byteArrayOf(0xBB.toByte()))

        assertFalse(rfSpyResponse.wasTimeout())
        assertTrue(rfSpyResponse.wasInterrupted())
        assertFalse(rfSpyResponse.isInvalidParam())
        assertFalse(rfSpyResponse.isUnknownCommand())
        assertFalse(rfSpyResponse.isOK())
    }

    @Test
    fun `multiple response code checks are mutually exclusive for invalid param`() {
        rfSpyResponse.with(null, byteArrayOf(0x11))

        assertFalse(rfSpyResponse.wasTimeout())
        assertFalse(rfSpyResponse.wasInterrupted())
        assertTrue(rfSpyResponse.isInvalidParam())
        assertFalse(rfSpyResponse.isUnknownCommand())
        assertFalse(rfSpyResponse.isOK())
    }

    @Test
    fun `multiple response code checks are mutually exclusive for unknown command`() {
        rfSpyResponse.with(null, byteArrayOf(0x22))

        assertFalse(rfSpyResponse.wasTimeout())
        assertFalse(rfSpyResponse.wasInterrupted())
        assertFalse(rfSpyResponse.isInvalidParam())
        assertTrue(rfSpyResponse.isUnknownCommand())
        assertFalse(rfSpyResponse.isOK())
    }

    @Test
    fun `multiple response code checks are mutually exclusive for success`() {
        rfSpyResponse.with(null, byteArrayOf(0xDD.toByte()))

        assertFalse(rfSpyResponse.wasTimeout())
        assertFalse(rfSpyResponse.wasInterrupted())
        assertFalse(rfSpyResponse.isInvalidParam())
        assertFalse(rfSpyResponse.isUnknownCommand())
        assertTrue(rfSpyResponse.isOK())
    }

    @Test
    fun `realistic timeout scenario`() {
        val command = mock<RileyLinkCommand>()
        whenever(command.getCommandType()).thenReturn(RileyLinkCommandType.SendAndListen)

        rfSpyResponse.with(command, byteArrayOf(0xAA.toByte()))

        assertTrue(rfSpyResponse.wasTimeout())
        assertFalse(rfSpyResponse.looksLikeRadioPacket())
        assertEquals("Timeout", rfSpyResponse.toString())
    }

    @Test
    fun `realistic radio packet scenario`() {
        val command = mock<RileyLinkCommand>()
        whenever(command.getCommandType()).thenReturn(RileyLinkCommandType.SendAndListen)
        whenever(mockRadioResponse.with(command)).thenReturn(mockRadioResponse)

        // Simulate a radio response with RSSI and data
        val radioData = byteArrayOf(0x00, 0x42, 0x01, 0xAA.toByte(), 0xBB.toByte())
        rfSpyResponse.with(command, radioData)

        assertTrue(rfSpyResponse.looksLikeRadioPacket())
        assertFalse(rfSpyResponse.wasTimeout())
        assertFalse(rfSpyResponse.wasInterrupted())
        assertEquals("Radio packet", rfSpyResponse.toString())
    }

    @Test
    fun `realistic success scenario`() {
        val command = mock<RileyLinkCommand>()
        whenever(command.getCommandType()).thenReturn(RileyLinkCommandType.UpdateRegister)

        rfSpyResponse.with(command, byteArrayOf(0xDD.toByte()))

        assertTrue(rfSpyResponse.isOK())
        assertFalse(rfSpyResponse.looksLikeRadioPacket())
        assertEquals("Success", rfSpyResponse.toString())
    }

    @Test
    fun `realistic invalid param scenario`() {
        val command = mock<RileyLinkCommand>()
        rfSpyResponse.with(command, byteArrayOf(0x11))

        assertTrue(rfSpyResponse.isInvalidParam())
        assertEquals("InvalidParam", rfSpyResponse.toString())
    }

    @Test
    fun `realistic unknown command scenario`() {
        rfSpyResponse.with(null, byteArrayOf(0x22))

        assertTrue(rfSpyResponse.isUnknownCommand())
        assertEquals("UnknownCommand", rfSpyResponse.toString())
    }
}
