package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.packets.e3.util.EversenseE3Writer

/**
 * Sends a blood glucose calibration value to the Eversense E3 transmitter.
 *
 * Uses the SendBloodGlucoseData command (ID 21) which accepts:
 * - BG value as Int16 in mg/dL (little-endian)
 * - Current date (2 bytes) and time (2 bytes) as packed timestamps
 * CRC16 is appended by EversenseBasePacket.buildRequest() — do not add it here.
 *
 * @param glucoseMgDl Blood glucose value in mg/dL
 */
@EversensePacket(
    requestId = EversenseE3Packets.SendBloodGlucoseDataCommandId,
    responseId = EversenseE3Packets.SendBloodGlucoseDataResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class SendCalibrationPacket(private val glucoseMgDl: Int) : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        val now = System.currentTimeMillis()
        val bgEncoded = EversenseE3Writer.writeInt16(glucoseMgDl)
        val date = EversenseE3Writer.writeDate(now)
        val time = EversenseE3Writer.writeTime(now)
        return bgEncoded + date + time
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}