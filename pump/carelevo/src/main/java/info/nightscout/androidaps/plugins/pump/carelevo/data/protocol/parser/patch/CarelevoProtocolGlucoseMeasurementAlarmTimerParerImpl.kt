package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.patch

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolGlucoseMeasurementAlarmTimerRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolGlucoseMeasurementAlarmTimerParerImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolGlucoseMeasurementAlarmTimerRspModel> {

    override fun parse(data: ByteArray): ProtocolGlucoseMeasurementAlarmTimerRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val timerId = data[1].toUByte().toInt()
        val minutes = data[2].toUByte().toInt()

        return ProtocolGlucoseMeasurementAlarmTimerRspModel(
            timestamp,
            cmd,
            timerId,
            minutes
        )
    }
}