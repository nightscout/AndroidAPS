package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.enums.EversenseE3Memory
import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket

@EversensePacket(
    requestId = EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId,
    responseId = EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class GetBatteryPercentagePacket : EversenseBasePacket() {
    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.BatteryPercentage.getRequestData()
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        // The E3 battery register returns an enum index (0-11). Clamp to valid range.
        val raw = receivedData[getStartIndex()].toInt() and 0xFF
        val percentage = raw.coerceIn(0, 11)
        return Response(percentage = percentage)
    }

    data class Response(val percentage: Int) : EversenseBasePacket.Response()
}