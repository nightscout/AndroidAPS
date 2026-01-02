package app.aaps.pump.omnipod.dash.driver.pod.response

import app.aaps.pump.omnipod.dash.driver.pod.definition.PodStatus
import app.aaps.pump.omnipod.dash.driver.pod.response.ResponseType.ActivationResponseType
import app.aaps.pump.omnipod.dash.driver.pod.util.byValue
import java.nio.ByteBuffer
import kotlin.experimental.and

class VersionResponse(
    encoded: ByteArray
) : ActivationResponseBase(ActivationResponseType.GET_VERSION_RESPONSE, encoded) {

    val messageType: Byte = encoded[0]
    val messageLength: Short = (encoded[1].toInt() and 0xff).toShort()
    val firmwareVersionMajor: Short = (encoded[2].toInt() and 0xff).toShort()
    val firmwareVersionMinor: Short = (encoded[3].toInt() and 0xff).toShort()
    val firmwareVersionInterim: Short = (encoded[4].toInt() and 0xff).toShort()
    val bleVersionMajor: Short = (encoded[5].toInt() and 0xff).toShort()
    val bleVersionMinor: Short = (encoded[6].toInt() and 0xff).toShort()
    val bleVersionInterim: Short = (encoded[7].toInt() and 0xff).toShort()
    val productId: Short = (encoded[8].toInt() and 0xff).toShort()
    val podStatus: PodStatus = byValue((encoded[9] and 0xf), PodStatus.UNKNOWN)
    val lotNumber: Long = ByteBuffer.wrap(
        byteArrayOf(
            0,
            0,
            0,
            0,
            encoded[10],
            encoded[11],
            encoded[12],
            encoded[13]
        )
    ).long
    val podSequenceNumber: Long = ByteBuffer.wrap(
        byteArrayOf(
            0,
            0,
            0,
            0,
            encoded[14],
            encoded[15],
            encoded[16],
            encoded[17]
        )
    ).long
    val rssi: Byte = (encoded[18] and 0x3f)
    val receiverLowerGain: Byte = ((encoded[18].toInt() shr 6 and 0x03).toByte())
    val uniqueIdReceivedInCommand: Long = ByteBuffer.wrap(
        byteArrayOf(
            0,
            0,
            0,
            0,
            encoded[19],
            encoded[20],
            encoded[21],
            encoded[22]
        )
    ).long

    override fun toString(): String {
        return "VersionResponse{" +
            "messageType=" + messageType +
            ", messageLength=" + messageLength +
            ", firmwareVersionMajor=" + firmwareVersionMajor +
            ", firmwareVersionMinor=" + firmwareVersionMinor +
            ", firmwareVersionInterim=" + firmwareVersionInterim +
            ", bleVersionMajor=" + bleVersionMajor +
            ", bleVersionMinor=" + bleVersionMinor +
            ", bleVersionInterim=" + bleVersionInterim +
            ", productId=" + productId +
            ", podStatus=" + podStatus +
            ", lotNumber=" + lotNumber +
            ", podSequenceNumber=" + podSequenceNumber +
            ", rssi=" + rssi +
            ", receiverLowerGain=" + receiverLowerGain +
            ", uniqueIdReceivedInCommand=" + uniqueIdReceivedInCommand +
            ", activationResponseType=" + activationResponseType +
            ", responseType=" + responseType +
            ", encoded=" + encoded.contentToString() +
            '}'
    }
}
