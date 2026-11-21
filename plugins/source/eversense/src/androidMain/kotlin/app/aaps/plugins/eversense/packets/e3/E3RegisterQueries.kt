package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.enums.CalibrationPhase
import app.aaps.plugins.eversense.enums.CalibrationReadiness
import app.aaps.plugins.eversense.enums.EversenseE3Memory
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
