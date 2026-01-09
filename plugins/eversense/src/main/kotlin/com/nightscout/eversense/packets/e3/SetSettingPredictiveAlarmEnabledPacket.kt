package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.EversenseE3Memory
import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.packets.e3.util.EversenseE3Writer

@EversensePacket(
    requestId = EversenseE3Packets.WriteSingleByteSerialFlashRegisterCommandId,
    responseId = EversenseE3Packets.WriteSingleByteSerialFlashRegisterResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class SetSettingPredictiveAlarmEnabledPacket(private val enabled: Boolean) : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.PredictiveAlert.getRequestData() + EversenseE3Writer.writeBoolean(enabled)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }

        return Response()
    }

    class Response : EversenseBasePacket.Response()
}