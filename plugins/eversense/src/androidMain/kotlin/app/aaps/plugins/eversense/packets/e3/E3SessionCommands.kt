package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.enums.EversenseE3Memory
import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.enums.EversenseTrendArrow
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.e3.util.EversenseE3Parser
import app.aaps.plugins.eversense.packets.e3.util.EversenseE3Writer
import app.aaps.plugins.eversense.protocol.GlucoseDecoders
import app.aaps.plugins.eversense.protocol.PacketSpec
import app.aaps.plugins.eversense.protocol.PacketTime
import app.aaps.plugins.eversense.util.EversenseLogger
import app.aaps.plugins.eversense.util.MessageCoder
import java.util.TimeZone
import kotlin.math.abs

class EnterDiagnosticModePacket : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.EnterDiagnosticModeCommandId, EversenseE3Packets.EnterDiagnosticModeResponseId)) {

    override fun getRequestData(): ByteArray = ByteArray(0)

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}

class ExitDiagnosticModePacket : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.ExitDiagnosticModeCommandId, EversenseE3Packets.ExitDiagnosticModeResponseId)) {

    override fun getRequestData(): ByteArray = ByteArray(0)

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}

class GetCurrentDatetimePacket : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.ReadCurrentTransmitterDateAndTimeCommandId, EversenseE3Packets.ReadCurrentTransmitterDateAndTimeResponseId)) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        val start = getStartIndex()
        val date = EversenseE3Parser.readDate(receivedData, start)
        val time = EversenseE3Parser.readTime(receivedData, start + 2)
        val timeZoneOffset = EversenseE3Parser.readTimezone(receivedData, start + 4)
        var needsTimeSync = false
        val actualTimeZoneOffset = TimeZone.getDefault().getOffset(PacketTime.nowMillis()).toLong()
        // Allow time drift <10s
        if (abs(PacketTime.nowMillis() - (date + time)) > 10_000) {
            EversenseLogger.warning("GetCurrentDatetimePacket", "time drift detected... drift: ${abs(PacketTime.nowMillis() - (date + time))} ms")
            needsTimeSync = true
        } else if (actualTimeZoneOffset != timeZoneOffset) {
            EversenseLogger.warning("GetCurrentDatetimePacket", "timezone mismatch - received: $timeZoneOffset, actual: $actualTimeZoneOffset")
            needsTimeSync = true
        }
        return Response(date + time, timeZoneOffset, needsTimeSync)
    }

    data class Response(val datetime: Long, val timezoneOffset: Long, val needsTimeSync: Boolean): EversenseBasePacket.Response()
}

class GetCurrentGlucosePacket : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.ReadSensorGlucoseCommandId, EversenseE3Packets.ReadSensorGlucoseResponseId)) {

    override fun parseResponse(): Response? = GlucoseDecoders.e3(receivedData)?.let {
        Response(it.timestamp, it.glucose, it.trend, it.raw)
    }

    data class Response(val datetime: Long, val glucoseInMgDl: Int, val trend: EversenseTrendArrow, val rawResponseHex: String = "") : EversenseBasePacket.Response()
}

class GetGlucoseAlertsAndStatusPacket : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.ReadSensorGlucoseAlertsAndStatusCommandId, EversenseE3Packets.ReadSensorGlucoseAlertsAndStatusResponseId)) {
    private val STATUS_FLAG_COUNT = 13

