package app.aaps.plugins.eversense.packets.e365

import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket

@EversensePacket(
    requestId = Eversense365Packets.WriteCommandId,
    responseId = Eversense365Packets.WriteResponseId,
    typeId = Eversense365Packets.WriteBleDisconnect,
    securityType = EversenseSecurityType.SecureV2
)
class SetBleDisconnect365Packet(private val intervalSeconds: Int = 300) : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        // UInt16 little-endian interval in seconds
        return byteArrayOf(
            (intervalSeconds and 0xFF).toByte(),
            ((intervalSeconds shr 8) and 0xFF).toByte()
        )
    }

    override fun parseResponse(): Response = Response()
    class Response : EversenseBasePacket.Response()
}