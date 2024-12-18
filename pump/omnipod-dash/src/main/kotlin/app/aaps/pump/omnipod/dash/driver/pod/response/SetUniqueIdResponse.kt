package app.aaps.pump.omnipod.dash.driver.pod.response

import app.aaps.pump.omnipod.dash.driver.pod.definition.PodStatus
import app.aaps.pump.omnipod.dash.driver.pod.response.ResponseType.ActivationResponseType
import app.aaps.pump.omnipod.dash.driver.pod.util.byValue
import java.nio.ByteBuffer

class SetUniqueIdResponse(
    encoded: ByteArray
) : ActivationResponseBase(ActivationResponseType.SET_UNIQUE_ID_RESPONSE, encoded) {

    val messageType: Byte = encoded[0]
    val messageLength: Short = (encoded[1].toInt() and 0xff).toShort()
    val pulseVolumeInTenThousandthMicroLiter: Short = ByteBuffer.wrap(byteArrayOf(encoded[2], encoded[3])).short
    val pumpRate: Short = (encoded[4].toInt() and 0xff).toShort()
    val primePumpRate: Short = (encoded[5].toInt() and 0xff).toShort()
    val numberOfEngagingClutchDrivePulses: Short = (encoded[6].toInt() and 0xff).toShort()
    val numberOfPrimePulses: Short = (encoded[7].toInt() and 0xff).toShort()
    val podExpirationTimeInHours: Short = (encoded[8].toInt() and 0xff).toShort()
    val firmwareVersionMajor: Short = (encoded[9].toInt() and 0xff).toShort()
    val firmwareVersionMinor: Short = (encoded[10].toInt() and 0xff).toShort()
    val firmwareVersionInterim: Short = (encoded[11].toInt() and 0xff).toShort()
    val bleVersionMajor: Short = (encoded[12].toInt() and 0xff).toShort()
    val bleVersionMinor: Short = (encoded[13].toInt() and 0xff).toShort()
    val bleVersionInterim: Short = (encoded[14].toInt() and 0xff).toShort()
    val productId: Short = (encoded[15].toInt() and 0xff).toShort()
    val podStatus: PodStatus = byValue(encoded[16], PodStatus.UNKNOWN)
    val lotNumber: Long = ByteBuffer.wrap(
        byteArrayOf(
            0,
            0,
            0,
            0,
            encoded[17],
            encoded[18],
            encoded[19],
            encoded[20]
        )
    ).long
    val podSequenceNumber: Long = ByteBuffer.wrap(
        byteArrayOf(
            0,
            0,
            0,
            0,
            encoded[21],
            encoded[22],
            encoded[23],
            encoded[24]
        )
    ).long
    val uniqueIdReceivedInCommand: Long = ByteBuffer.wrap(
        byteArrayOf(
            0,
            0,
            0,
            0,
            encoded[25],
            encoded[26],
            encoded[27],
            encoded[28]
        )
    ).long

    override fun toString(): String {
        return "SetUniqueIdResponse{" +
            "messageType=" + messageType +
            ", messageLength=" + messageLength +
            ", pulseVolume=" + pulseVolumeInTenThousandthMicroLiter +
            ", pumpRate=" + pumpRate +
            ", primePumpRate=" + primePumpRate +
            ", numberOfEngagingClutchDrivePulses=" + numberOfEngagingClutchDrivePulses +
            ", numberOfPrimePulses=" + numberOfPrimePulses +
            ", podExpirationTimeInHours=" + podExpirationTimeInHours +
            ", softwareVersionMajor=" + firmwareVersionMajor +
            ", softwareVersionMinor=" + firmwareVersionMinor +
            ", softwareVersionInterim=" + firmwareVersionInterim +
            ", bleVersionMajor=" + bleVersionMajor +
            ", bleVersionMinor=" + bleVersionMinor +
            ", bleVersionInterim=" + bleVersionInterim +
            ", productId=" + productId +
            ", podStatus=" + podStatus +
            ", lotNumber=" + lotNumber +
            ", podSequenceNumber=" + podSequenceNumber +
            ", uniqueIdReceivedInCommand=" + uniqueIdReceivedInCommand +
            ", activationResponseType=" + activationResponseType +
            ", responseType=" + responseType +
            ", encoded=" + encoded.contentToString() +
            '}'
    }
}