    override fun getRequestData(): ByteArray = ByteArray(0)

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        val s = getStartIndex()
        val rawContent = receivedData.drop(s + 1).dropLast(2).map { it.toInt() and 0xFF }
        val content = IntArray(STATUS_FLAG_COUNT)
        val offset = STATUS_FLAG_COUNT - rawContent.size.coerceAtMost(STATUS_FLAG_COUNT)
        rawContent.take(STATUS_FLAG_COUNT).forEachIndexed { i, v -> content[offset + i] = v }
        if (content.all { it == 0 }) return Response(alarms = emptyList())
        val alarms = mutableListOf<ActiveAlarm>()
        fun add(alarm: app.aaps.plugins.eversense.enums.EversenseAlarm?) {
            alarm?.let { alarms.add(ActiveAlarm(code = it, codeRaw = it.code, flag = 0, priority = 0)) }
        }
        add(MessageCoder.messageCodeForGlucoseLevelAlarmFlags(content[0]))
        add(MessageCoder.messageCodeForGlucoseLevelAlertFlags(content[1]))
        add(MessageCoder.messageCodeForRateAlertFlags(content[2]))
        add(MessageCoder.messageCodeForPredictiveAlertFlags(content[3]))
        add(MessageCoder.messageCodeForSensorHardwareAndAlertFlags(content[4]))
        add(MessageCoder.messageCodeForSensorReadAlertFlags(content[5]))
        add(MessageCoder.messageCodeForSensorReplacementFlags(content[6]))
        add(MessageCoder.messageCodeForSensorCalibrationFlags(content[7]))
        add(MessageCoder.messageCodeForTransmitterStatusAlertFlags(content[8]))
        add(MessageCoder.messageCodeForTransmitterBatteryAlertFlags(content[9]))
        add(MessageCoder.messageCodeForTransmitterEOLAlertFlags(content[10]))
        add(MessageCoder.messageCodeForSensorReplacementFlags2(content[11]))
        add(MessageCoder.messageCodeForCalibrationSwitchFlags(content[12]))
        return Response(alarms = alarms)
    }

    data class Response(val alarms: List<ActiveAlarm>) : EversenseBasePacket.Response()
}

class GetHighGlucoseRepeatIntervalPacket : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray = EversenseE3Memory.HighGlucoseAlarmRepeatIntervalDay.getRequestData()

    override fun parseResponse(): Response? {
        if (receivedData.size < getStartIndex() + 1) return null
        return Response(intervalMinutes = receivedData[getStartIndex()].toInt() and 0xFF)
    }

    data class Response(val intervalMinutes: Int) : EversenseBasePacket.Response()
}

class GetLowGlucoseRepeatIntervalPacket : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray = EversenseE3Memory.LowGlucoseAlarmRepeatIntervalDay.getRequestData()

    override fun parseResponse(): Response? {
        if (receivedData.size < getStartIndex() + 1) return null
        return Response(intervalMinutes = receivedData[getStartIndex()].toInt() and 0xFF)
    }

    data class Response(val intervalMinutes: Int) : EversenseBasePacket.Response()
}

class GetMmaFeaturesPacket : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray = EversenseE3Memory.MmaFeatures.getRequestData()

    override fun parseResponse(): Response? {
        if (receivedData.size < getStartIndex() + 1) return null
        return Response(value = receivedData[getStartIndex()].toInt() and 0xFF)
    }

    data class Response(val value: Int) : EversenseBasePacket.Response()
}

class GetSignalStrengthRawPacket : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.ReadTwoByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadTwoByteSerialFlashRegisterResponseId)) {
    companion object {
        // Raw thresholds matching iOS EversenseKit SignalStrength.swift rawThreshold values
        const val THRESHOLD_EXCELLENT = 1600
        const val THRESHOLD_GOOD = 1300
        const val THRESHOLD_LOW = 800
        const val THRESHOLD_VERY_LOW = 500
        const val THRESHOLD_POOR = 350
    }

    override fun getRequestData(): ByteArray = EversenseE3Memory.SensorFieldCurrentRaw.getRequestData()

    override fun parseResponse(): Response? {
        val start = getStartIndex()
        if (receivedData.size < start + 2) return null
        // Little-endian UInt16 — matches iOS: UInt16(data[start]) | (UInt16(data[start + 1]) << 8)
        val raw = (receivedData[start].toInt() and 0xFF) or
                  ((receivedData[start + 1].toInt() and 0xFF) shl 8)
        // Classify the RAW flash value using iOS EversenseKit SignalStrength.threshold
        // values (903/705/494/395/350), then map to a 0-100 percentage that the
        // placement activity bar thresholds (75/48/30/28/25) bucket correctly.
        // The previous code used raw/20, which systematically under-reported signal
        // (a good placement at raw>=903 displayed as "Fair" instead of "Excellent").
        val signalPercent = when {
            raw >= 903 -> 80   // Excellent -> 5 bars
            raw >= 705 -> 55   // Good      -> 4 bars
            raw >= 494 -> 35   // Low       -> 3 bars
            raw >= 395 -> 28   // Very low  -> 2 bars
            raw >= 350 -> 25   // Poor      -> 1 bar
            else       -> 0    // No signal -> 0 bars
        }
        return Response(rawValue = raw, signalStrength = signalPercent)
    }

