package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.ResponseType.ActivationResponseType
import java.nio.ByteBuffer
import java.util.*
import kotlin.experimental.and

class SetUniqueIdResponse(encoded: ByteArray) : ActivationResponseBase(ActivationResponseType.SET_UNIQUE_ID_RESPONSE, encoded) {

    private val messageType: Byte // TODO directly assign here
    private val messageLength: Short
    private val pulseVolumeInTenThousandthMicroLiter: Short
    private val pumpRate: Short
    private val primePumpRate: Short
    private val numberOfEngagingClutchDrivePulses: Short
    private val numberOfPrimePulses: Short
    private val podExpirationTimeInHours: Short
    private val firmwareVersionMajor: Short
    private val firmwareVersionMinor: Short
    private val firmwareVersionInterim: Short
    private val bleVersionMajor: Short
    private val bleVersionMinor: Short
    private val bleVersionInterim: Short
    private val productId: Short
    private val podStatus: PodStatus
    private val lotNumber: Long
    private val podSequenceNumber: Long
    private val uniqueIdReceivedInCommand: Long
    fun getMessageType(): Byte {
        return messageType
    }

    fun getMessageLength(): Short { // TODO value getters
        return messageLength
    }

    fun getPulseVolumeInTenThousandthMicroLiter(): Short {
        return pulseVolumeInTenThousandthMicroLiter
    }

    fun getDeliveryRate(): Short {
        return pumpRate
    }

    fun getPrimeRate(): Short {
        return primePumpRate
    }

    fun getNumberOfEngagingClutchDrivePulses(): Short {
        return numberOfEngagingClutchDrivePulses
    }

    fun getNumberOfPrimePulses(): Short {
        return numberOfPrimePulses
    }

    fun getPodExpirationTimeInHours(): Short {
        return podExpirationTimeInHours
    }

    fun getFirmwareVersionMajor(): Short {
        return firmwareVersionMajor
    }

    fun getFirmwareVersionMinor(): Short {
        return firmwareVersionMinor
    }

    fun getFirmwareVersionInterim(): Short {
        return firmwareVersionInterim
    }

    fun getBleVersionMajor(): Short {
        return bleVersionMajor
    }

    fun getBleVersionMinor(): Short {
        return bleVersionMinor
    }

    fun getBleVersionInterim(): Short {
        return bleVersionInterim
    }

    fun getProductId(): Short {
        return productId
    }

    fun getPodStatus(): PodStatus {
        return podStatus
    }

    fun getLotNumber(): Long {
        return lotNumber
    }

    fun getPodSequenceNumber(): Long {
        return podSequenceNumber
    }

    fun getUniqueIdReceivedInCommand(): Long {
        return uniqueIdReceivedInCommand
    }

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

    init {
        messageType = encoded[0]
        messageLength = (encoded[1].toInt() and 0xff) .toShort()
        pulseVolumeInTenThousandthMicroLiter = ByteBuffer.wrap(byteArrayOf(encoded[2], encoded[3])).short
        pumpRate = (encoded[4].toInt() and 0xff) .toShort()
        primePumpRate = (encoded[5].toInt() and 0xff) .toShort()
        numberOfEngagingClutchDrivePulses = (encoded[6].toInt() and 0xff) .toShort()
        numberOfPrimePulses = (encoded[7].toInt() and 0xff) .toShort()
        podExpirationTimeInHours = (encoded[8].toInt() and 0xff) .toShort()
        firmwareVersionMajor = (encoded[9].toInt() and 0xff) .toShort()
        firmwareVersionMinor = (encoded[10].toInt() and 0xff) .toShort()
        firmwareVersionInterim = (encoded[11].toInt() and 0xff) .toShort()
        bleVersionMajor = (encoded[12].toInt() and 0xff) .toShort()
        bleVersionMinor = (encoded[13].toInt() and 0xff) .toShort()
        bleVersionInterim = (encoded[14].toInt() and 0xff) .toShort()
        productId = (encoded[15].toInt() and 0xff) .toShort()
        podStatus = PodStatus.byValue(encoded[16])
        lotNumber = ByteBuffer.wrap(byteArrayOf(0, 0, 0, 0, encoded[17], encoded[18], encoded[19], encoded[20])).long
        podSequenceNumber = ByteBuffer.wrap(byteArrayOf(0, 0, 0, 0, encoded[21], encoded[22], encoded[23], encoded[24])).long
        uniqueIdReceivedInCommand = ByteBuffer.wrap(byteArrayOf(0, 0, 0, 0, encoded[25], encoded[26], encoded[27], encoded[28])).long
    }
}