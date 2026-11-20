package app.aaps.plugins.eversense.packets.e365

import app.aaps.plugins.eversense.enums.CalibrationFlag
import app.aaps.plugins.eversense.enums.CalibrationMode
import app.aaps.plugins.eversense.enums.CalibrationPhase
import app.aaps.plugins.eversense.enums.CalibrationReadiness
import app.aaps.plugins.eversense.enums.EversenseAlarm
import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.enums.EversenseTrendArrow
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.models.GlucoseHistoryItem
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.e365.utils.toInt
import app.aaps.plugins.eversense.packets.e365.utils.toShort
import app.aaps.plugins.eversense.packets.e365.utils.toUnix
import app.aaps.plugins.eversense.packets.e365.utils.toUtfString
import app.aaps.plugins.eversense.protocol.GlucoseDecoders
import app.aaps.plugins.eversense.protocol.PacketSpec
import app.aaps.plugins.eversense.util.EversenseLogger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.serialization.Serializable

class GetActiveAlarmsPacket : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.ReadCommandId, Eversense365Packets.ReadResponseId, Eversense365Packets.ReadActiveAlerts)) {

    override fun getRequestData(): ByteArray = byteArrayOf()
    // 42 22 -> CmdType & CmdId
    // 03 -> Active alarm count
    // [code, flag, priority] * count -> 3 bytes per alarm

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        val count = receivedData[2].toInt() and 0xFF
        val alarms = mutableListOf<ActiveAlarm>()
        for (i in 0 until count) {
            val offsetStart = i * 3 + 3
            if (receivedData.size < offsetStart + 3) {
                EversenseLogger.warning("GetActiveAlarmsPacket", "Missing data for alarm $i")
                break
            }
            alarms.add(ActiveAlarm(
                code = EversenseAlarm.from(receivedData[offsetStart].toInt() and 0xFF),
                codeRaw = receivedData[offsetStart].toInt() and 0xFF,
                flag = receivedData[offsetStart + 1].toInt() and 0xFF,
                priority = receivedData[offsetStart + 2].toInt() and 0xFF
            ))
        }
        alarms.sortBy { it.priority }
        EversenseLogger.debug("GetActiveAlarmsPacket") { "Active alarms: ${alarms.map { it.code.title }}" }
        return Response(count = count, alarms = alarms)
    }

    data class Response(
        val count: Int,
        val alarms: List<ActiveAlarm>
    ) : EversenseBasePacket.Response()
}

data class AlertHistoryItem(
    val datetime: Long,
    val code: EversenseAlarm
)

class GetAlertsLogValuesPacket(
    private val from: Int,
    private val to: Int
) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.ReadCommandId, Eversense365Packets.ReadLogsId, Eversense365Packets.ReadLogValue)) {

    override fun getRequestData(): ByteArray {
        return byteArrayOf(
            Eversense365Packets.LogTypeAlerts,
            (from and 0xFF).toByte(), ((from shr 8) and 0xFF).toByte(),
            ((from shr 16) and 0xFF).toByte(), ((from shr 24) and 0xFF).toByte(),
            (to and 0xFF).toByte(), ((to shr 8) and 0xFF).toByte(),
            ((to shr 16) and 0xFF).toByte(), ((to shr 24) and 0xFF).toByte()
        )
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        if (receivedData[6].toInt() != Eversense365Packets.LogTypeAlerts.toInt()) {
            EversenseLogger.error("GetAlertsLogValuesPacket", "Invalid log type: ${receivedData[6]}")
            return Response(count = 0, alertHistory = emptyList())
        }
        val actualData = receivedData.drop(7).toUByteArray()
        val recordLength = 60
        val history = mutableListOf<AlertHistoryItem>()
        var i = 0
        while (i + recordLength <= actualData.size) {
            val chunk = actualData.copyOfRange(i, i + recordLength)
            val datetime = chunk.copyOfRange(4, 12).toUnix()
            val alarmCode = chunk[12].toInt() and 0xFF
            history.add(AlertHistoryItem(datetime = datetime, code = EversenseAlarm.from(alarmCode)))
            i += recordLength
        }
        return Response(count = history.size, alertHistory = history)
    }

    data class Response(
        val count: Int,
        val alertHistory: List<AlertHistoryItem>
    ) : EversenseBasePacket.Response()
}

