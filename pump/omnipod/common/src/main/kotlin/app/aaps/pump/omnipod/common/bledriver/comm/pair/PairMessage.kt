package app.aaps.pump.omnipod.common.bledriver.comm.pair

import app.aaps.pump.omnipod.common.bledriver.comm.Id
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessagePacket
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageType
import app.aaps.pump.omnipod.common.bledriver.comm.message.StringLengthPrefixEncoding

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
