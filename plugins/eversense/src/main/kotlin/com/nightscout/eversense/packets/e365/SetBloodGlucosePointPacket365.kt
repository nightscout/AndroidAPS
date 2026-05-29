package com.nightscout.eversense.packets.e365

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.packets.e365.utils.toUnixArray

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
