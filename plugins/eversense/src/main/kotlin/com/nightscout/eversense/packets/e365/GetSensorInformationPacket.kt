package com.nightscout.eversense.packets.e365

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.packets.e365.utils.toUnix
import com.nightscout.eversense.packets.e365.utils.toUtfString

@EversensePacket(
    requestId = Eversense365Packets.ReadCommandId,
    responseId = Eversense365Packets.ReadResponseId,
    typeId = Eversense365Packets.ReadSensorInformation,
    securityType = EversenseSecurityType.SecureV2
)
class GetSensorInformationPacket : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        return byteArrayOf()
    }

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