package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.DeliveryStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.util.AlertUtil
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.util.byValue
import java.util.*
import kotlin.experimental.and

class DefaultStatusResponse(
    encoded: ByteArray
) : ResponseBase(ResponseType.DEFAULT_STATUS_RESPONSE, encoded) {

    val messageType: Byte = encoded[0]
    val deliveryStatus: DeliveryStatus = byValue((encoded[1].toInt() shr 4 and 0x0f).toByte(), DeliveryStatus.UNKNOWN)
    val podStatus: PodStatus = byValue((encoded[1] and 0x0f), PodStatus.UNKNOWN)
    val totalPulsesDelivered: Short =
        (encoded[2] and 0x0f shl 9 or (encoded[3].toInt() and 0xff shl 1) or (encoded[4].toInt() and 0xff ushr 7)).toShort()

    val sequenceNumberOfLastProgrammingCommand: Short = (encoded[4] ushr 3 and 0x0f).toShort()
    val bolusPulsesRemaining: Short = ((encoded[4] and 0x07 shl 10 or (encoded[5].toInt() and 0xff) and 2047).toShort())
    val activeAlerts: EnumSet<AlertType> =
        AlertUtil.decodeAlertSet((encoded[6].toInt() and 0xff shl 1 or (encoded[7] ushr 7)).toByte())
    val minutesSinceActivation: Short =
        (encoded[7] and 0x7f shl 6 or (encoded[8].toInt() and 0xff ushr 2 and 0x3f)).toShort()
    val reservoirPulsesRemaining: Short = (encoded[8] shl 8 or encoded[9].toInt() and 0x3ff).toShort()

    override fun toString(): String {
        return "DefaultStatusResponse(" +
            "messageType=$messageType" +
            ", deliveryStatus=$deliveryStatus" +
            ", podStatus=$podStatus" +
            ", totalPulsesDelivered=$totalPulsesDelivered" +
            ", sequenceNumberOfLastProgrammingCommand=$sequenceNumberOfLastProgrammingCommand" +
            ", bolusPulsesRemaining=$bolusPulsesRemaining" +
            ", activeAlerts=$activeAlerts" +
            ", minutesSinceActivation=$minutesSinceActivation" +
            ", reservoirPulsesRemaining=$reservoirPulsesRemaining)"
    }
}

infix fun Byte.ushr(i: Int) = toInt() ushr i
infix fun Byte.shl(i: Int): Int = toInt() shl i
