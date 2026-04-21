package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.patch

import android.os.Build.VERSION_CODES.P
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolSetTimeRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolTimeSetParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolSetTimeRspModel> {

    override fun parse(data: ByteArray): ProtocolSetTimeRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()

        return ProtocolSetTimeRspModel(
            timestamp,
            cmd,
            result
        )
    }
}