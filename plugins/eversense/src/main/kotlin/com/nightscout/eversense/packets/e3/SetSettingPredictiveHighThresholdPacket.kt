package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.EversenseE3Memory
import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.packets.e3.util.EversenseE3Writer

@EversensePacket(
    requestId = EversenseE3Packets.WriteTwoByteSerialFlashRegisterCommandId,
    responseId = EversenseE3Packets.WriteTwoByteSerialFlashRegisterResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class SetSettingPredictiveHighThresholdPacket(private val threshold: Int) : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.PredictiveHighTarget.getRequestData() + EversenseE3Writer.writeInt16(threshold)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }

        return Response()
    }

    class Response : EversenseBasePacket.Response()
}