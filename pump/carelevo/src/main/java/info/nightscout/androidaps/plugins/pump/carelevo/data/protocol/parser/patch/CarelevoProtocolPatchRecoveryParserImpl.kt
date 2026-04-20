package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.patch

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolPatchRecoveryRptModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolPatchRecoveryParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolPatchRecoveryRptModel> {

    override fun parse(data: ByteArray): ProtocolPatchRecoveryRptModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()

        return ProtocolPatchRecoveryRptModel(
            timestamp,
            cmd
        )
    }
}