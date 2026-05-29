package com.nightscout.eversense.packets.e365

import com.nightscout.eversense.enums.EversenseAlarm
import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.packets.e365.utils.toUnix
import com.nightscout.eversense.util.EversenseLogger

data class AlertHistoryItem(
    val datetime: Long,
    val code: EversenseAlarm
)

@EversensePacket(
    requestId = Eversense365Packets.ReadCommandId,
    responseId = Eversense365Packets.ReadLogsId,
    typeId = Eversense365Packets.ReadLogValue,
    securityType = EversenseSecurityType.SecureV2
)
class GetAlertsLogValuesPacket(
    private val from: Int,
    private val to: Int
) : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        return byteArrayOf(
            Eversense365Packets.LogTypeAlerts,
            (from and 0xFF).toByte(), ((from shr 8) and 0xFF).toByte(),
            ((from shr 16) and 0xFF).toByte(), ((from shr 24) and 0xFF).toByte(),
            (to and 0xFF).toByte(), ((to shr 8) and 0xFF).toByte(),
            ((to shr 16) and 0xFF).toByte(), ((to shr 24) and 0xFF).toByte()
        )
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null

        if (receivedData[6].toInt() != Eversense365Packets.LogTypeAlerts.toInt()) {
            EversenseLogger.error("GetAlertsLogValuesPacket", "Invalid log type: ${receivedData[6]}")
            return Response(count = 0, alertHistory = emptyList())
        }

        val actualData = receivedData.drop(7).toUByteArray()
        val recordLength = 60
        val history = mutableListOf<AlertHistoryItem>()
        var i = 0

        while (i + recordLength <= actualData.size) {
            val chunk = actualData.copyOfRange(i, i + recordLength)
            val datetime = chunk.copyOfRange(4, 12).toUnix()
            val alarmCode = chunk[12].toInt() and 0xFF
            history.add(AlertHistoryItem(datetime = datetime, code = EversenseAlarm.from(alarmCode)))
            i += recordLength
        }

        return Response(count = history.size, alertHistory = history)
    }

    data class Response(
        val count: Int,
        val alertHistory: List<AlertHistoryItem>
    ) : EversenseBasePacket.Response()
}
