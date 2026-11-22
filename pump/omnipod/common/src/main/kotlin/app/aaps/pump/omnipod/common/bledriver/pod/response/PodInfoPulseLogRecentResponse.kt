package app.aaps.pump.omnipod.common.bledriver.pod.response

import app.aaps.pump.omnipod.common.bledriver.pod.definition.PulseLogEntry
import app.aaps.pump.omnipod.common.bledriver.pod.response.ResponseType.StatusResponseType
import java.nio.ByteBuffer

/**
 * Status page 80 (0x50) - the most recent (up to) 50 pulse-log entries. Byte layout
 * ported from OmnipodKit's PodInfoPulseLog.swift (`PodInfoPulseLogRecent`) - see
 * [PodInfoTriggeredAlertsResponse]'s doc comment for the +2 offset-mapping rationale.
 */
class PodInfoPulseLogRecentResponse(
    encoded: ByteArray
) : AdditionalStatusResponseBase(StatusResponseType.STATUS_RESPONSE_PAGE_80, encoded) {

    val messageType: Byte = encoded[0]
    val messageLength: Short = (encoded[1].toInt() and 0xff).toShort()
    val additionalStatusResponseType: Byte = encoded[2]
    val indexLastEntry: Int = ByteBuffer.wrap(byteArrayOf(encoded[3], encoded[4])).short.toInt() and 0xffff
    val pulseLog: List<Int>

    /** [pulseLog] decoded per-entry - see [PulseLogEntry]'s doc comment for what's confirmed
     *  vs. left as raw diagnostic values. */
    val decodedPulseLog: List<PulseLogEntry> by lazy { PulseLogEntry.decodeAll(pulseLog) }

    init {
        val logStartOffset = 5
        val nEntries = (encoded.size - logStartOffset) / 4
        pulseLog = parsePulseLog(encoded, logStartOffset, nEntries)
    }

    override fun toString(): String {
        return "PodInfoPulseLogRecentResponse(" +
            "messageType=$messageType, " +
            "messageLength=$messageLength, " +
            "additionalStatusResponseType=$additionalStatusResponseType, " +
            "indexLastEntry=$indexLastEntry, " +
            "pulseLog=$pulseLog" +
            ")"
    }
}
