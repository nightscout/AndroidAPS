package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.enums.EversenseE3Memory
import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket

@EversensePacket(
    requestId = EversenseE3Packets.WriteSingleByteSerialFlashRegisterCommandId,
    responseId = EversenseE3Packets.WriteSingleByteSerialFlashRegisterResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class SetSettingPredictiveHighTimePacket(private val minutes: Int) : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.PredictiveHighTime.getRequestData() + byteArrayOf(minutes.toByte())
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }

        return Response()
    }

    class Response : EversenseBasePacket.Response()
}