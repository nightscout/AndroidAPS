package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket
import app.aaps.plugins.eversense.packets.e3.util.EversenseE3Writer

@EversensePacket(
    requestId = EversenseE3Packets.SetCurrentTransmitterDateAndTimeCommandId,
    responseId = EversenseE3Packets.SetCurrentTransmitterDateAndTimeResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class SetCurrentDatetimePacket : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        val now = System.currentTimeMillis()
        return EversenseE3Writer.writeDate(now) +
            EversenseE3Writer.writeTime(now) +
            EversenseE3Writer.writeTimezone(now)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }

        return Response()
    }

    class Response : EversenseBasePacket.Response() {}
}