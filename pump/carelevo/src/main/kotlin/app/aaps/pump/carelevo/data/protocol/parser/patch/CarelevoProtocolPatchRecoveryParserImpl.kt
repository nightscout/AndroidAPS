package app.aaps.pump.carelevo.data.protocol.parser.patch

import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchRecoveryRptModel
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolPatchRecoveryParserImpl(
    override val command: Int
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