package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

abstract class Message(
    val type: MessageType,
    val source: Address,
    val destination: Address,
    val payload: ByteArray,
    val sequenceNumber: Byte,
    val ack: Boolean = false,
    val ackNumber: Byte = 0.toByte(),
    val eqos: Short = 0.toShort(), // TODO: understand
    val priority: Boolean = false,
    val lastMessage: Boolean = false,
    val gateway: Boolean = false,
    val sas: Boolean = false, // TODO: understand
    val tfs: Boolean = false, // TODO: understand
    val version: Short = 0.toShort(),
) {

    fun asByteArray(): ByteArray {
        return payload; // TODO implement
    }
}