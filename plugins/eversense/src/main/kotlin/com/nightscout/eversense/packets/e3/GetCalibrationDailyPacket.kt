package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.EversenseE3Memory
import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket

@EversensePacket(
    requestId = EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId,
    responseId = EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class GetCalibrationDailyPacket : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.IsOneCalibration.getRequestData()
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        // Official app: IsOneCalibration register value 0x01 = one calibration per day (daily single)
        return Response(isDaily = receivedData[getStartIndex()].toInt() and 0xFF == 0x01)
    }

    data class Response(val isDaily: Boolean) : EversenseBasePacket.Response()
}