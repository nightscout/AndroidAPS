package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.patch

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolPatchOperationDataRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolPatchOperationDataParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolPatchOperationDataRspModel> {

    override fun parse(data: ByteArray): ProtocolPatchOperationDataRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val mode = data[1].toUByte().toInt()
        val pulseCnt = data[2].toUByte().toInt()
        val totalNo = data[3].toUByte().toInt()
        val count = data[4].toUByte().toInt()
        val hour = data[5].toUByte().toInt()
        val min = data[6].toUByte().toInt()
        val useMins = hour * 60 + min
        val remainUnit100 = data[7].toUByte().toInt() * 100.0
        val remainUnitInt = data[8].toUByte().toInt()
        val remainUnitDecimal = data[9].toUByte().toInt() / 100.0
        val remains = remainUnit100 + remainUnitInt + remainUnitDecimal

        return ProtocolPatchOperationDataRspModel(
            timestamp,
            cmd,
            mode,
            pulseCnt,
            totalNo,
            count,
            useMins,
            remains
        )
    }
}