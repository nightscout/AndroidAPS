package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket
import app.aaps.plugins.eversense.packets.e3.util.EversenseE3Writer

/**
 * Sends a blood glucose calibration point to an Eversense E3 transmitter using the
 * "two timestamps" command (0x3C / response 0xBC).
 *
 * Ported from loopandlearn/EversenseKit commit b79d858 (SetBloodGlucosePointPacket.swift).
 * The E3 transmitter expects the SendBloodGlucoseDataWithTwoTimestamps command, NOT the
 * older single-format SendBloodGlucoseData command (0x15). The previous implementation
 * used the old command id with packed Unix2000 timestamps (toUnixArray), which the E3 did
 * not accept correctly - the symptom being that calibrations did not register and the
 * last/next calibration dates stayed wrong.
 *
 * Body produced by getRequestData() (the framework prepends the 0x3C command id from the
 * annotation and appends the CRC16):
 *   writeDate(sampleTimestamp)   2 bytes (date)
 *   writeTime(sampleTimestamp)   2 bytes (time)
 *   writeDate(now)               2 bytes (date)
 *   writeTime(now)               2 bytes (time)
 *   writeInt16(glucoseInMgDl)    2 bytes (little-endian)
 *   0x00 0x00 0x00               3 bytes padding
 *   0x55                         1 byte trailer
 *
 * TIMEZONE CAVEAT: iOS toDateArray/toTimeArray use Calendar.current (LOCAL device time).
 * EversenseE3Writer.writeDate/writeTime here use GMT. The bit-packing is byte-identical,
 * but the timezone differs. If E3 testing shows the calibration lands at the wrong time
 * (off by the local UTC offset), the fix is to encode these timestamps in LOCAL time
 * instead of GMT.
 *
 * !!! UNVERIFIED: ported from the iOS reference, NOT tested on E3 hardware. Must be
 * validated by an E3 user (confirm a calibration actually registers and the next/last
 * calibration dates update correctly) before being trusted. !!!
 *
 * @param glucoseInMgDl Blood glucose value in mg/dL
 * @param sampleTimestamp Epoch milliseconds of the blood glucose sample
 */
@EversensePacket(
    requestId = EversenseE3Packets.SendBloodGlucoseDataWithTwoTimestampsCommandId,
    responseId = EversenseE3Packets.SendBloodGlucoseDataWithTwoTimestampsResponseId,
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
        return EversenseE3Writer.writeDate(sampleTimestamp) +
            EversenseE3Writer.writeTime(sampleTimestamp) +
            EversenseE3Writer.writeDate(now) +
            EversenseE3Writer.writeTime(now) +
            EversenseE3Writer.writeInt16(glucoseInMgDl) +
            byteArrayOf(0x00, 0x00, 0x00) +
            byteArrayOf(0x55)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}