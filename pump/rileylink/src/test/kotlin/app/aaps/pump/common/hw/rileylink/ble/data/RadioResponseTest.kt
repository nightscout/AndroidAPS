package app.aaps.pump.common.hw.rileylink.ble.data

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.ble.RileyLinkCommunicationException
import app.aaps.pump.common.hw.rileylink.ble.command.RileyLinkCommand
import app.aaps.pump.common.hw.rileylink.ble.data.encoding.Encoding4b6b
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersionBase
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.common.utils.CRC
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for RadioResponse class
 */
class RadioResponseTest {

    private lateinit var aapsLogger: AAPSLogger
    private lateinit var rileyLinkServiceData: RileyLinkServiceData
    private lateinit var rileyLinkUtil: RileyLinkUtil
    private lateinit var radioResponse: RadioResponse
    private lateinit var encoding4b6b: Encoding4b6b

    @BeforeEach
    fun setup() {
        aapsLogger = mock()
        rileyLinkServiceData = mock()
        rileyLinkUtil = mock()
        encoding4b6b = mock()
        whenever(rileyLinkUtil.encoding4b6b).thenReturn(encoding4b6b)

        radioResponse = RadioResponse(aapsLogger, rileyLinkServiceData, rileyLinkUtil)
    }

    @Test
    fun `init with null data does nothing`() {
        radioResponse.init(null)
        // Should not throw exception
        assertEquals(0, radioResponse.rssi)
    }

    @Test
    fun `init with too short data does nothing`() {
        val shortData = byteArrayOf(0x01, 0x02)
        radioResponse.init(shortData)
        // Should not throw exception
        assertEquals(0, radioResponse.rssi)
    }

