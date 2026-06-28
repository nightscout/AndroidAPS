package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket

@EversensePacket(
    requestId = EversenseE3Packets.SaveBLEBondingInformationCommandId,
    responseId = EversenseE3Packets.SaveBLEBondingInformationResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class SaveBondingInformationPacket : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        return ByteArray(0)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }

        return SaveBondingInformationResponse()
    }

    class SaveBondingInformationResponse() : Response()
}