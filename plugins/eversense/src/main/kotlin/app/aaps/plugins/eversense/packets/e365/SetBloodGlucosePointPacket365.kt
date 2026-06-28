package app.aaps.plugins.eversense.packets.e365

import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket
import app.aaps.plugins.eversense.packets.e365.utils.toUnixArray

@EversensePacket(
    requestId = Eversense365Packets.WriteCommandId,
    responseId = Eversense365Packets.WriteResponseId,
    typeId = Eversense365Packets.WriteCalibration,
    securityType = EversenseSecurityType.SecureV2
)
class SetBloodGlucosePointPacket365(private val glucoseInMgDl: Int, private val timestampMs: Long = System.currentTimeMillis()) : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        var data = timestampMs.toUnixArray()      // fingerstick measurement timestamp
        data += System.currentTimeMillis().toUnixArray()  // current time
        data += byteArrayOf(
            (glucoseInMgDl and 0xFF).toByte(),
            ((glucoseInMgDl shr 8) and 0xFF).toByte()
        )
        data += byteArrayOf(1, 0, 0)
        return data
    }

    override fun parseResponse(): Response {
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}
