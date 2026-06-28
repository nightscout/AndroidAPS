package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket
import app.aaps.plugins.eversense.util.MessageCoder

@EversensePacket(
    requestId = EversenseE3Packets.ReadSensorGlucoseAlertsAndStatusCommandId,
    responseId = EversenseE3Packets.ReadSensorGlucoseAlertsAndStatusResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class GetGlucoseAlertsAndStatusPacket : EversenseBasePacket() {

    private val STATUS_FLAG_COUNT = 13

    override fun getRequestData(): ByteArray = ByteArray(0)

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null

        val s = getStartIndex()
        val rawContent = receivedData.drop(s + 1).dropLast(2).map { it.toInt() and 0xFF }
        val content = IntArray(STATUS_FLAG_COUNT)
        val offset = STATUS_FLAG_COUNT - rawContent.size.coerceAtMost(STATUS_FLAG_COUNT)
        rawContent.take(STATUS_FLAG_COUNT).forEachIndexed { i, v -> content[offset + i] = v }

        if (content.all { it == 0 }) return Response(alarms = emptyList())

        val alarms = mutableListOf<ActiveAlarm>()

        fun add(alarm: app.aaps.plugins.eversense.enums.EversenseAlarm?) {
            alarm?.let { alarms.add(ActiveAlarm(code = it, codeRaw = it.code, flag = 0, priority = 0)) }
        }

        add(MessageCoder.messageCodeForGlucoseLevelAlarmFlags(content[0]))
        add(MessageCoder.messageCodeForGlucoseLevelAlertFlags(content[1]))
        add(MessageCoder.messageCodeForRateAlertFlags(content[2]))
        add(MessageCoder.messageCodeForPredictiveAlertFlags(content[3]))
        add(MessageCoder.messageCodeForSensorHardwareAndAlertFlags(content[4]))
        add(MessageCoder.messageCodeForSensorReadAlertFlags(content[5]))
        add(MessageCoder.messageCodeForSensorReplacementFlags(content[6]))
        add(MessageCoder.messageCodeForSensorCalibrationFlags(content[7]))
        add(MessageCoder.messageCodeForTransmitterStatusAlertFlags(content[8]))
        add(MessageCoder.messageCodeForTransmitterBatteryAlertFlags(content[9]))
        add(MessageCoder.messageCodeForTransmitterEOLAlertFlags(content[10]))
        add(MessageCoder.messageCodeForSensorReplacementFlags2(content[11]))
        add(MessageCoder.messageCodeForCalibrationSwitchFlags(content[12]))

        return Response(alarms = alarms)
    }

    data class Response(val alarms: List<ActiveAlarm>) : EversenseBasePacket.Response()
}
