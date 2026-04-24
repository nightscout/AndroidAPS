package com.nightscout.eversense.packets.e365

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.packets.e365.utils.toShort

@EversensePacket(
    requestId = Eversense365Packets.ReadCommandId,
    responseId = Eversense365Packets.ReadResponseId,
    typeId = Eversense365Packets.ReadPatientInformation,
    securityType = EversenseSecurityType.SecureV2
)
class GetPatientSettingsPacket : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        return byteArrayOf()
    }

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
            lowGlucoseAlarmInMgDl = receivedData.copyOfRange(60, 62).toShort().toInt(),
            highGlucoseAlarmInMgDl = receivedData.copyOfRange(63, 65).toShort().toInt(),
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