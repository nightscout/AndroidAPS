package app.aaps.pump.omnipod.common.bledriver.pod.response

import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlarmType
import app.aaps.pump.omnipod.common.bledriver.pod.response.ResponseType.StatusResponseType
import app.aaps.pump.omnipod.common.bledriver.pod.util.byValue
import java.nio.ByteBuffer

/**
 * Status page 5 - pod activation date/time plus fault event code & fault time (same
 * fault info [AlarmStatusResponse] reports, available here even without a live alarm
 * status query). [year] is the raw 2-digit value the pod reports (e.g. 26 for 2026) -
 * converting to a full year/epoch timestamp is left to the caller.
 *
 * Byte layout ported from OmnipodKit's PodInfoActivationTime.swift - see
 * [PodInfoTriggeredAlertsResponse]'s doc comment for the +2 offset-mapping rationale.
 */
class PodInfoActivationTimeResponse(
    encoded: ByteArray
) : AdditionalStatusResponseBase(StatusResponseType.STATUS_RESPONSE_PAGE_5, encoded) {

    val messageType: Byte = encoded[0]
    val messageLength: Short = (encoded[1].toInt() and 0xff).toShort()
    val additionalStatusResponseType: Byte = encoded[2]
    val faultEventCode: AlarmType = byValue(encoded[3], AlarmType.UNKNOWN)
    val faultTime: Short = ByteBuffer.wrap(byteArrayOf(encoded[4], encoded[5])).short
    val month: Int = encoded[14].toInt() and 0xff
    val day: Int = encoded[15].toInt() and 0xff
    val year: Int = encoded[16].toInt() and 0xff
    val hour: Int = encoded[17].toInt() and 0xff
    val minute: Int = encoded[18].toInt() and 0xff

    override fun toString(): String {
        return "PodInfoActivationTimeResponse(" +
            "messageType=$messageType, " +
            "messageLength=$messageLength, " +
            "additionalStatusResponseType=$additionalStatusResponseType, " +
            "faultEventCode=$faultEventCode, " +
            "faultTime=$faultTime, " +
            "month=$month, " +
            "day=$day, " +
            "year=$year, " +
            "hour=$hour, " +
            "minute=$minute" +
            ")"
    }
}