class GetCalibrationInfoPacket : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.ReadCommandId, Eversense365Packets.ReadResponseId, Eversense365Packets.ReadCalibrationInfo)) {
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
        EversenseLogger.debug("GetCalibrationInfoPacket") { "Raw calibration readiness byte: $rawReadiness (0x${rawReadiness.toString(16)})" }
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

@Serializable

data class CalibrationHistoryItem(
    val datetime: Long,
    val glucoseInMgDl: Int,
    val flag: CalibrationFlag
)

class GetCalibrationLogValuesPacket(
    private val from: Int,
    private val to: Int
) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.ReadCommandId, Eversense365Packets.ReadLogsId, Eversense365Packets.ReadLogValue)) {

    override fun getRequestData(): ByteArray {
        return byteArrayOf(
            Eversense365Packets.LogTypeCalibrations,
            (from and 0xFF).toByte(), ((from shr 8) and 0xFF).toByte(),
            ((from shr 16) and 0xFF).toByte(), ((from shr 24) and 0xFF).toByte(),
            (to and 0xFF).toByte(), ((to shr 8) and 0xFF).toByte(),
            ((to shr 16) and 0xFF).toByte(), ((to shr 24) and 0xFF).toByte()
        )
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        if (receivedData[6].toInt() != Eversense365Packets.LogTypeCalibrations.toInt()) {
            EversenseLogger.error("GetCalibrationLogValuesPacket", "Invalid log type: ${receivedData[6]}")
            return Response(count = 0, calibrationHistory = emptyList())
        }
        val actualData = receivedData.drop(7).toUByteArray()
        val recordLength = 56
        // Offset: recordLength(4) + datetime(8) + FsStartEndFlag(2) + ProcessingDatetime(8)
        //         + SampleDatetime(8) + MmaFSDatetime(8) + DecisionDatetime(8) = 46
        val offsetGlucose = 4 + 8 + 2 + 8 + 8 + 8 + 8
        val offsetFlag = offsetGlucose + 2
        val history = mutableListOf<CalibrationHistoryItem>()
        var i = 0
        while (i + recordLength <= actualData.size) {
            val chunk = actualData.copyOfRange(i, i + recordLength)
            val datetime = chunk.copyOfRange(4, 12).toUnix()
            val glucose = (chunk[offsetGlucose].toInt() and 0xFF) or ((chunk[offsetGlucose + 1].toInt() and 0xFF) shl 8)
            val flag = CalibrationFlag.from(chunk[offsetFlag].toInt() and 0xFF)
            history.add(CalibrationHistoryItem(datetime = datetime, glucoseInMgDl = glucose, flag = flag))
            i += recordLength
        }
        return Response(count = history.size, calibrationHistory = history)
    }

    data class Response(
        val count: Int,
        val calibrationHistory: List<CalibrationHistoryItem>
    ) : EversenseBasePacket.Response()
}

class GetGlucoseDataPacket(private val sensorIdLen: Int) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.ReadCommandId, Eversense365Packets.ReadResponseId, Eversense365Packets.ReadGlucoseData)) {

    override fun parseResponse(): Response? = GlucoseDecoders.v2(receivedData, sensorIdLen)?.let {
        Response(it.timestamp, it.glucose, it.trend, it.signal, it.sensorId, it.raw)
    }

    data class Response(val datetime: Long, val glucoseInMgDl: Int, val trend: EversenseTrendArrow,
                        val signalStrength: Int, val sensorId: String = "", val rawResponseHex: String = "") : EversenseBasePacket.Response()
}

