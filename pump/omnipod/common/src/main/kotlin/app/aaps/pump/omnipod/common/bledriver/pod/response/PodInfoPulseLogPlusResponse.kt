package app.aaps.pump.omnipod.common.bledriver.pod.response

import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlarmType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PulseLogEntry
import app.aaps.pump.omnipod.common.bledriver.pod.response.ResponseType.StatusResponseType
import app.aaps.pump.omnipod.common.bledriver.pod.util.byValue
import java.nio.ByteBuffer

/**
 * Status page 3 - up to the last 60 pulse-log entries plus fault/activation-time
 * info. Byte layout ported from OmnipodKit's PodInfoPulseLogPlus.swift - see
 * [PodInfoTriggeredAlertsResponse]'s doc comment for the +2 offset-mapping rationale.
 */
class PodInfoPulseLogPlusResponse(
    encoded: ByteArray
) : AdditionalStatusResponseBase(StatusResponseType.STATUS_RESPONSE_PAGE_3, encoded) {

    val messageType: Byte = encoded[0]
    val messageLength: Short = (encoded[1].toInt() and 0xff).toShort()
    val additionalStatusResponseType: Byte = encoded[2]
    val faultEventCode: AlarmType = byValue(encoded[3], AlarmType.UNKNOWN)
    val faultTime: Short = ByteBuffer.wrap(byteArrayOf(encoded[4], encoded[5])).short
    val activationTime: Short = ByteBuffer.wrap(byteArrayOf(encoded[6], encoded[7])).short
    val entrySize: Int = encoded[8].toInt() and 0xff
    val maxEntries: Int = encoded[9].toInt() and 0xff
    val pulseLog: List<Int>

    /** [pulseLog] decoded per-entry - see [PulseLogEntry]'s doc comment for what's confirmed
     *  vs. left as raw diagnostic values. */
    val decodedPulseLog: List<PulseLogEntry> by lazy { PulseLogEntry.decodeAll(pulseLog) }

    init {
        require(entrySize == 4) { "Unexpected pulseLogPlus entry size: $entrySize" }
        val logStartOffset = 10
        val nEntries = (encoded.size - logStartOffset) / 4
        pulseLog = parsePulseLog(encoded, logStartOffset, nEntries)
    }

    override fun toString(): String {
        return "PodInfoPulseLogPlusResponse(" +
            "messageType=$messageType, " +
            "messageLength=$messageLength, " +
            "additionalStatusResponseType=$additionalStatusResponseType, " +
            "faultEventCode=$faultEventCode, " +
            "faultTime=$faultTime, " +
            "activationTime=$activationTime, " +
            "entrySize=$entrySize, " +
            "maxEntries=$maxEntries, " +
            "pulseLog=$pulseLog" +
            ")"
    }
}
