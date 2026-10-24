package app.aaps.pump.common.hw.rileylink.ble.data

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.ble.RileyLinkCommunicationException
import app.aaps.pump.common.hw.rileylink.ble.command.RileyLinkCommand
import app.aaps.pump.common.hw.rileylink.ble.data.encoding.Encoding4b6b
import app.aaps.pump.common.hw.rileylink.ble.data.encoding.Encoding4b6bGeoff
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType
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

    // A Medtronic 522 (serial 678734) answering the model request of the wake-up, as a RileyLink with
    // firmware 2 received it with local 4b6b encoding. Captured on a real pump.
    private val medtronic522ModelReply =
        ("DD 2C 20 A9 69 96 69 68 F4 68 D5 59 56 38 E5 8F 28 F2 " + "55 ".repeat(90) + "5A 65")
            .split(" ").map { it.toInt(16).toByte() }.toByteArray()

    /**
     * The wake-up and the frequency scan create the response without a command. It was then taken as a
     * non-radio response and returned undecoded, so the model was read from 4b6b bytes ("h?Y") and the
     * pump was reported unreachable although it had answered.
     */
    @Test
    fun `response without command is decoded with local 4b6b encoding`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)
        whenever(rileyLinkUtil.encoding).thenReturn(RileyLinkEncodingType.FourByteSixByteLocal)
        whenever(rileyLinkUtil.encoding4b6b).thenReturn(Encoding4b6bGeoff(aapsLogger))

        radioResponse.init(medtronic522ModelReply)

        // A7 = packet type, 67 87 34 = serial, 8D = read model, then length 3 and "522"
        val payload = radioResponse.getPayload()
        assertArrayEquals(
            byteArrayOf(0xA7.toByte(), 0x67, 0x87.toByte(), 0x34, 0x8D.toByte(), 0x09, 0x03, 0x35, 0x32, 0x32),
            payload.copyOfRange(0, 10)
        )
        assertTrue(radioResponse.isValid())
        assertEquals(0x2C, radioResponse.rssi)
    }

    @Test
    fun `response without command and with a wrong CRC is not valid`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)
        whenever(rileyLinkUtil.encoding).thenReturn(RileyLinkEncodingType.FourByteSixByteLocal)

        val encodedPayload = byteArrayOf(0x55, 0x66)
        val payloadData = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        val wrongCRC = (CRC.crc8(payloadData) + 1).toByte()
        whenever(encoding4b6b.decode4b6b(encodedPayload)).thenReturn(payloadData + wrongCRC)

        radioResponse.init(byteArrayOf(0x00, 0x42, 0x01) + encodedPayload)

        assertFalse(radioResponse.isValid())
    }

    // Unchanged: the RileyLink decodes these itself and there is no CRC byte to check here
    @Test
    fun `response without command with RileyLink encoding returns the payload and is valid`() {
        whenever(rileyLinkServiceData.firmwareVersion).thenReturn(RileyLinkFirmwareVersionBase.Version_2_0)
        whenever(rileyLinkUtil.encoding).thenReturn(RileyLinkEncodingType.FourByteSixByteRileyLink)

        radioResponse.init(byteArrayOf(0x00, 0x42, 0x01, 0xAA.toByte(), 0xBB.toByte()))

        assertArrayEquals(byteArrayOf(0xAA.toByte(), 0xBB.toByte()), radioResponse.getPayload())
        assertTrue(radioResponse.isValid())
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
