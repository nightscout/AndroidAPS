package com.nightscout.eversense.packets.e365

import com.nightscout.eversense.enums.EversenseAlarm
import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.models.ActiveAlarm
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.packets.e365.utils.toUnix

/**
 * Push notification packet for alarms with data payload.
 *
 * Packet format:
 * [0] = 0x44 (NotificationResponseId)
 * [1] = 0x03 (AlarmWithData)
 * [2] = reserved
 * [3] = alarm code
 * [4..11] = alarm datetime (Unix2000)
 * [12..] = alarm data
 */
@EversensePacket(
    requestId = Eversense365Packets.NotificationResponseId,
    responseId = Eversense365Packets.NotificationAlarmWithData,
    typeId = 0,
    securityType = EversenseSecurityType.SecureV2
)
class PushAlarmWithDataPacket : EversenseBasePacket() {

    override fun getRequestData(): ByteArray = ByteArray(0)

    override fun parseResponse(): Response? {
        if (receivedData.size < 12) return null

        val alarmCode = receivedData[3].toInt() and 0xFF
        val alarm = EversenseAlarm.from(alarmCode)
        val datetime = receivedData.copyOfRange(4, 12).toUnix()

        return Response(
            alarm = ActiveAlarm(code = alarm, codeRaw = alarmCode, flag = 0, priority = 0),
            datetime = datetime
        )
    }

    data class Response(
        val alarm: ActiveAlarm,
        val datetime: Long
    ) : EversenseBasePacket.Response()
}
