package com.nightscout.eversense.packets.e365

import com.nightscout.eversense.enums.CalibrationMode
import com.nightscout.eversense.enums.CalibrationPhase
import com.nightscout.eversense.enums.CalibrationReadiness
import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.packets.e365.utils.toUnix

@EversensePacket(
    requestId = Eversense365Packets.ReadCommandId,
    responseId = Eversense365Packets.ReadResponseId,
    typeId = Eversense365Packets.ReadCalibrationInfo,
    securityType = EversenseSecurityType.SecureV2
)
class GetCalibrationInfoPacket : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        return byteArrayOf()
    }

    // Parsed message:
    // 42 1D -> CmdType & CmdId
    // 00 -> Current calibration phase
    // 06 -> Ready for calibration (CALIBRATION_READINESS)
    // 00 00 00 00 00 00 00 00 -> Next calibration datetime
    // 00 -> Number of calibrations per day
    // 00 -> Number of calibrations in this Phase
    // 00 00 -> Minutes allowed before next calibration due
    // 00 00 -> Minutes allowed after next calibration due
    // 00 00 -> Number of completed calibrations
    // 00 00 00 00 00 00 00 00 -> Start datetime of current phase
    // 00 00 -> Sensor lifetime
    // 00 00 -> Warmup duration
    // 00 00 -> Minutes until next calibration
    // 00 00 00 00 00 00 00 00 -> Last calibration datetime
    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }

        return Response(
            currentPhase = CalibrationPhase.from365(receivedData[2].toInt()),
            calibrationReadiness = CalibrationReadiness.from(receivedData[3].toInt()),
            calibrationMode = CalibrationMode.from365(receivedData[12].toInt()),
            nextCalibration = receivedData.copyOfRange(4, 12).toUnix(),
            lastCalibration = receivedData.copyOfRange(34, 42).toUnix(),
        )
    }

    data class Response(
        val currentPhase: CalibrationPhase,
        val calibrationReadiness: CalibrationReadiness,
        val calibrationMode: CalibrationMode,
        val nextCalibration: Long,
        val lastCalibration: Long
    ) : EversenseBasePacket.Response()
}