class GetGlucoseLogValuesPacket(
    private val from: Int,
    private val to: Int,
    private val sensorIdLength: Int = 10
) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.ReadCommandId, 0x62.toByte(), Eversense365Packets.ReadLogValue)) {

    override fun getRequestData(): ByteArray {
        val logType: Byte = 13 // LogTypes.Glucose
        return byteArrayOf(
            logType,
            (from and 0xFF).toByte(), ((from shr 8) and 0xFF).toByte(),
            ((from shr 16) and 0xFF).toByte(), ((from shr 24) and 0xFF).toByte(),
            (to and 0xFF).toByte(), ((to shr 8) and 0xFF).toByte(),
            ((to shr 16) and 0xFF).toByte(), ((to shr 24) and 0xFF).toByte()
        )
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        if (receivedData[6].toInt() != 13) {
            EversenseLogger.error("GetGlucoseLogValuesPacket", "Invalid log type: ${receivedData[6]}")
            return Response(count = 0, glucoseHistory = emptyList())
        }
        val actualData = receivedData.drop(7).toUByteArray()
        val recordLength = 193
        val offsetGlucose = sensorIdLength + 8 + 4
        val offsetTrend = offsetGlucose + 2 + 4
        val history = mutableListOf<GlucoseHistoryItem>()
        var i = 0
        while (i + recordLength <= actualData.size) {
            val chunk = actualData.copyOfRange(i, i + recordLength)
            val datetime = chunk.copyOfRange(4, 12).toUnix()
            val glucose = (chunk[offsetGlucose].toInt() and 0xFF) or
                ((chunk[offsetGlucose + 1].toInt() and 0xFF) shl 8)
            val trend = getTrend(chunk[offsetTrend].toInt() and 0xFF)
            i += recordLength
            // Safety ceiling matches EversenseKit's (loopandlearn/bastiaanv, the reference iOS
            // implementation this port is based on), tightened from 1000 to 450 mg/dl.
            if (glucose >= 450) {
                EversenseLogger.warning("GetGlucoseLogValuesPacket", "Glucose exceeds limits: $glucose — skipping")
                continue
            }
            val rawHex = chunk.joinToString("") { "%02X".format(it.toInt()) }
            history.add(GlucoseHistoryItem(valueInMgDl = glucose, datetime = datetime, trend = trend, rawResponseHex = rawHex))
        }
        EversenseLogger.debug("GetGlucoseLogValuesPacket") { "History records: ${history.size}" }
        return Response(count = history.size, glucoseHistory = history)
    }

    private fun getTrend(value: Int): EversenseTrendArrow =
        GlucoseDecoders.trend(value, extended = true, unknown = EversenseTrendArrow.FLAT)

    data class Response(
        val count: Int,
        val glucoseHistory: List<GlucoseHistoryItem>
    ) : EversenseBasePacket.Response()
}

enum class LogType(val value: Byte) {
    ALERTS(0),
    CALIBRATIONS(6),
    GLUCOSE(13)
}

class GetLogRangePacket365(private val logType: LogType) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.ReadCommandId, Eversense365Packets.ReadResponseId, Eversense365Packets.ReadLogRange)) {

    override fun getRequestData(): ByteArray = byteArrayOf(logType.value)
    // 42 38 -> CmdType & CmdId
    // XX -> log type
    // 00 00 00 00 -> rangeFrom (UInt32 little-endian)
    // 00 00 00 00 -> rangeTo (UInt32 little-endian)

    override fun parseResponse(): Response? {
        if (receivedData.size < 11) return null
        val rangeFrom = ((receivedData[3].toLong() and 0xFF) or
            ((receivedData[4].toLong() and 0xFF) shl 8) or
            ((receivedData[5].toLong() and 0xFF) shl 16) or
            ((receivedData[6].toLong() and 0xFF) shl 24)).toInt()
        val rangeTo = ((receivedData[7].toLong() and 0xFF) or
            ((receivedData[8].toLong() and 0xFF) shl 8) or
            ((receivedData[9].toLong() and 0xFF) shl 16) or
            ((receivedData[10].toLong() and 0xFF) shl 24)).toInt()
        app.aaps.plugins.eversense.util.EversenseLogger.debug("GetLogRangePacket365") { "Log range: $rangeFrom - $rangeTo" }
        return Response(rangeFrom = rangeFrom, rangeTo = rangeTo)
    }

    data class Response(val rangeFrom: Int, val rangeTo: Int) : EversenseBasePacket.Response()
}

class GetPatientSettingsPacket : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.ReadCommandId, Eversense365Packets.ReadResponseId, Eversense365Packets.ReadPatientInformation)) {

