package app.aaps.plugins.eversense.packets.e365

import app.aaps.plugins.eversense.enums.CalibrationMode
import app.aaps.plugins.eversense.enums.CalibrationPhase
import app.aaps.plugins.eversense.enums.CalibrationReadiness
import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket
import app.aaps.plugins.eversense.packets.e365.utils.toUnix
import app.aaps.plugins.eversense.util.EversenseLogger

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
        val calPerDay = receivedData[12].toInt()
        val rawReadiness = receivedData[3].toInt() and 0xFF
        EversenseLogger.info("GetCalibrationInfoPacket", "Raw calibration readiness byte: $rawReadiness (0x${rawReadiness.toString(16)})")
        return Response(
            currentPhase = CalibrationPhase.from365(receivedData[2].toInt(), calPerDay),
            calibrationReadiness = CalibrationReadiness.from365(rawReadiness),
            calibrationMode = CalibrationMode.from365(calPerDay),
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