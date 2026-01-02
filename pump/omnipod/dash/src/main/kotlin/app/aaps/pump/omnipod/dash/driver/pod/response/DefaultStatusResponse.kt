package app.aaps.pump.omnipod.dash.driver.pod.response

import app.aaps.pump.omnipod.dash.driver.pod.definition.AlertType
import app.aaps.pump.omnipod.dash.driver.pod.definition.DeliveryStatus
import app.aaps.pump.omnipod.dash.driver.pod.definition.PodStatus
import app.aaps.pump.omnipod.dash.driver.pod.util.AlertUtil
import app.aaps.pump.omnipod.dash.driver.pod.util.byValue
import java.nio.ByteBuffer
import java.util.*
import kotlin.experimental.and

class DefaultStatusResponse(
    encoded: ByteArray
) : ResponseBase(ResponseType.DEFAULT_STATUS_RESPONSE, encoded) {

    val messageType: Byte = encoded[0]

    private var first4bytes = ByteBuffer.wrap(byteArrayOf(encoded[2], encoded[3], encoded[4], encoded[5])).int
    private var last4bytes = ByteBuffer.wrap(byteArrayOf(encoded[6], encoded[7], encoded[8], encoded[9])).int

    val podStatus: PodStatus = byValue((encoded[1] and 0x0f), PodStatus.UNKNOWN)
    val deliveryStatus: DeliveryStatus = byValue(((encoded[1].toInt() and 0xff) shr 4 and 0x0f).toByte(), DeliveryStatus.UNKNOWN)

    val totalPulsesDelivered: Short = (first4bytes ushr 11 ushr 4 and 0x1FFF).toShort()
    val sequenceNumberOfLastProgrammingCommand: Short = (first4bytes ushr 11 and 0X0F).toShort()
    val bolusPulsesRemaining: Short = (first4bytes and 0X7FF).toShort()

    val activeAlerts: EnumSet<AlertType> =
        AlertUtil.decodeAlertSet((last4bytes ushr 10 ushr 13 and 0xFF).toByte())
    val minutesSinceActivation: Short = ((last4bytes ushr 10 and 0x1FFF)).toShort()
    val reservoirPulsesRemaining: Short = (last4bytes and 0X3FF).toShort()

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