    override fun parseResponse(): Response? {
        if (receivedData.size < 65) {
            return null
        }
        // Message parsed:
        // 42 21 -> CmdType & CmdId
        // 44 33 30 36 33 36 36 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 -> Transmitter name
        // 38 2E 30 2E 34 00 00 00 00 00 00 00 00 00 00 00 -> Recent MMA Version
        // 00 -> Is clinical mode enabled
        // 00 -> Is do not Disturb Enabled
        // 2C 01 -> BLE connect time in sec -> 300s
        // 46 00 -> Low sugar target in mg/dl
        // B4 00 -> High sugar target in mg/dl
        // 00 -> Alarm rate falling enabled
        // 19 -> Alarm rate falling threshold
        // 00 -> Alarm rate rising enabled
        // 19 -> Alarm rate rising threshold
        // 00 -> Alarm Predictive Low enabled
        // 14 -> Alarm Predictive Low Time
        // 00 -> Alarm Predictive High enabled
        // 14 -> Alarm Predictive High Time
        // 01 -> Alarm High Glucose enabled
        // FA 00 -> Alarm High Glucose Threshold
        // 1E -> Alarm High Glucose Repeat Interval
        // 41 00 -> Alarm Low Glucose Threshold
        // 0F -> Alarm Low Glucose Repeat Interval
        // 34 -> Battery Temp Thresh Mode Change
        // 44 -> Battery Temp Thresh Warn
        return Response(
            vibrateMode = receivedData[44].toInt() != 0x00,
            highGlucoseEnabled = receivedData[59].toInt() != 0x00,
            highGlucoseAlarmInMgDl = receivedData.copyOfRange(60, 62).toShort().toInt(),
            lowGlucoseAlarmInMgDl = receivedData.copyOfRange(63, 65).toShort().toInt(),
            predictionLowEnabled = receivedData[55].toInt() != 0x00,
            predictionHighEnabled = receivedData[57].toInt() != 0x00,
            predictionFallingInterval = receivedData[56].toInt(),
            predictionRisingInterval = receivedData[58].toInt(),
            predictionFallingThreshold = receivedData.copyOfRange(47, 49).toShort().toInt(),
            predictionRisingThreshold = receivedData.copyOfRange(49, 51).toShort().toInt(),
            rateFallingEnabled = receivedData[51].toInt() != 0x00,
            rateRisingEnabled = receivedData[53].toInt() != 0x00,
            rateFallingThreshold = receivedData[52].toDouble() / 10,
            rateRisingThreshold = receivedData[54].toDouble() / 10,
        )
    }

    data class Response(
        val vibrateMode: Boolean,
        val highGlucoseEnabled: Boolean,
        val lowGlucoseAlarmInMgDl: Int,
        val highGlucoseAlarmInMgDl: Int,
        val predictionLowEnabled: Boolean,
        val predictionHighEnabled: Boolean,
        val predictionFallingInterval: Int,
        val predictionRisingInterval: Int,
        val predictionFallingThreshold: Int,
        val predictionRisingThreshold: Int,
        val rateFallingEnabled: Boolean,
        val rateRisingEnabled: Boolean,
        val rateFallingThreshold: Double,
        val rateRisingThreshold: Double
    ) : EversenseBasePacket.Response()
}

class GetSensorInformationPacket : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.ReadCommandId, Eversense365Packets.ReadResponseId, Eversense365Packets.ReadSensorInformation)) {
// 42 20
    // 33 30 36 33 36 36 00 00 00 00 00 00 00 00 00 00
    // 44 33 30 36 33 36 36 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
    // 26 9f 14 b7 c4 00 00 00
    // 29 6d 23 06
    // 30352e30302e30312e30374d000030312e30360030312e30300030312e30300030312e30300000000111008c012c01010a7900000000000076000083d959f3c30000006d01790000000000007600005730352e30302e30312e30374d2d303600000000000000000030312e30302e30312e30320000000000
    // Message parsed:
    // 42 20 -> CmdType & CmdId
    // 33 30 36 33 36 36 00 00 00 00 00 00 00 00 00 00 -> Serial number
    // 44 33 30 36 33 36 36 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 -> Transmitter name
    // 1A 44 08 CB BF 00 00 00 -> Current datetime
    // 29 6D 23 06 -> Transmitter model
    // 30 35 2E 30 30 2E 30 31 2E 30 37 4D 00 00 -> Current firmware version
    // 30 31 2E 30 36 00 -> Comm version
    // 30 31 2E 30 30 00 -> Register map version
    // 30 31 2E 30 30 00 -> Log map version
    // 30 31 2E 30 30 00 -> Push map version
    // 00 -> Glucose algorithm version major
    // 00 -> Glucose algorithm version minor
    // 01 -> MMA functionality
    // 11 00 -> Transmitter mode
    // 8C 01 -> Transmitter life remaining
    // 2C 01 -> Sensor sample interval
    // 01 -> Sensor type
    // 0A -> Sensor ID length
    // 00 00 00 00 00 00 00 00 00 00 -> Sensor ID (size is based on previous value)
    // 00 00 00 00 00 00 00 00 -> Sensor insertion date
    // 00 00 -> Sensor life remaining
    // 00 00 00 00 00 00 00 00 00 00 -> Detected sensor ID (length is based on Sensor ID length)
    // 62 -> Battery percentage
    // 30 35 2E 30 30 2E 30 31 2E 30 37 4D 2D 30 36 00 -> Firmware version
    // 00 00 00 00 00 00 00 00 -> Operation start datetime
    // 30 31 2E 30 30 2E 30 31 2E 30 32 00 00 00 00 00 -> Other firmware version

