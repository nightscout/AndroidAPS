package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlarmType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.DeliveryStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.ResponseType.StatusResponseType
import java.nio.ByteBuffer
import java.util.*
import kotlin.experimental.and

class AlarmStatusResponse(
    encoded: ByteArray
) : AdditionalStatusResponseBase(StatusResponseType.ALARM_STATUS, encoded) {

    private val messageType: Byte
    private val messageLength: Short
    private val additionalStatusResponseType: Byte
    private val podStatus: PodStatus
    private val deliveryStatus: DeliveryStatus
    private val bolusPulsesRemaining: Short
    private val sequenceNumberOfLastProgrammingCommand: Short
    private val totalPulsesDelivered: Short
    private val alarmType: AlarmType
    private val alarmTime: Short
    private val reservoirPulsesRemaining: Short
    private val minutesSinceActivation: Short
    private val alert0Active: Boolean
    private val alert1Active: Boolean
    private val alert2Active: Boolean
    private val alert3Active: Boolean
    private val alert4Active: Boolean
    private val alert5Active: Boolean
    private val alert6Active: Boolean
    private val alert7Active: Boolean
    private val occlusionAlarm: Boolean
    private val pulseInfoInvalid: Boolean
    private val podStatusWhenAlarmOccurred: PodStatus
    private val immediateBolusWhenAlarmOccurred: Boolean
    private val occlusionType: Byte
    private val occurredWhenFetchingImmediateBolusActiveInformation: Boolean
    private val rssi: Short
    private val receiverLowerGain: Short
    private val podStatusWhenAlarmOccurred2: PodStatus
    private val returnAddressOfPodAlarmHandlerCaller: Short

    fun getMessageType(): Byte {
        return messageType
    }

    fun getMessageLength(): Short {
        return messageLength
    }

    fun getAdditionalStatusResponseType(): Byte {
        return additionalStatusResponseType
    }

    fun getPodStatus(): PodStatus {
        return podStatus
    }

    fun getDeliveryStatus(): DeliveryStatus {
        return deliveryStatus
    }

    fun getBolusPulsesRemaining(): Short {
        return bolusPulsesRemaining
    }

    fun getSequenceNumberOfLastProgrammingCommand(): Short {
        return sequenceNumberOfLastProgrammingCommand
    }

    fun getTotalPulsesDelivered(): Short {
        return totalPulsesDelivered
    }

    fun getAlarmType(): AlarmType {
        return alarmType
    }

    fun getAlarmTime(): Short {
        return alarmTime
    }

    fun getReservoirPulsesRemaining(): Short {
        return reservoirPulsesRemaining
    }

    fun getMinutesSinceActivation(): Short {
        return minutesSinceActivation
    }

    fun isAlert0Active(): Boolean {
        return alert0Active
    }

    fun isAlert1Active(): Boolean {
        return alert1Active
    }

    fun isAlert2Active(): Boolean {
        return alert2Active
    }

    fun isAlert3Active(): Boolean {
        return alert3Active
    }

    fun isAlert4Active(): Boolean {
        return alert4Active
    }

    fun isAlert5Active(): Boolean {
        return alert5Active
    }

    fun isAlert6Active(): Boolean {
        return alert6Active
    }

    fun isAlert7Active(): Boolean {
        return alert7Active
    }

    fun isOcclusionAlarm(): Boolean {
        return occlusionAlarm
    }

    fun isPulseInfoInvalid(): Boolean {
        return pulseInfoInvalid
    }

    fun getPodStatusWhenAlarmOccurred(): PodStatus {
        return podStatusWhenAlarmOccurred
    }

    fun isImmediateBolusWhenAlarmOccurred(): Boolean {
        return immediateBolusWhenAlarmOccurred
    }

    fun getOcclusionType(): Byte {
        return occlusionType
    }

    fun isOccurredWhenFetchingImmediateBolusActiveInformation(): Boolean {
        return occurredWhenFetchingImmediateBolusActiveInformation
    }

    fun getRssi(): Short {
        return rssi
    }

    fun getReceiverLowerGain(): Short {
        return receiverLowerGain
    }

    fun getPodStatusWhenAlarmOccurred2(): PodStatus {
        return podStatusWhenAlarmOccurred2
    }

    fun getReturnAddressOfPodAlarmHandlerCaller(): Short {
        return returnAddressOfPodAlarmHandlerCaller
    }

    override fun toString(): String {
        return "AlarmStatusResponse{" +
            "messageType=" + messageType +
            ", messageLength=" + messageLength +
            ", additionalStatusResponseType=" + additionalStatusResponseType +
            ", podStatus=" + podStatus +
            ", deliveryStatus=" + deliveryStatus +
            ", bolusPulsesRemaining=" + bolusPulsesRemaining +
            ", sequenceNumberOfLastProgrammingCommand=" + sequenceNumberOfLastProgrammingCommand +
            ", totalPulsesDelivered=" + totalPulsesDelivered +
            ", alarmType=" + alarmType +
            ", alarmTime=" + alarmTime +
            ", reservoirPulsesRemaining=" + reservoirPulsesRemaining +
            ", minutesSinceActivation=" + minutesSinceActivation +
            ", alert0Active=" + alert0Active +
            ", alert1Active=" + alert1Active +
            ", alert2Active=" + alert2Active +
            ", alert3Active=" + alert3Active +
            ", alert4Active=" + alert4Active +
            ", alert5Active=" + alert5Active +
            ", alert6Active=" + alert6Active +
            ", alert7Active=" + alert7Active +
            ", occlusionAlarm=" + occlusionAlarm +
            ", pulseInfoInvalid=" + pulseInfoInvalid +
            ", podStatusWhenAlarmOccurred=" + podStatusWhenAlarmOccurred +
            ", immediateBolusWhenAlarmOccurred=" + immediateBolusWhenAlarmOccurred +
            ", occlusionType=" + occlusionType +
            ", occurredWhenFetchingImmediateBolusActiveInformation=" + occurredWhenFetchingImmediateBolusActiveInformation +
            ", rssi=" + rssi +
            ", receiverLowerGain=" + receiverLowerGain +
            ", podStatusWhenAlarmOccurred2=" + podStatusWhenAlarmOccurred2 +
            ", returnAddressOfPodAlarmHandlerCaller=" + returnAddressOfPodAlarmHandlerCaller +
            ", statusResponseType=" + statusResponseType +
            ", responseType=" + responseType +
            ", encoded=" + Arrays.toString(encoded) +
            '}'
    }

    init {
        messageType = encoded[0]
        messageLength = (encoded[1].toInt() and 0xff).toShort()
        additionalStatusResponseType = encoded[2]
        podStatus = PodStatus.byValue((encoded[3] and 0x0f))
        deliveryStatus = DeliveryStatus.byValue((encoded[4] and 0x0f))
        bolusPulsesRemaining = (ByteBuffer.wrap(byteArrayOf(encoded[5], encoded[6])).short and 2047)
        sequenceNumberOfLastProgrammingCommand = (encoded[7] and 0x0f).toShort()
        totalPulsesDelivered = ByteBuffer.wrap(byteArrayOf(encoded[8], encoded[9])).short
        alarmType = AlarmType.byValue(encoded[10])
        alarmTime = ByteBuffer.wrap(byteArrayOf(encoded[11], encoded[12])).short
        reservoirPulsesRemaining = ByteBuffer.wrap(byteArrayOf(encoded[13], encoded[14])).short
        minutesSinceActivation = ByteBuffer.wrap(byteArrayOf(encoded[15], encoded[16])).short
        val activeAlerts = encoded[17].toInt() // TODO: toInt()?
        alert0Active = activeAlerts and 1 == 1
        alert1Active = activeAlerts ushr 1 and 1 == 1
        alert2Active = activeAlerts ushr 2 and 1 == 1
        alert3Active = activeAlerts ushr 3 and 1 == 1
        alert4Active = activeAlerts ushr 4 and 1 == 1
        alert5Active = activeAlerts ushr 5 and 1 == 1
        alert6Active = activeAlerts ushr 6 and 1 == 1
        alert7Active = activeAlerts ushr 7 and 1 == 1
        val alarmFlags = encoded[18]
        occlusionAlarm = (alarmFlags.toInt() and 1) == 1
        pulseInfoInvalid = alarmFlags shr 1 and 1 == 1
        val byte19 = encoded[19]
        val byte20 = encoded[20]
        podStatusWhenAlarmOccurred = PodStatus.byValue((byte19 and 0x0f))
        immediateBolusWhenAlarmOccurred = byte19 shr 4 and 1 == 1
        occlusionType = ((byte19 shr 5 and 3).toByte())
        occurredWhenFetchingImmediateBolusActiveInformation = byte19 shr 7 and 1 == 1
        rssi = (byte20 and 0x3f).toShort()
        receiverLowerGain = ((byte20 shr 6 and 0x03).toShort())
        podStatusWhenAlarmOccurred2 = PodStatus.byValue((encoded[21] and 0x0f))
        returnAddressOfPodAlarmHandlerCaller = ByteBuffer.wrap(byteArrayOf(encoded[22], encoded[23])).short
    }

    //TODO autoconvert to Int ok?
    private infix fun Byte.ushr(i: Int) = toInt() ushr i
    private infix fun Short.shr(i: Int): Int = toInt() shr i
    private infix fun Byte.shl(i: Int): Int = toInt() shl i
    private infix fun Byte.shr(i: Int): Int = toInt() shr i

}

