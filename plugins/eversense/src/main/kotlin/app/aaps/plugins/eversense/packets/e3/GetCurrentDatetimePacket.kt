package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.util.EversenseLogger
import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket
import app.aaps.plugins.eversense.packets.e3.util.EversenseE3Parser
import java.util.TimeZone
import kotlin.math.abs

@EversensePacket(
    requestId = EversenseE3Packets.ReadCurrentTransmitterDateAndTimeCommandId,
    responseId = EversenseE3Packets.ReadCurrentTransmitterDateAndTimeResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class GetCurrentDatetimePacket : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        return ByteArray(0)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }

        val start = getStartIndex()
        val date = EversenseE3Parser.readDate(receivedData, start)
        val time = EversenseE3Parser.readTime(receivedData, start + 2)
        val timeZoneOffset = EversenseE3Parser.readTimezone(receivedData, start + 4)

        var needsTimeSync = false
        val actualTimeZoneOffset = TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()

        // Allow time drift <10s
        if (abs(System.currentTimeMillis() - (date + time)) > 10_000) {
            EversenseLogger.warning("GetCurrentDatetimePacket", "time drift detected... drift: ${abs(System.currentTimeMillis() - (date + time))} ms")
            needsTimeSync = true
        } else if (actualTimeZoneOffset != timeZoneOffset) {
            EversenseLogger.warning("GetCurrentDatetimePacket", "timezone mismatch - received: $timeZoneOffset, actual: $actualTimeZoneOffset")
            needsTimeSync = true
        }

        return Response(date + time, timeZoneOffset, needsTimeSync)
    }

    data class Response(val datetime: Long, val timezoneOffset: Long, val needsTimeSync: Boolean): EversenseBasePacket.Response()
}