package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.pair

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.Id
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessagePacket
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageType

data class PairMessage(
    val sequenceNumber: Byte,
    val source: Id,
    val destination: Id,
    val payload: ByteArray,
    val messagePacket: MessagePacket = MessagePacket(
        type = MessageType.PAIRING,
        source = source,
        destination = destination,
        payload = payload,
        sequenceNumber = sequenceNumber,
        sas = true // TODO: understand why this is true for PairMessages
    ),
)