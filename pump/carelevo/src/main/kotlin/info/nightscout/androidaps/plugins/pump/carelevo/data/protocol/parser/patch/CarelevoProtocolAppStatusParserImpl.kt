package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.patch

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolAppStatusRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolAppStatusParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolAppStatusRspModel> {

    override fun parse(data: ByteArray): ProtocolAppStatusRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val status = data[1].toUByte().toInt()

        return ProtocolAppStatusRspModel(
            timestamp,
            cmd,
            status
        )
    }
}