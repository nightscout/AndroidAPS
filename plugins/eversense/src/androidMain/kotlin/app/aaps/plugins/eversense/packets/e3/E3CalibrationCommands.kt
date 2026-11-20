package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.protocol.CalibrationPayload
import app.aaps.plugins.eversense.protocol.PacketSpec
import app.aaps.plugins.eversense.protocol.PacketTime

class SendCalibrationPacket(private val glucoseMgDl: Int, private val sampleTimeMs: Long = PacketTime.nowMillis()) :
    EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.SendBloodGlucoseDataWithTwoTimestampsCommandId, EversenseE3Packets.SendBloodGlucoseDataWithTwoTimestampsResponseId)) {

    override fun getRequestData(): ByteArray = CalibrationPayload.e3(glucoseMgDl, sampleTimeMs, PacketTime.nowMillis())

    override fun parseResponse(): Response? = if (receivedData.isEmpty()) null else Response()

    class Response : EversenseBasePacket.Response()
}

class SetBloodGlucosePointPacket(private val glucoseInMgDl: Int, private val sampleTimestamp: Long = PacketTime.nowMillis()) :
    EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.SendBloodGlucoseDataWithTwoTimestampsCommandId, EversenseE3Packets.SendBloodGlucoseDataWithTwoTimestampsResponseId)) {
    override val skipResponseIdValidation = true

    override fun getRequestData(): ByteArray = CalibrationPayload.e3(glucoseInMgDl, sampleTimestamp, PacketTime.nowMillis())

    override fun parseResponse(): Response? = if (receivedData.isEmpty()) null else Response()

    class Response : EversenseBasePacket.Response()
}