    override fun parseResponse(): Response? {
        if (receivedData.size < 104) {
            return null
        }
        val sensorIdLength = receivedData[103].toInt()
        val sensorIdDoubleLength = 2 * sensorIdLength
        if (receivedData.size < 139 + sensorIdDoubleLength) {
            return null
        }
        return Response(
            serialNumber = receivedData.copyOfRange(2, 18).toUtfString(),
            transmitterName = receivedData.copyOfRange(18, 43).toUtfString(),
            transmitterDatetime = receivedData.copyOfRange(43, 51).toUnix(),
            insertionDate = receivedData.copyOfRange(104+sensorIdLength, 112+sensorIdLength).toUnix(),
            mmaFeatures = receivedData[95].toInt(),
            batteryLevel = receivedData[114+sensorIdDoubleLength].toInt(),
            version = receivedData.copyOfRange(115+sensorIdDoubleLength, 131+sensorIdDoubleLength).toUtfString(),
            extVersion = receivedData.copyOfRange(131+sensorIdDoubleLength, 139+sensorIdDoubleLength).toUtfString(),
            sensorIdLength = sensorIdLength,
            communicationProtocolVersion = receivedData.copyOfRange(69, 75).toUtfString().toDouble()
        )
    }

    data class Response(
        val serialNumber: String,
        val transmitterName: String,
        val transmitterDatetime: Long,
        val insertionDate: Long,
        val mmaFeatures: Int,
        val batteryLevel: Int,
        val version: String,
        val extVersion: String,
        val sensorIdLength: Int,
        val communicationProtocolVersion: Double
    ) : EversenseBasePacket.Response()
}

class GetSignalStrengthPacket : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.ReadCommandId, Eversense365Packets.ReadResponseId, Eversense365Packets.ReadSignalStrength)) {

    override fun getRequestData(): ByteArray = byteArrayOf()
    // Parsed message:
    // 42 1B -> CmdType & CmdId
    // 01 -> SensorType
    // 0A -> Sensor ID length
    // 00 00 00 00 00 00 00 00 00 00 -> Sensor ID (length = byte[3])
    // XX XX XX XX XX XX XX XX -> Timestamp
    // XX XX -> Signal Strength (raw)
    // XX XX XX XX -> Signal Coupling (little-endian float, multiply by 100 for percentage)
    // XX -> Placement

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        app.aaps.plugins.eversense.util.EversenseLogger.debug("GetSignalStrengthPacket") {
            "Raw response: " + receivedData.joinToString(" ") { it.toString(16).padStart(2, '0') }
        }
        // Byte [3] = sensor ID length
        val sensorIdLength = receivedData[3].toInt() and 0xFF
        // Signal coupling float starts at byte 14 + sensorIdLength
        val couplingStart = 14 + sensorIdLength
        if (receivedData.size < couplingStart + 4) {
            app.aaps.plugins.eversense.util.EversenseLogger.warning("GetSignalStrengthPacket", "Response too short: ${receivedData.size} bytes, need ${couplingStart + 4}")
            return Response(signalStrength = 0)
        }
        // Read little-endian float and multiply by 100 for percentage
        val floatBytes = byteArrayOf(
            receivedData[couplingStart].toByte(),
            receivedData[couplingStart + 1].toByte(),
            receivedData[couplingStart + 2].toByte(),
            receivedData[couplingStart + 3].toByte()
        )
        val signalFloat = ByteBuffer.wrap(floatBytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .float
        val signalPercent = (signalFloat * 100).toInt().coerceIn(0, 100)
        app.aaps.plugins.eversense.util.EversenseLogger.debug("GetSignalStrengthPacket") { "sensorIdLength: $sensorIdLength, signalFloat: $signalFloat -> $signalPercent%" }
        return Response(signalStrength = signalPercent)
    }

    data class Response(
        val signalStrength: Int  // 0-100, transmitter-to-sensor placement signal
    ) : EversenseBasePacket.Response()
}
