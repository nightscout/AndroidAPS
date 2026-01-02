package app.aaps.pump.omnipod.dash.driver.comm.message

import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.dash.driver.comm.exceptions.MessageIOException
import java.nio.ByteBuffer

/***
 *  String prefix and length encoding and decoding. Example message:
 */
class StringLengthPrefixEncoding private constructor() {

    companion object {

        private const val LENGTH_BYTES = 2

        fun parseKeys(keys: Array<String>, payload: ByteArray): Array<ByteArray> {
            val ret = Array(keys.size) { ByteArray(0) }
            var remaining = payload
            for ((index, key) in keys.withIndex()) {
                remaining.assertSizeAtLeast(key.length)
                when {
                    remaining.copyOfRange(0, key.length).decodeToString() != key ->
                        throw MessageIOException("Key not found: $key in ${payload.toHex()}")
                    // last key can be empty, no length
                    index == keys.size - 1 && remaining.size == key.length       ->
                        return ret
                }
                remaining.assertSizeAtLeast(key.length + LENGTH_BYTES)

                remaining = remaining.copyOfRange(key.length, remaining.size)
                val length = (remaining[0].toUnsignedInt() shl 1) or remaining[1].toUnsignedInt()
                remaining.assertSizeAtLeast(length)
                ret[index] = remaining.copyOfRange(LENGTH_BYTES, LENGTH_BYTES + length)
                remaining = remaining.copyOfRange(LENGTH_BYTES + length, remaining.size)
            }
            return ret
        }

        fun formatKeys(keys: Array<String>, payloads: Array<ByteArray>): ByteArray {
            val payloadTotalSize = payloads.fold(0) { acc, i -> acc + i.size }
            val keyTotalSize = keys.fold(0) { acc, i -> acc + i.length }
            val zeros = payloads.fold(0) { acc, i -> acc + if (i.isEmpty()) 1 else 0 }

            val bb = ByteBuffer.allocate(2 * (keys.size - zeros) + keyTotalSize + payloadTotalSize)
            for (idx in keys.indices) {
                val k = keys[idx]
                val payload = payloads[idx]
                bb.put(k.toByteArray())
                if (payload.isNotEmpty()) {
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

private fun ByteArray.assertSizeAtLeast(size: Int) {
    if (this.size < size) {
        throw MessageIOException("Payload too short: ${this.toHex()}")
    }
}
