package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.Id
import java.nio.ByteBuffer

/***
 * MessagePacket contains header and raw payload for a message
 */
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
        val bb = ByteBuffer.allocate(16 + payload.size)
        bb.put(MAGIC_PATTERN.toByteArray())

        val f1 = Flag()
        f1.set(0, this.version.toInt() and 4 != 0)
        f1.set(1, this.version.toInt() and 2 != 0)
        f1.set(2, this.version.toInt() and 1 != 0)
        f1.set(3, this.sas)
        f1.set(4, this.tfs)
        f1.set(5, this.eqos.toInt() and 4 != 0)
        f1.set(6, this.eqos.toInt() and 2 != 0)
        f1.set(7, this.eqos.toInt() and 1 != 0)

        val f2 = Flag()
        f2.set(0, this.ack)
        f2.set(1, this.priority)
        f2.set(2, this.lastMessage)
        f2.set(3, this.gateway)
        f2.set(4, this.type.value.toInt() and 8 != 0)
        f2.set(5, this.type.value.toInt() and 4 != 0)
        f2.set(6, this.type.value.toInt() and 2 != 0)
        f2.set(7, this.type.value.toInt() and 1 != 0)

        bb.put(f1.value.toByte())
        bb.put(f2.value.toByte())
        bb.put(this.sequenceNumber)
        bb.put(this.ackNumber)

        bb.put((this.payload.size ushr 3).toByte())
        bb.put((this.payload.size shl 5).toByte())

        bb.put(this.source.address)
        bb.put(this.destination.address)

        bb.put(this.payload)

        val ret = ByteArray(bb.position())
        bb.flip()
        bb.get(ret)

        return ret
    }

    companion object {

        private val MAGIC_PATTERN = "TW" // all messages start with this string

        fun parse(payload: ByteArray): MessagePacket {
            TODO("implement message header parsing")
        }
    }
}

private class Flag(var value: Int = 0) {

    fun set(idx: Byte, set: Boolean) {
        val mask = 1 shl (7 - idx)
        if (!set)
            return
        value = value or mask
    }

    fun get(idx: Byte): Boolean {
        val mask = 1 shl (7 - idx)
        return value and mask != 0
    }
}