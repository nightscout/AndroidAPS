package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.packets.e3.util.EversenseE3Writer

/**
 * Sends a blood glucose calibration value to the Eversense E3 transmitter.
 *
 * Uses the SendBloodGlucoseData command (ID 21) which accepts:
 * - BG value as Int16 (in mg/dL x10, e.g. 100 mg/dL = 1000)
 * - Current date (2 bytes) and time (2 bytes) as timestamps
 * - CRC16 checksum of the payload
 *
 * The transmitter must be in CalibrationReadiness.READY state before calling this.
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

        // BG value is sent as mg/dL * 10 to preserve one decimal place
        val bgEncoded = EversenseE3Writer.writeInt16(glucoseMgDl * 10)
        val date = EversenseE3Writer.writeDate(now)
        val time = EversenseE3Writer.writeTime(now)

        val payload = bgEncoded + date + time

        // Append CRC16 checksum of the payload for data integrity
        val crc = EversenseE3Writer.generateChecksumCRC16(payload)

        return payload + crc
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}