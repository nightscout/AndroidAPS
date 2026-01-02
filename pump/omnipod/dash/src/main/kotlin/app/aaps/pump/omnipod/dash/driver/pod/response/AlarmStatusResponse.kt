package app.aaps.pump.omnipod.dash.driver.pod.response

import app.aaps.pump.omnipod.dash.driver.pod.definition.AlarmType
import app.aaps.pump.omnipod.dash.driver.pod.definition.AlertType
import app.aaps.pump.omnipod.dash.driver.pod.definition.DeliveryStatus
import app.aaps.pump.omnipod.dash.driver.pod.definition.PodStatus
import app.aaps.pump.omnipod.dash.driver.pod.response.ResponseType.StatusResponseType
import app.aaps.pump.omnipod.dash.driver.pod.util.AlertUtil
import app.aaps.pump.omnipod.dash.driver.pod.util.byValue
import java.nio.ByteBuffer
import java.util.*
import kotlin.experimental.and

class AlarmStatusResponse(
    encoded: ByteArray
) : AdditionalStatusResponseBase(StatusResponseType.ALARM_STATUS, encoded) {

    val messageType: Byte = encoded[0]
    val messageLength: Short = (encoded[1].toInt() and 0xff).toShort()
    val additionalStatusResponseType: Byte = encoded[2]
    val podStatus: PodStatus = byValue((encoded[3] and 0x0f), PodStatus.UNKNOWN)
    val deliveryStatus: DeliveryStatus = byValue((encoded[4] and 0x0f), DeliveryStatus.UNKNOWN)
    val bolusPulsesRemaining: Short = (ByteBuffer.wrap(byteArrayOf(encoded[5], encoded[6])).short and 2047)
    val sequenceNumberOfLastProgrammingCommand: Short = (encoded[7] and 0x0f).toShort()
    val totalPulsesDelivered: Short = ByteBuffer.wrap(byteArrayOf(encoded[8], encoded[9])).short
    val alarmType: AlarmType = byValue(encoded[10], AlarmType.UNKNOWN)
    val alarmTime: Short = ByteBuffer.wrap(byteArrayOf(encoded[11], encoded[12])).short
    val reservoirPulsesRemaining: Short = ByteBuffer.wrap(byteArrayOf(encoded[13], encoded[14])).short
    val minutesSinceActivation: Short = ByteBuffer.wrap(byteArrayOf(encoded[15], encoded[16])).short
    val activeAlerts: EnumSet<AlertType> = AlertUtil.decodeAlertSet(encoded[17])
    val occlusionAlarm: Boolean
    val pulseInfoInvalid: Boolean
    val podStatusWhenAlarmOccurred: PodStatus
    val immediateBolusWhenAlarmOccurred: Boolean
    val occlusionType: Byte
    val occurredWhenFetchingImmediateBolusActiveInformation: Boolean
    val rssi: Short
    val receiverLowerGain: Short
    val podStatusWhenAlarmOccurred2: PodStatus
    val returnAddressOfPodAlarmHandlerCaller: Short

    init {
        val alarmFlags = encoded[18]
        occlusionAlarm = (alarmFlags.toInt() and 1) == 1
        pulseInfoInvalid = alarmFlags shr 1 and 1 == 1
        val byte19 = encoded[19]
        val byte20 = encoded[20]
        podStatusWhenAlarmOccurred = byValue((byte19 and 0x0f), PodStatus.UNKNOWN)
        immediateBolusWhenAlarmOccurred = byte19 shr 4 and 1 == 1
        occlusionType = ((byte19 shr 5 and 3).toByte())
        occurredWhenFetchingImmediateBolusActiveInformation = byte19 shr 7 and 1 == 1
        rssi = (byte20 and 0x3f).toShort()
        receiverLowerGain = ((byte20 shr 6 and 0x03).toShort())
        podStatusWhenAlarmOccurred2 = byValue((encoded[21] and 0x0f), PodStatus.UNKNOWN)
        returnAddressOfPodAlarmHandlerCaller = ByteBuffer.wrap(byteArrayOf(encoded[22], encoded[23])).short
    }

    override fun toString(): String {
        return "AlarmStatusResponse(" +
            "messageType=$messageType, " +
            "messageLength=$messageLength, " +
            "additionalStatusResponseType=$additionalStatusResponseType, " +
            "podStatus=$podStatus, " +
            "deliveryStatus=$deliveryStatus, " +
            "bolusPulsesRemaining=$bolusPulsesRemaining, " +
            "sequenceNumberOfLastProgrammingCommand=$sequenceNumberOfLastProgrammingCommand, " +
            "totalPulsesDelivered=$totalPulsesDelivered, " +
            "alarmType=$alarmType, " +
            "alarmTime=$alarmTime, " +
            "reservoirPulsesRemaining=$reservoirPulsesRemaining, " +
            "minutesSinceActivation=$minutesSinceActivation, " +
            "activeAlerts=$activeAlerts, " +
            "occlusionAlarm=$occlusionAlarm, " +
            "pulseInfoInvalid=$pulseInfoInvalid, " +
            "podStatusWhenAlarmOccurred=$podStatusWhenAlarmOccurred, " +
            "immediateBolusWhenAlarmOccurred=$immediateBolusWhenAlarmOccurred, " +
            "occlusionType=$occlusionType, " +
            "occurredWhenFetchingImmediateBolusActiveInformation=$occurredWhenFetchingImmediateBolusActiveInformation, " +
            "rssi=$rssi, " +
            "receiverLowerGain=$receiverLowerGain, " +
            "podStatusWhenAlarmOccurred2=$podStatusWhenAlarmOccurred2, " +
            "returnAddressOfPodAlarmHandlerCaller=$returnAddressOfPodAlarmHandlerCaller" +
            ")"
    }

    infix fun Byte.shr(i: Int): Int = toInt() shr i
}
