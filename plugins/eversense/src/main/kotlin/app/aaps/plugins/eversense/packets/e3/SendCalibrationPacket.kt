package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket
import app.aaps.plugins.eversense.packets.e3.util.EversenseE3Writer

/**
 * Sends a blood glucose calibration value to the Eversense E3 transmitter.
 *
 * Packet structure verified against official Eversense app
 * (decompiled from operationToSendBloodGlucoseValueWithTwoTimestampsToTransmitter):
 *
 * [0]    = 0x3C (60) — command ID for E3 two-timestamp calibration
 * [1-2]  = sampleDate — FAT-encoded date of the BG measurement
 * [3-4]  = sampleTime — FAT-encoded time of the BG measurement
 * [5-6]  = currentDate — FAT-encoded date of submission (NOW) ← critical, was missing
 * [7-8]  = currentTime — FAT-encoded time of submission (NOW)
 * [9]    = glucoseMgDl raw value (low byte)
 * [10]   = glucose MSB (data16BitsFromIntLSByteFirst[1])
 * [11]   = glucose LSB (data16BitsFromIntLSByteFirst[0])
 * [12]   = 0x00 — additional param (zeros)
 * [13]   = 0x00 — additional param (zeros)
 * [14]   = 0x00 — rolling cal disabled for non-US devices
 * [15-16]= CRC16, appended by buildRequest()
 *
 * NOTE: The previous implementation used command 0x15 (single timestamp, 365-style)
 * which caused the E3 transmitter to read currentTime bytes as the glucose value,
 * producing wildly incorrect readings (e.g. 36416 mg/dL = FAT time 17:50 UTC).
 *
 * @param glucoseMgDl  Blood glucose value in mg/dL
 * @param sampleTimeMs Timestamp of the BG measurement (defaults to now)
 */
@EversensePacket(
    requestId = EversenseE3Packets.SendBloodGlucoseDataWithTwoTimestampsCommandId,
    responseId = EversenseE3Packets.SendBloodGlucoseDataWithTwoTimestampsResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class SendCalibrationPacket(
    private val glucoseMgDl: Int,
    private val sampleTimeMs: Long = System.currentTimeMillis()
) : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        val now = System.currentTimeMillis()

        val sampleDate = EversenseE3Writer.writeDate(sampleTimeMs)
        val sampleTime = EversenseE3Writer.writeTime(sampleTimeMs)
        val currentDate = EversenseE3Writer.writeDate(now)   // current date of submission
        val currentTime = EversenseE3Writer.writeTime(now)   // current time of submission

        // Official app uses data16BitsFromIntLSByteFirst: [LSB, MSB]
        val bgLsb = (glucoseMgDl and 0xFF).toByte()
        val bgMsb = ((glucoseMgDl shr 8) and 0xFF).toByte()

        return byteArrayOf(
            sampleDate[0], sampleDate[1],   // [1-2]  sample date
            sampleTime[0], sampleTime[1],   // [3-4]  sample time
            currentDate[0], currentDate[1], // [5-6]  current submission date
            currentTime[0], currentTime[1], // [7-8]  current submission time
            bgLsb,                          // [9]    glucose LSB (data16Bits low byte)
            bgMsb,                          // [10]   glucose MSB (data16Bits high byte)
            0x00.toByte(),                  // [11]   param7[1] = 0
            0x00.toByte(),                  // [12]   param7[0] = 0
            0x00.toByte(),                  // [13]   param6 = 0
            0x55.toByte()                   // [14]   0x55 = calibration flag (confirmed from iOS EversenseKit PR#35)
        )
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}
