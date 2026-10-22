package app.aaps.pump.omnipod.common.bledriver.pod.response

import app.aaps.pump.omnipod.common.bledriver.pod.definition.PulseLogEntry
import app.aaps.pump.omnipod.common.bledriver.pod.response.ResponseType.StatusResponseType
import java.nio.ByteBuffer

/**
 * Status page 81 (0x51) - like page 80, but the (up to) 50 pulse-log entries
 * preceding the "recent" window. Byte layout ported from OmnipodKit's
 * PodInfoPulseLog.swift (`PodInfoPulseLogPrevious`) - see
 * [PodInfoTriggeredAlertsResponse]'s doc comment for the +2 offset-mapping rationale.
 */
class PodInfoPulseLogPreviousResponse(
    encoded: ByteArray
) : AdditionalStatusResponseBase(StatusResponseType.STATUS_RESPONSE_PAGE_81, encoded) {

    val messageType: Byte = encoded[0]
    val messageLength: Short = (encoded[1].toInt() and 0xff).toShort()
    val additionalStatusResponseType: Byte = encoded[2]

    /** Entry count the pod reports; may exceed the number of entries actually
     *  returned in this payload, matching OmnipodKit's own cross-check. */
    val nEntries: Int = ByteBuffer.wrap(byteArrayOf(encoded[3], encoded[4])).short.toInt() and 0xffff
    val pulseLog: List<Int>

    /** [pulseLog] decoded per-entry - see [PulseLogEntry]'s doc comment for what's confirmed
     *  vs. left as raw diagnostic values. */
    val decodedPulseLog: List<PulseLogEntry> by lazy { PulseLogEntry.decodeAll(pulseLog) }

    init {
        val logStartOffset = 5
        val nEntriesCalculated = (encoded.size - logStartOffset) / 4
        require(nEntries <= nEntriesCalculated) {
            "PulseLogPrevious reports $nEntries entries but only $nEntriesCalculated fit in ${encoded.size} bytes"
        }
        pulseLog = parsePulseLog(encoded, logStartOffset, nEntries)
    }

    override fun toString(): String {
        return "PodInfoPulseLogPreviousResponse(" +
            "messageType=$messageType, " +
            "messageLength=$messageLength, " +
            "additionalStatusResponseType=$additionalStatusResponseType, " +
            "nEntries=$nEntries, " +
            "pulseLog=$pulseLog" +
            ")"
    }
}
