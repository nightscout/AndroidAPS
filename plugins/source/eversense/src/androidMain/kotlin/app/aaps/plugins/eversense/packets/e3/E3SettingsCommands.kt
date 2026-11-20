package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.enums.EversenseE3Memory
import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.e3.util.EversenseE3Writer
import app.aaps.plugins.eversense.protocol.PacketSpec

class SetSettingGlucoseHighEnablePacket(private val enabled: Boolean) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.WriteSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.WriteSingleByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.HighGlucoseAlarmEnabled.getRequestData() + EversenseE3Writer.writeBoolean(enabled)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response()
    }

    class Response() : EversenseBasePacket.Response()
}

class SetSettingGlucoseHighThresholdPacket(private val threshold: Int) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.WriteTwoByteSerialFlashRegisterCommandId, EversenseE3Packets.WriteTwoByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.HighGlucoseAlarmThreshold.getRequestData() + EversenseE3Writer.writeInt16(threshold)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response()
    }

    class Response() : EversenseBasePacket.Response()
}

class SetSettingGlucoseLowThresholdPacket(private val threshold: Int) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.WriteTwoByteSerialFlashRegisterCommandId, EversenseE3Packets.WriteTwoByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.LowGlucoseAlarmThreshold.getRequestData() + EversenseE3Writer.writeInt16(threshold)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response()
    }

    class Response() : EversenseBasePacket.Response()
}

class SetSettingPredictiveAlarmEnabledPacket(private val enabled: Boolean) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.WriteSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.WriteSingleByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.PredictiveAlert.getRequestData() + EversenseE3Writer.writeBoolean(enabled)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}

class SetSettingPredictiveHighAlarmEnabledPacket(private val enabled: Boolean) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.WriteSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.WriteSingleByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.PredictiveHighAlert.getRequestData() + EversenseE3Writer.writeBoolean(enabled)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}

class SetSettingPredictiveHighThresholdPacket(private val threshold: Int) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.WriteTwoByteSerialFlashRegisterCommandId, EversenseE3Packets.WriteTwoByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.PredictiveHighTarget.getRequestData() + EversenseE3Writer.writeInt16(threshold)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}

class SetSettingPredictiveHighTimePacket(private val minutes: Int) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.WriteSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.WriteSingleByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.PredictiveHighTime.getRequestData() + byteArrayOf(minutes.toByte())
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}

class SetSettingPredictiveLowAlarmEnabledPacket(private val enabled: Boolean) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.WriteSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.WriteSingleByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.PredictiveLowAlert.getRequestData() + EversenseE3Writer.writeBoolean(enabled)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}

class SetSettingPredictiveLowThresholdPacket(private val threshold: Int) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.WriteTwoByteSerialFlashRegisterCommandId, EversenseE3Packets.WriteTwoByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.PredictiveLowTarget.getRequestData() + EversenseE3Writer.writeInt16(threshold)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}

class SetSettingPredictiveLowTimePacket(private val minutes: Int) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.WriteSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.WriteSingleByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.PredictiveLowTime.getRequestData() + byteArrayOf(minutes.toByte())
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}

class SetSettingRateEnabledPacket(private val enabled: Boolean) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.WriteSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.WriteSingleByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.RateAlert.getRequestData() + EversenseE3Writer.writeBoolean(enabled)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}

class SetSettingRateFallingEnabledPacket(private val enabled: Boolean) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.WriteSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.WriteSingleByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.RateFallingAlert.getRequestData() + EversenseE3Writer.writeBoolean(enabled)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}

class SetSettingRateFallingThresholdPacket(private val threshold: Double) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.WriteSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.WriteSingleByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.RateFallingThreshold.getRequestData() + EversenseE3Writer.writeDouble(threshold)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}

class SetSettingRateRisingEnabledPacket(private val enabled: Boolean) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.WriteSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.WriteSingleByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.RateRisingAlert.getRequestData() + EversenseE3Writer.writeBoolean(enabled)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}

class SetSettingRateRisingThresholdPacket(private val threshold: Double) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.WriteSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.WriteSingleByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.RateRisingThreshold.getRequestData() + EversenseE3Writer.writeDouble(threshold)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}

class SetSettingVibratePacket(private val enabled: Boolean) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.WriteSingleByteSerialFlashRegisterCommandId, EversenseE3Packets.WriteSingleByteSerialFlashRegisterResponseId)) {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.VibrateMode.getRequestData() + EversenseE3Writer.writeBoolean(enabled)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}
