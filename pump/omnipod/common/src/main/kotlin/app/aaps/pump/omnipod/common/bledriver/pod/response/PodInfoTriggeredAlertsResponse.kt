package app.aaps.pump.omnipod.common.bledriver.pod.response

import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlertType
import app.aaps.pump.omnipod.common.bledriver.pod.response.ResponseType.StatusResponseType
import java.nio.ByteBuffer

/**
 * Status page 1 - unacknowledged triggered-alert values, one per [AlertType] slot
 * (pod-clock minutes-since-activation each alert fired, or 0 if never triggered).
 * Byte layout ported from OmnipodKit's PodInfoTriggeredAlerts.swift; that reference
 * implementation's `init` operates on a Data slice with the subtype byte already at
 * index 0, so its documented offsets are shifted by +2 here to match this codebase's
 * convention (used by [AlarmStatusResponse] etc.) of indexing the full response,
 * including the messageType/length prefix.
 */
class PodInfoTriggeredAlertsResponse(
    encoded: ByteArray
) : AdditionalStatusResponseBase(StatusResponseType.STATUS_RESPONSE_PAGE_1, encoded) {

    val messageType: Byte = encoded[0]
    val messageLength: Short = (encoded[1].toInt() and 0xff).toShort()
    val additionalStatusResponseType: Byte = encoded[2]
    val unknownWord: Short = ByteBuffer.wrap(byteArrayOf(encoded[3], encoded[4])).short
    val alertActivations: Map<AlertType, Short> = AlertType.entries
        .filter { it != AlertType.UNKNOWN }
        .sortedBy { it.index }
        .mapIndexed { i, slot ->
            val offset = 5 + 2 * i
            slot to ByteBuffer.wrap(byteArrayOf(encoded[offset], encoded[offset + 1])).short
        }.toMap()

    override fun toString(): String {
        return "PodInfoTriggeredAlertsResponse(" +
            "messageType=$messageType, " +
            "messageLength=$messageLength, " +
            "additionalStatusResponseType=$additionalStatusResponseType, " +
            "unknownWord=$unknownWord, " +
            "alertActivations=$alertActivations" +
            ")"
    }
}
