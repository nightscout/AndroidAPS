package app.aaps.plugins.eversense.packets.e365

import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket
import app.aaps.plugins.eversense.packets.e365.utils.toByteArray
import app.aaps.plugins.eversense.packets.e365.utils.toTimeZone
import app.aaps.plugins.eversense.packets.e365.utils.toUnixArray
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.math.abs

@EversensePacket(
    requestId = Eversense365Packets.WriteCommandId,
    responseId = Eversense365Packets.WriteResponseId,
    typeId = Eversense365Packets.WriteCurrentDateTime,
    securityType = EversenseSecurityType.SecureV2
)
class SetCurrentDateTimePacket : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        val now = System.currentTimeMillis()
        val timezoneOffset = TimeZone.getDefault().getOffset(now)
        val timezoneNegative = if (timezoneOffset < 0) 255.toByte() else 0.toByte()

        var request = now.toUnixArray()
        request += abs(timezoneOffset).toTimeZone()
        request += byteArrayOf(timezoneNegative)

        return request
    }

    override fun parseResponse(): Response {
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}