    data class Response(
        val rawValue: Int,
        val signalStrength: Int  // 0-100 scaled, matching iOS implementation
    ) : EversenseBasePacket.Response()
}

class GetVersionExtendedPacket : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.ReadFourByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadFourByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray = EversenseE3Memory.TransmitterSoftwareVersionExt.getRequestData()

    override fun parseResponse(): Response? {
        val start = getStartIndex()
        if (receivedData.size < start + 4) return null
        val extVersion = (start until start + 4).map { receivedData[it].toInt().toChar() }.joinToString("")
        return Response(extVersion = extVersion.trim())
    }

    data class Response(val extVersion: String) : EversenseBasePacket.Response()
}

class GetVersionPacket : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.ReadFourByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadFourByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray = EversenseE3Memory.TransmitterSoftwareVersion.getRequestData()

    override fun parseResponse(): Response? {
        val start = getStartIndex()
        if (receivedData.size < start + 4) return null
        val version = (start until start + 4).map { receivedData[it].toInt().toChar() }.joinToString("")
        return Response(version = version.trim())
    }

    data class Response(val version: String) : EversenseBasePacket.Response()
}

class PingPacket : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.PingCommandId, EversenseE3Packets.PingResponseId)) {

    override fun getRequestData(): ByteArray = ByteArray(0)

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}

class SaveBondingInformationPacket : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.SaveBLEBondingInformationCommandId, EversenseE3Packets.SaveBLEBondingInformationResponseId)) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return SaveBondingInformationResponse()
    }

    class SaveBondingInformationResponse() : Response()
}

class SetAppVersionE3Packet(private val appVersion: String = "8.0.4") : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.WriteFourByteSerialFlashRegisterCommandId, EversenseE3Packets.WriteFourByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray {
        val parts = appVersion.split(".")
        val i0 = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val i1 = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val i2 = parts.getOrNull(2)?.toIntOrNull() ?: 0
        val addr = EversenseE3Memory.AppVersion.getRequestData()
        return byteArrayOf(addr[0], addr[1], addr[2],
            (i2 and 0xFF).toByte(),
            ((i2 and 0xFF00) shr 8).toByte(),
            (i1 and 0xFF).toByte(),
            (i0 and 0xFF).toByte()
        )
    }

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

class SetBleDisconnectPacket(private val intervalSeconds: Int = 300) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.WriteTwoByteSerialFlashRegisterCommandId, EversenseE3Packets.WriteTwoByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray {
        val addr = EversenseE3Memory.BleDisconnect.getRequestData()
        return byteArrayOf(addr[0], addr[1], addr[2],
            (intervalSeconds and 0xFF).toByte(),
            ((intervalSeconds shr 8) and 0xFF).toByte()
        )
    }

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

class SetCurrentDatetimePacket : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.SetCurrentTransmitterDateAndTimeCommandId, EversenseE3Packets.SetCurrentTransmitterDateAndTimeResponseId)) {

    override fun getRequestData(): ByteArray {
        val now = PacketTime.nowMillis()
        return EversenseE3Writer.writeDate(now) +
            EversenseE3Writer.writeTime(now) +
            EversenseE3Writer.writeTimezone(now)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response()
    }

    class Response : EversenseBasePacket.Response() {}
}

class SetHighGlucoseRepeatIntervalDayPacket(private val intervalMinutes: Int) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.WriteSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.WriteSingleByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.HighGlucoseAlarmRepeatIntervalDay.getRequestData() +
            byteArrayOf(intervalMinutes.toByte())
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}

class SetHighGlucoseRepeatIntervalNightPacket(private val intervalMinutes: Int) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.WriteSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.WriteSingleByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.HighGlucoseAlarmRepeatIntervalNight.getRequestData() +
            byteArrayOf(intervalMinutes.toByte())
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}

class SetLowGlucoseRepeatIntervalDayPacket(private val intervalMinutes: Int) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.WriteSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.WriteSingleByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.LowGlucoseAlarmRepeatIntervalDay.getRequestData() +
            byteArrayOf(intervalMinutes.toByte())
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}

class SetLowGlucoseRepeatIntervalNightPacket(private val intervalMinutes: Int) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.WriteSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.WriteSingleByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.LowGlucoseAlarmRepeatIntervalNight.getRequestData() +
            byteArrayOf(intervalMinutes.toByte())
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}
