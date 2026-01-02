package app.aaps.pump.omnipod.dash.driver.comm.pair

import app.aaps.pump.omnipod.dash.driver.comm.Id
import app.aaps.pump.omnipod.dash.driver.comm.message.MessagePacket
import app.aaps.pump.omnipod.dash.driver.comm.message.MessageType
import app.aaps.pump.omnipod.dash.driver.comm.message.StringLengthPrefixEncoding

class PairMessage(
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