    @Test
    fun `init with firmware v1 parses rssi correctly`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_1_0)
        whenever(rileyLinkUtil.encoding).thenReturn(RileyLinkEncodingType.Manchester)

        val command = mock<RileyLinkCommand>()
        whenever(command.getCommandType()).thenReturn(RileyLinkCommandType.SendAndListen)

        radioResponse.with(command)

        // Firmware v1 format: [rssi, responseNumber, ...payload]
        val rxData = byteArrayOf(0x42.toByte(), 0x01, 0xAA.toByte(), 0xBB.toByte())
        radioResponse.init(rxData)

        assertEquals(0x42, radioResponse.rssi)
    }

    @Test
    fun `init with firmware v2 parses rssi correctly`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)
        whenever(rileyLinkUtil.encoding).thenReturn(RileyLinkEncodingType.Manchester)

        val command = mock<RileyLinkCommand>()
        whenever(command.getCommandType()).thenReturn(RileyLinkCommandType.SendAndListen)

        radioResponse.with(command)

        // Firmware v2 format: [0, rssi, responseNumber, ...payload]
        val rxData = byteArrayOf(0x00, 0x42.toByte(), 0x01, 0xAA.toByte(), 0xBB.toByte())
        radioResponse.init(rxData)

        assertEquals(0x42, radioResponse.rssi)
    }

    @Test
    fun `init with Manchester encoding returns payload correctly`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)
        whenever(rileyLinkUtil.encoding).thenReturn(RileyLinkEncodingType.Manchester)

        val command = mock<RileyLinkCommand>()
        whenever(command.getCommandType()).thenReturn(RileyLinkCommandType.SendAndListen)

        radioResponse.with(command)

        val rxData = byteArrayOf(0x00, 0x42, 0x01, 0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        radioResponse.init(rxData)

        val expectedPayload = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        assertArrayEquals(expectedPayload, radioResponse.getPayload())
    }

    @Test
    fun `init with FourByteSixByteLocal decodes payload`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)
        whenever(rileyLinkUtil.encoding).thenReturn(RileyLinkEncodingType.FourByteSixByteLocal)

        val command = mock<RileyLinkCommand>()
        whenever(command.getCommandType()).thenReturn(RileyLinkCommandType.SendAndListen)

        radioResponse.with(command)

        val encodedPayload = byteArrayOf(0x55, 0x66, 0x77, 0x88.toByte())
        val decodedPayload = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0x00) // Last byte is CRC
        whenever(encoding4b6b.decode4b6b(encodedPayload)).thenReturn(decodedPayload)

        val rxData = byteArrayOf(0x00, 0x42, 0x01) + encodedPayload
        radioResponse.init(rxData)

        // Payload should be decoded data minus the CRC byte
        val expectedPayload = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        assertArrayEquals(expectedPayload, radioResponse.getPayload())
    }

    @Test
    fun `isValid returns true for non-SendAndListen command`() {
        val command = mock<RileyLinkCommand>()
        whenever(command.getCommandType()).thenReturn(RileyLinkCommandType.GetVersion)

        radioResponse.with(command)

        assertTrue(radioResponse.isValid())
    }

    @Test
    fun `isValid returns true when CRC matches`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)
        whenever(rileyLinkUtil.encoding).thenReturn(RileyLinkEncodingType.FourByteSixByteLocal)

        val command = mock<RileyLinkCommand>()
        whenever(command.getCommandType()).thenReturn(RileyLinkCommandType.SendAndListen)

        radioResponse.with(command)

        val encodedPayload = byteArrayOf(0x55, 0x66)
        // Calculate correct CRC8 of [0xAA, 0xBB]
        val payloadData = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        val correctCRC = CRC.crc8(payloadData)
        val decodedPayload = payloadData + correctCRC
        whenever(encoding4b6b.decode4b6b(encodedPayload)).thenReturn(decodedPayload)

        val rxData = byteArrayOf(0x00, 0x42, 0x01) + encodedPayload
        radioResponse.init(rxData)

        assertTrue(radioResponse.isValid())
    }

    @Test
    fun `isValid returns false when decoding fails`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)
        whenever(rileyLinkUtil.encoding).thenReturn(RileyLinkEncodingType.FourByteSixByteLocal)

        val command = mock<RileyLinkCommand>()
        whenever(command.getCommandType()).thenReturn(RileyLinkCommandType.SendAndListen)

        radioResponse.with(command)

        val encodedPayload = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        whenever(encoding4b6b.decode4b6b(encodedPayload)).thenThrow(RileyLinkCommunicationException::class.java)

        val rxData = byteArrayOf(0x00, 0x42, 0x01) + encodedPayload

        try {
            radioResponse.init(rxData)
        } catch (_: Exception) {
            // Expected
        }

        assertFalse(radioResponse.isValid())
    }

    @Test
    fun `with sets command correctly`() {
        val command = mock<RileyLinkCommand>()
        val result = radioResponse.with(command)

        assertEquals(radioResponse, result)
        assertEquals(command, radioResponse.command)
    }

    @Test
    fun `getPayload returns empty array initially`() {
        val payload = radioResponse.getPayload()
        assertArrayEquals(byteArrayOf(), payload)
    }

    @Test
    fun `init with FourByteSixByteRileyLink returns raw payload`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)
        whenever(rileyLinkUtil.encoding).thenReturn(RileyLinkEncodingType.FourByteSixByteRileyLink)

        val command = mock<RileyLinkCommand>()
        whenever(command.getCommandType()).thenReturn(RileyLinkCommandType.SendAndListen)

        radioResponse.with(command)

        val rxData = byteArrayOf(0x00, 0x42, 0x01, 0xAA.toByte(), 0xBB.toByte())
        radioResponse.init(rxData)

        val expectedPayload = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        assertArrayEquals(expectedPayload, radioResponse.getPayload())
    }

    @Test
    fun `init handles negative byte values correctly`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)
        whenever(rileyLinkUtil.encoding).thenReturn(RileyLinkEncodingType.Manchester)

        val command = mock<RileyLinkCommand>()
        whenever(command.getCommandType()).thenReturn(RileyLinkCommandType.SendAndListen)

        radioResponse.with(command)

        val rxData = byteArrayOf(0x00, 0xFF.toByte(), 0x01, 0xAA.toByte())
        radioResponse.init(rxData)

        // RSSI should be interpreted as signed value
        assertEquals(0xFF.toByte().toInt(), radioResponse.rssi)
    }

    @Test
    fun `init with empty payload`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)
        whenever(rileyLinkUtil.encoding).thenReturn(RileyLinkEncodingType.Manchester)

        val command = mock<RileyLinkCommand>()
        whenever(command.getCommandType()).thenReturn(RileyLinkCommandType.SendAndListen)

        radioResponse.with(command)

        val rxData = byteArrayOf(0x00, 0x42, 0x01)
        radioResponse.init(rxData)

        assertArrayEquals(byteArrayOf(), radioResponse.getPayload())
    }
}
