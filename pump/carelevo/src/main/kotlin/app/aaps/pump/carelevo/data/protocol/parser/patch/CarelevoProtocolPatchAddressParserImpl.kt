package app.aaps.pump.carelevo.data.protocol.parser.patch

import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchAddressRspModel
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParser
import app.aaps.pump.carelevo.ext.convertBytesToHex

class CarelevoProtocolPatchAddressParserImpl(
    override val command: Int
) : CarelevoProtocolParser<ByteArray, ProtocolPatchAddressRspModel> {

    override fun parse(data: ByteArray): ProtocolPatchAddressRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        // val value = data[1].toUByte().toInt()
        val macAddress = data.filterIndexed { index, byte ->
            // (index != 0 && index != 1) && index <= 7
            index in 1..6
        }.toByteArray().convertBytesToHex()
        val checkSum = data.filterIndexed { index, byte ->
            index > 6
        }.toByteArray().convertBytesToHex()

        return ProtocolPatchAddressRspModel(
            timestamp,
            cmd,
            // value,
            macAddress,
            checkSum
        )
    }
}