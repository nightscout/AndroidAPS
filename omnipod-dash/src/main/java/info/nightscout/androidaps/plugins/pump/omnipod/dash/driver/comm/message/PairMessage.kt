package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

class PairMessage(source: Address, destination: Address, payload: ByteArray, sequenceNumber: Byte
) : Message(
    type=MessageType.PAIRING, source, destination, payload, sequenceNumber,
) {

}
