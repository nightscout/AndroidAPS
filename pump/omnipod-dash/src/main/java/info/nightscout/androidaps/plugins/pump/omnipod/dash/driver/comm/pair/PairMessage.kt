package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.pair

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.Id
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessagePacket
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.StringLengthPrefixEncoding

data class PairMessage(
    val sequenceNumber: Byte,
    val source: Id,
    val destination: Id,
    private val keys: Array<String>,
    private val payloads: Array<ByteArray>,
    val messagePacket: MessagePacket = MessagePacket(
        type = MessageType.PAIRING,
        source = source,
        destination = destination,
        payload = StringLengthPrefixEncoding.formatKeys(
            keys,
            payloads,
        ),
        sequenceNumber = sequenceNumber,
        sas = true // TODO: understand why this is true for PairMessages
    )
)
