package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.ResponseType.ActivationResponseType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.util.byValue
import java.nio.ByteBuffer
import java.util.*
import kotlin.experimental.and

class VersionResponse(
    encoded: ByteArray
) : ActivationResponseBase(ActivationResponseType.GET_VERSION_RESPONSE, encoded) {

    private val messageType: Byte = encoded[0]
    private val messageLength: Short = (encoded[1].toInt() and 0xff).toShort()
    private val firmwareVersionMajor: Short = (encoded[2].toInt() and 0xff).toShort()
    private val firmwareVersionMinor: Short = (encoded[3].toInt() and 0xff).toShort()
    private val firmwareVersionInterim: Short = (encoded[4].toInt() and 0xff).toShort()
    private val bleVersionMajor: Short = (encoded[5].toInt() and 0xff).toShort()
    private val bleVersionMinor: Short = (encoded[6].toInt() and 0xff).toShort()
    private val bleVersionInterim: Short = (encoded[7].toInt() and 0xff).toShort()
    private val productId: Short = (encoded[8].toInt() and 0xff).toShort()
    private val podStatus: PodStatus = byValue((encoded[9] and 0xf), PodStatus.UNKNOWN)
    private val lotNumber: Long = ByteBuffer.wrap(byteArrayOf(0, 0, 0, 0, encoded[10], encoded[11], encoded[12], encoded[13])).long
    private val podSequenceNumber: Long = ByteBuffer.wrap(byteArrayOf(0, 0, 0, 0, encoded[14], encoded[15], encoded[16], encoded[17])).long
    private val rssi: Byte = (encoded[18] and 0x3f)
    private val receiverLowerGain: Byte = ((encoded[18].toInt() shr 6 and 0x03).toByte())
    private val uniqueIdReceivedInCommand: Long = ByteBuffer.wrap(byteArrayOf(0, 0, 0, 0, encoded[19], encoded[20], encoded[21], encoded[22])).long

    fun getMessageType(): Byte {
        return messageType
    }

    fun getMessageLength(): Short {
        return messageLength
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

    fun getRssi(): Byte {
        return rssi
    }

    fun getReceiverLowerGain(): Byte {
        return receiverLowerGain
    }

    fun getUniqueIdReceivedInCommand(): Long {
        return uniqueIdReceivedInCommand
    }

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
            ", encoded=" + Arrays.toString(encoded) +
            '}'
    }

}