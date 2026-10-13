package app.aaps.pump.common.hw.rileylink.ble.data

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.pump.ByteUtil.shortHexString
import app.aaps.core.utils.pump.ByteUtil.substring
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.ble.RileyLinkCommunicationException
import app.aaps.pump.common.hw.rileylink.ble.command.RileyLinkCommand
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkBLEError
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.common.utils.CRC
import org.apache.commons.lang3.NotImplementedException
import javax.inject.Inject

/**
 * Created by geoff on 5/30/16.
 */
class RadioResponse @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rileyLinkServiceData: RileyLinkServiceData,
    private val rileyLinkUtil: RileyLinkUtil
) {

    var rssi: Int = 0

    var command: RileyLinkCommand? = null
    private var decodedOK = false
    private var responseNumber = 0
    private var decodedPayload: ByteArray = ByteArray(0)
    private var receivedCRC: Byte = 0

    fun with(command: RileyLinkCommand? = null): RadioResponse {
        this.command = command
        return this
    }

    fun isValid(): Boolean {
        // We should check for all listening commands, but only one is actually used

        if (command?.getCommandType() != RileyLinkCommandType.SendAndListen) {
            return true
        }

        if (!decodedOK) {
            return false
        }
        return receivedCRC == CRC.crc8(decodedPayload)
    }

    @Throws(RileyLinkCommunicationException::class) fun init(rxData: ByteArray?) {
        if (rxData == null) {
            return
        }
        if (rxData.size < 3) {
            // This does not look like something valid heard from a RileyLink device
            return
        }
        var encodedPayload: ByteArray?

        if (rileyLinkServiceData.firmwareVersion?.isSameVersion(RileyLinkFirmwareVersion.Version2AndHigher) == true) {
            encodedPayload = substring(rxData, 3, rxData.size - 3)
            rssi = rxData[1].toInt()
            responseNumber = rxData[2].toInt()
        } else {
            encodedPayload = substring(rxData, 2, rxData.size - 2)
            rssi = rxData[0].toInt()
            responseNumber = rxData[1].toInt()
        }

        try {
            // for non-radio commands we just return the raw response
            // well, for non-radio commands we shouldn't even reach this point
            // but getVersion is kind of exception

            if (command?.getCommandType() != RileyLinkCommandType.SendAndListen) {
                decodedOK = true
                decodedPayload = encodedPayload
                return
            }

            when (rileyLinkUtil.encoding) {
                RileyLinkEncodingType.Manchester, RileyLinkEncodingType.FourByteSixByteRileyLink -> {
                    decodedOK = true
                    decodedPayload = encodedPayload
                }

                RileyLinkEncodingType.FourByteSixByteLocal                                       -> {
                    rileyLinkUtil.encoding4b6b.decode4b6b(encodedPayload).let { decodeThis ->
                        if (decodeThis.size > 2) {
                            decodedOK = true

                            decodedPayload = substring(decodeThis, 0, decodeThis.size - 1)
                            receivedCRC = decodeThis[decodeThis.size - 1]
                            val calculatedCRC = CRC.crc8(decodedPayload)
                            if (receivedCRC != calculatedCRC) {
                                aapsLogger.error(
                                    LTag.PUMPBTCOMM, String.format(
                                        "RadioResponse: CRC mismatch, calculated 0x%02x, received 0x%02x",
                                        calculatedCRC, receivedCRC
                                    )
                                )
                            }
                        } else throw RileyLinkCommunicationException(RileyLinkBLEError.TooShortOrNullResponse, null)
                    }
                }

                else                                                                             -> throw NotImplementedException(
                    ("this {" + rileyLinkUtil.encoding.toString()
                        + "} encoding is not supported")
                )
            }
        } catch (_: NumberFormatException) {
            decodedOK = false
            aapsLogger.error(LTag.PUMPBTCOMM, "Failed to decode radio data: " + shortHexString(encodedPayload))
        }
    }

    fun getPayload(): ByteArray = decodedPayload
}
