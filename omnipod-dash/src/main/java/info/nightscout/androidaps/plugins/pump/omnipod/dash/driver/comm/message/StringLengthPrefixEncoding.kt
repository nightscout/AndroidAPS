package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

import java.nio.ByteBuffer

/***
 *  String prefix and length encoding and decoding. Example message:
 */
class StringLengthPrefixEncoding {

    companion object {

        fun parseKeys(keys: List<String>): List<ByteArray> {
            TODO("not implemented")
        }

        fun formatKeys(keys: Array<String>, payloads: Array<ByteArray>): ByteArray {
            val payloadTotalSize = payloads.fold(0) { acc, i -> acc + i.size }
            val keyTotalSize = keys.fold(0) { acc, i -> acc + i.length }

            val bb = ByteBuffer.allocate(2 * keys.size + keyTotalSize + payloadTotalSize)
            for (idx in keys.indices) {
                val k = keys[idx]
                val payload = payloads[idx]
                bb.put(k.toByteArray())
                bb.putShort(payload.size.toShort())
                bb.put(payload)
            }

            val ret = ByteArray(bb.position())
            bb.flip()
            bb.get(ret)

            return ret
        }
    }
}