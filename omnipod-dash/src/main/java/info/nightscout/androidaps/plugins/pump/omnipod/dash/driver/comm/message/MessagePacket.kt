package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

import java.io.DataOutput
import java.nio.ByteBuffer

/***
 * MessagePacket contains header and raw payload for a message
 */

class Flag:Byte{
    fun set(idx: Byte, val: Boolean) {
        val mask = 1 <lsh (7-idx)

    }
func (f *flag) set(index byte, val bool) {
	var mask flag = 1 << (7 - index)
	if !val {
		return
	}
	*f |= mask
}

func (f flag) get(index byte) byte {
	var mask flag = 1 << (7 - index)
	if f&mask == 0 {
		return 0
	}
	return 1
}
}

data class MessagePacket(
    val type: MessageType,
    val source: Id,
    val destination: Id,
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
    val version: Short = 0.toShort()) {

    fun asByteArray(): ByteArray {
        val v = ByteBuffer.allocate(16+ payload.size)
        v.put(MAGIC_PATTERN.toByteArray())
        val f1: Byte=0


        return this.payload
    }

    companion object {
        private val MAGIC_PATTERN = "TW" // all messages start with this string

        fun parse(payload: ByteArray): MessagePacket {
            TODO("implement message header parsing")
        }
    }
}