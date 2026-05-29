package com.nightscout.eversense.packets.e365

import com.nightscout.eversense.enums.EversenseAlarm
import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.models.ActiveAlarm
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.util.EversenseLogger

@EversensePacket(
    requestId = Eversense365Packets.ReadCommandId,
    responseId = Eversense365Packets.ReadResponseId,
    typeId = Eversense365Packets.ReadActiveAlerts,
    securityType = EversenseSecurityType.SecureV2
)
class GetActiveAlarmsPacket : EversenseBasePacket() {

    override fun getRequestData(): ByteArray = byteArrayOf()

    // 42 22 -> CmdType & CmdId
    // 03 -> Active alarm count
    // [code, flag, priority] * count -> 3 bytes per alarm
    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null

        val count = receivedData[2].toInt() and 0xFF
        val alarms = mutableListOf<ActiveAlarm>()

        for (i in 0 until count) {
            val offsetStart = i * 3 + 3
            if (receivedData.size < offsetStart + 3) {
                EversenseLogger.warning("GetActiveAlarmsPacket", "Missing data for alarm $i")
                break
            }
            alarms.add(ActiveAlarm(
                code = EversenseAlarm.from(receivedData[offsetStart].toInt() and 0xFF),
                codeRaw = receivedData[offsetStart].toInt() and 0xFF,
                flag = receivedData[offsetStart + 1].toInt() and 0xFF,
                priority = receivedData[offsetStart + 2].toInt() and 0xFF
            ))
        }

        alarms.sortBy { it.priority }
        EversenseLogger.info("GetActiveAlarmsPacket", "Active alarms: ${alarms.map { it.code.title }}")
        return Response(count = count, alarms = alarms)
    }

    data class Response(
        val count: Int,
        val alarms: List<ActiveAlarm>
    ) : EversenseBasePacket.Response()
}
