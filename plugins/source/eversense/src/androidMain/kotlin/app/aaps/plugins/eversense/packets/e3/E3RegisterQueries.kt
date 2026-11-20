package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.enums.CalibrationPhase
import app.aaps.plugins.eversense.enums.CalibrationReadiness
import app.aaps.plugins.eversense.enums.EversenseE3Memory
import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.e3.util.EversenseE3Parser
import app.aaps.plugins.eversense.protocol.PacketSpec

class GetBatteryPercentagePacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId), EversenseE3Memory.BatteryPercentage) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        // The E3 battery register returns an enum index (0-11). Clamp to valid range.
        val raw = receivedData[getStartIndex()].toInt() and 0xFF
        val percentage = raw.coerceIn(0, 11)
        return Response(percentage = percentage)
    }

    data class Response(val percentage: Int) : EversenseBasePacket.Response()
}

class GetBleDisconnectPacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadTwoByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadTwoByteSerialFlashRegisterResponseId), EversenseE3Memory.BleDisconnect) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        val s = getStartIndex()
        val intervalSeconds = (receivedData[s].toInt() and 0xFF) or ((receivedData[s + 1].toInt() and 0xFF) shl 8)
        return Response(intervalSeconds = intervalSeconds)
    }

    data class Response(val intervalSeconds: Int) : EversenseBasePacket.Response()
}

class GetCalibrationDailyPacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId), EversenseE3Memory.IsOneCalibration) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        // Official app: IsOneCalibration register value 0x01 = one calibration per day (daily single)
        return Response(isDaily = receivedData[getStartIndex()].toInt() and 0xFF == 0x01)
    }

    data class Response(val isDaily: Boolean) : EversenseBasePacket.Response()
}

class GetCalibrationPhasePacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId), EversenseE3Memory.CalibrationPhase) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(phase = CalibrationPhase.fromE3(receivedData[getStartIndex()].toInt()))
    }

    data class Response(val phase: CalibrationPhase) : EversenseBasePacket.Response()
}

class GetCalibrationReadinessPacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId), EversenseE3Memory.CalibrationReadiness) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(readiness = CalibrationReadiness.from(receivedData[getStartIndex()].toInt()))
    }

    data class Response(val readiness: CalibrationReadiness) : EversenseBasePacket.Response()
}

class GetCompletedCalibrationsCountPacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadTwoByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadTwoByteSerialFlashRegisterResponseId), EversenseE3Memory.CalibrationsMadeInThisPhase) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        val s = getStartIndex()
        val count = (receivedData[s].toInt() and 0xFF) or ((receivedData[s + 1].toInt() and 0xFF) shl 8)
        return Response(count = count)
    }

    data class Response(val count: Int) : EversenseBasePacket.Response()
}

class GetInsertionDatePacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadTwoByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadTwoByteSerialFlashRegisterResponseId), EversenseE3Memory.SensorInsertionDate) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(date = EversenseE3Parser.readDate(receivedData, getStartIndex()))
    }

    data class Response(val date: Long) : EversenseBasePacket.Response()
}

class GetInsertionTimePacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadTwoByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadTwoByteSerialFlashRegisterResponseId), EversenseE3Memory.SensorInsertionTime) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(time = EversenseE3Parser.readTime(receivedData, getStartIndex()))
    }

    data class Response(val time: Long) : EversenseBasePacket.Response()
}

class GetIsOneCalPhasePacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId), EversenseE3Memory.IsOneCalibration) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        return Response(value = receivedData[getStartIndex()].toInt() == 0x55)
    }

    data class Response(val value: Boolean) : EversenseBasePacket.Response()
}

class GetLastCalibrationDatePacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadTwoByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadTwoByteSerialFlashRegisterResponseId), EversenseE3Memory.LastCalibrationDate) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        return Response(date = EversenseE3Parser.readDate(receivedData, getStartIndex()))
    }

    data class Response(val date: Long) : EversenseBasePacket.Response()
}

class GetLastCalibrationTimePacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadTwoByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadTwoByteSerialFlashRegisterResponseId), EversenseE3Memory.LastCalibrationTime) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        return Response(time = EversenseE3Parser.readTime(receivedData, getStartIndex()))
    }

    data class Response(val time: Long) : EversenseBasePacket.Response()
}

class GetSettingGlucoseHighEnabled : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId), EversenseE3Memory.HighGlucoseAlarmEnabled) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(enabled = receivedData[getStartIndex()].toInt() == 0x55)
    }

    data class Response(val enabled: Boolean) : EversenseBasePacket.Response()
}

class GetSettingGlucoseHighThresholdPacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadTwoByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadTwoByteSerialFlashRegisterResponseId), EversenseE3Memory.HighGlucoseAlarmThreshold) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(
            threshold = EversenseE3Parser.readGlucose(receivedData, getStartIndex())
        )
    }

    data class Response(val threshold: Int) : EversenseBasePacket.Response()
}

class GetSettingGlucoseLowThresholdPacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadTwoByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadTwoByteSerialFlashRegisterResponseId), EversenseE3Memory.LowGlucoseAlarmThreshold) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(threshold = EversenseE3Parser.readGlucose(receivedData, getStartIndex()))
    }

    data class Response(val threshold: Int): EversenseBasePacket.Response()
}

class GetSettingPredictiveAlarmEnabledPacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId), EversenseE3Memory.PredictiveAlert) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(enabled = receivedData[getStartIndex()].toInt() == 0x55)
    }

    data class Response(val enabled: Boolean) : EversenseBasePacket.Response()
}

class GetSettingPredictiveHighEnabledPacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId), EversenseE3Memory.PredictiveHighAlert) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(enabled = receivedData[getStartIndex()].toInt() == 0x55)
    }

    data class Response(val enabled: Boolean) : EversenseBasePacket.Response()
}

class GetSettingPredictiveHighThresholdPacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadTwoByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadTwoByteSerialFlashRegisterResponseId), EversenseE3Memory.PredictiveHighTarget) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(threshold = EversenseE3Parser.readGlucose(receivedData, getStartIndex()))
    }

    data class Response(val threshold: Int) : EversenseBasePacket.Response()
}

class GetSettingPredictiveHighTimePacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId), EversenseE3Memory.PredictiveHighTime) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(minutes = receivedData[getStartIndex()].toInt())
    }

    data class Response(val minutes: Int) : EversenseBasePacket.Response()
}

class GetSettingPredictiveLowEnabledPacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId), EversenseE3Memory.PredictiveLowAlert) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(enabled = receivedData[getStartIndex()].toInt() == 0x55)
    }

    data class Response(val enabled: Boolean) : EversenseBasePacket.Response()
}

class GetSettingPredictiveLowThresholdPacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadTwoByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadTwoByteSerialFlashRegisterResponseId), EversenseE3Memory.PredictiveLowTarget) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(threshold = EversenseE3Parser.readGlucose(receivedData, getStartIndex()))
    }

    data class Response(val threshold: Int) : EversenseBasePacket.Response()
}

class GetSettingPredictiveLowTimePacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId), EversenseE3Memory.PredictiveLowTime) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(minutes = receivedData[getStartIndex()].toInt())
    }

    data class Response(val minutes: Int) : EversenseBasePacket.Response()
}

class GetSettingRateEnabledPacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId), EversenseE3Memory.RateAlert) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(enabled = receivedData[getStartIndex()].toInt() == 0x55)
    }

    data class Response(val enabled: Boolean) : EversenseBasePacket.Response()
}

class GetSettingRateFallingEnabledPacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId), EversenseE3Memory.RateFallingAlert) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(enabled = receivedData[getStartIndex()].toInt() == 0x55)
    }

    data class Response(val enabled: Boolean) : EversenseBasePacket.Response()
}

class GetSettingRateFallingThresholdPacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId), EversenseE3Memory.RateFallingThreshold) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(threshold = receivedData[getStartIndex()].toDouble() / 10)
    }

    data class Response(val threshold: Double): EversenseBasePacket.Response()
}

class GetSettingRateRisingEnabledPacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId), EversenseE3Memory.RateRisingAlert) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(enabled = receivedData[getStartIndex()].toInt() == 0x55)
    }

    data class Response(val enabled: Boolean) : EversenseBasePacket.Response()
}

class GetSettingRateRisingThresholdPacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId), EversenseE3Memory.RateRisingThreshold) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(threshold = receivedData[getStartIndex()].toDouble() / 10)
    }

    data class Response(val threshold: Double) : EversenseBasePacket.Response()
}

class GetSettingVibratePacket : E3RegisterPacket(PacketSpec.e3(EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId), EversenseE3Memory.VibrateMode) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(
            enabled = receivedData[getStartIndex()].toInt() == 0x55
        )
    }

    data class Response(val enabled: Boolean) : EversenseBasePacket.Response()
}
