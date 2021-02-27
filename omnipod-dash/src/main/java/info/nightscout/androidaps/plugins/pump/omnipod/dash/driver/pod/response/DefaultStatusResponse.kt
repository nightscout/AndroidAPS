package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.DeliveryStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.util.byValue
import kotlin.experimental.and

class DefaultStatusResponse(
    encoded: ByteArray
) : ResponseBase(ResponseType.DEFAULT_STATUS_RESPONSE, encoded) {

    val messageType: Byte = encoded[0]
    val deliveryStatus: DeliveryStatus = byValue((encoded[1].toInt() shr 4 and 0x0f).toByte(), DeliveryStatus.UNKNOWN)
    val podStatus: PodStatus = byValue((encoded[1] and 0x0f), PodStatus.UNKNOWN)
    val totalPulsesDelivered: Short = ((encoded[2] and 0x0f shl 12 or (encoded[3].toInt() and 0xff shl 1) or (encoded[4].toInt() and 0xff ushr 7)).toShort())
    val sequenceNumberOfLastProgrammingCommand: Short = (encoded[4] ushr 3 and 0x0f).toShort()
    val bolusPulsesRemaining: Short = ((encoded[4] and 0x07 shl 10 or (encoded[5].toInt() and 0xff) and 2047).toShort())
    val activeAlerts = (encoded[6].toInt() and 0xff shl 1 or (encoded[7] ushr 7)).toShort()
    val occlusionAlertActive: Boolean = (activeAlerts and 1).toInt() == 1
    val alert1Active: Boolean = activeAlerts shr 1 and 1 == 1
    val alert2Active: Boolean = activeAlerts shr 2 and 1 == 1
    val alert3Active: Boolean = activeAlerts shr 3 and 1 == 1
    val alert4Active: Boolean = activeAlerts shr 4 and 1 == 1
    val alert5Active: Boolean = activeAlerts shr 5 and 1 == 1
    val alert6Active: Boolean = activeAlerts shr 6 and 1 == 1
    val alert7Active: Boolean = activeAlerts shr 7 and 1 == 1
    val minutesSinceActivation: Short = (encoded[7] and 0x7f shl 6 or (encoded[8].toInt() and 0xff ushr 2 and 0x3f)).toShort()
    val reservoirPulsesRemaining: Short = (encoded[8] shl 8 or encoded[9].toInt() and 0x3ff).toShort()

    override fun toString(): String {
        return "DefaultStatusResponse{" +
            "messageType=" + messageType +
            ", deliveryStatus=" + deliveryStatus +
            ", podStatus=" + podStatus +
            ", totalPulsesDelivered=" + totalPulsesDelivered +
            ", sequenceNumberOfLastProgrammingCommand=" + sequenceNumberOfLastProgrammingCommand +
            ", bolusPulsesRemaining=" + bolusPulsesRemaining +
            ", occlusionAlertActive=" + occlusionAlertActive +
            ", alert1Active=" + alert1Active +
            ", alert2Active=" + alert2Active +
            ", alert3Active=" + alert3Active +
            ", alert4Active=" + alert4Active +
            ", alert5Active=" + alert5Active +
            ", alert6Active=" + alert6Active +
            ", alert7Active=" + alert7Active +
            ", minutesSinceActivation=" + minutesSinceActivation +
            ", reservoirPulsesRemaining=" + reservoirPulsesRemaining +
            ", responseType=" + responseType +
            ", encoded=" + encoded.contentToString() +
            '}'
    }
}

infix fun Byte.ushr(i: Int) = toInt() ushr i
infix fun Short.shr(i: Int): Int = toInt() shr i
infix fun Byte.shl(i: Int): Int = toInt() shl i
