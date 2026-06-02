package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.enums.EversenseE3Memory
import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket

@EversensePacket(
    requestId = EversenseE3Packets.ReadTwoByteSerialFlashRegisterCommandId,
    responseId = EversenseE3Packets.ReadTwoByteSerialFlashRegisterResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class GetBleDisconnectPacket : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.BleDisconnect.getRequestData()
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null

        val s = getStartIndex()
        val intervalSeconds = (receivedData[s].toInt() and 0xFF) or ((receivedData[s + 1].toInt() and 0xFF) shl 8)
        return Response(intervalSeconds = intervalSeconds)
    }

    data class Response(val intervalSeconds: Int) : EversenseBasePacket.Response()
}
