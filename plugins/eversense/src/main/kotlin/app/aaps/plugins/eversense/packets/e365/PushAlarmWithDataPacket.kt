package app.aaps.plugins.eversense.packets.e365

import app.aaps.plugins.eversense.enums.EversenseAlarm
import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket
import app.aaps.plugins.eversense.packets.e365.utils.toUnix

/**
 * Push notification packet for alarms with data payload.
 *
 * Packet format (empirically confirmed against real device logs - alarm code at [2] correctly
 * decoded real alarms like HIGH_GLUCOSE across multiple live captures; [3] was observed to be
 * constant/always 0, i.e. it's the reserved byte, not the code):
 * [0] = 0x44 (NotificationResponseId)
 * [1] = 0x03 (AlarmWithData)
 * [2] = alarm code
 * [3] = reserved
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

        val alarmCode = receivedData[2].toInt() and 0xFF
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
