package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.MessageIOException
import info.nightscout.androidaps.utils.extensions.toHex
import java.nio.ByteBuffer

/***
 *  String prefix and length encoding and decoding. Example message:
 */
class StringLengthPrefixEncoding {

    companion object {

        private val LENGTH_BYTES = 2

        fun parseKeys(keys: Array<String>, payload: ByteArray): Array<ByteArray> {
            val ret = Array<ByteArray>(keys.size, { ByteArray(0) })
            var remaining = payload
            for ((index, key) in keys.withIndex()) {
                when {
                    remaining.size < key.length ->
                        throw MessageIOException("Payload too short: ${payload.toHex()} for key: $key")
                    !(remaining.copyOfRange(0, key.length).decodeToString() == key) ->
                        throw MessageIOException("Key not found: $key in ${payload.toHex()}")
                    // last key can be empty, no length
                    index == keys.size - 1 && remaining.size == key.length ->
                        return ret

                    remaining.size < key.length + LENGTH_BYTES ->
                        throw MessageIOException("Length not found: for $key in ${payload.toHex()}")
                }
                remaining = remaining.copyOfRange(key.length, remaining.size)
                val length = (remaining[0].toUnsignedInt() shl 1) or remaining[1].toUnsignedInt()
                if (length > remaining.size) {
                    throw MessageIOException("Payload too short, looking for length $length for $key in ${payload.toHex()}")
                }
                ret[index] = remaining.copyOfRange(LENGTH_BYTES, LENGTH_BYTES + length)
                remaining = remaining.copyOfRange(LENGTH_BYTES + length, remaining.size)
            }
            return ret
        }

        fun formatKeys(keys: Array<String>, payloads: Array<ByteArray>): ByteArray {
            val payloadTotalSize = payloads.fold(0) { acc, i -> acc + i.size }
            val keyTotalSize = keys.fold(0) { acc, i -> acc + i.length }
            val zeros = payloads.fold(0) { acc, i -> acc + if (i.size == 0) 1 else 0 }

            val bb = ByteBuffer.allocate(2 * (keys.size - zeros) + keyTotalSize + payloadTotalSize)
            for (idx in keys.indices) {
                val k = keys[idx]
                val payload = payloads[idx]
                bb.put(k.toByteArray())
                if (payload.size > 0) {
                    bb.putShort(payload.size.toShort())
                    bb.put(payload)
                }
            }

            val ret = ByteArray(bb.position())
            bb.flip()
            bb.get(ret)

            return ret
        }
    }
}
