package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.EversenseE3Memory
import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.packets.e3.util.EversenseE3Parser

@EversensePacket(
    requestId = EversenseE3Packets.ReadTwoByteSerialFlashRegisterCommandId,
    responseId = EversenseE3Packets.ReadTwoByteSerialFlashRegisterResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class GetSettingGlucoseLowThresholdPacket : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.LowGlucoseAlarmThreshold.getRequestData()
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }

        return Response(threshold = EversenseE3Parser.readGlucose(receivedData, getStartIndex()))
    }

    data class Response(val threshold: Int): EversenseBasePacket.Response()
}