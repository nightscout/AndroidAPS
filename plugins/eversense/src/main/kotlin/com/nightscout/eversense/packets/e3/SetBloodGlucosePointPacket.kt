package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.packets.e3.util.EversenseE3Writer
import com.nightscout.eversense.packets.e365.utils.toUnixArray

/**
 * Sends a blood glucose calibration point using Unix2000 timestamps.
 * Accepts a specific sample timestamp plus the current time, matching the
 * iOS SendBloodGlucoseDataWithTwoTimestamps protocol format.
 *
 * @param glucoseInMgDl Blood glucose value in mg/dL
 * @param sampleTimestamp Epoch milliseconds of the blood glucose sample
 */
@EversensePacket(
    requestId = EversenseE3Packets.SendBloodGlucoseDataCommandId,
    responseId = EversenseE3Packets.SendBloodGlucoseDataResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class SetBloodGlucosePointPacket(
    private val glucoseInMgDl: Int,
    private val sampleTimestamp: Long = System.currentTimeMillis()
) : EversenseBasePacket() {
    override val skipResponseIdValidation: Boolean = true

    override fun getRequestData(): ByteArray {
        val now = System.currentTimeMillis()
        return sampleTimestamp.toUnixArray() +
            now.toUnixArray() +
            EversenseE3Writer.writeInt16(glucoseInMgDl) +
            byteArrayOf(0x55)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}
