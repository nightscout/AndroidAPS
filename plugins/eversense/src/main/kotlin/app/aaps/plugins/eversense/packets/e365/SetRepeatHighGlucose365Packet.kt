package app.aaps.plugins.eversense.packets.e365

import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket

@EversensePacket(
    requestId = Eversense365Packets.WriteCommandId,
    responseId = Eversense365Packets.WriteResponseId,
    typeId = Eversense365Packets.WriteHighGlucoseAlarmRepeat,
    securityType = EversenseSecurityType.SecureV2
)
class SetRepeatHighGlucose365Packet(private val value: Int) : EversenseBasePacket() {
    override fun getRequestData(): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte()
    )
    override fun parseResponse(): Response = Response()
    class Response : EversenseBasePacket.Response()
}
