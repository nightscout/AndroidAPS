package app.aaps.plugins.eversense.protocol

import java.io.ByteArrayOutputStream

/** Framing is independent of packet payloads and the cryptographic session. */
internal object WireEncoding {
    private val crcTable = IntArray(256) { seed ->
        var remainder = seed shl 8
        repeat(8) {
            remainder = (remainder shl 1) xor if (remainder and 0x8000 == 0) 0 else 0x1021
        }
        remainder and 0xffff
    }

    fun crc16(bytes: ByteArray): ByteArray {
        val value = bytes.fold(0xffff) { crc, octet ->
            ((crc shl 8) xor crcTable[(crc ushr 8) xor (octet.toInt() and 0xff)]) and 0xffff
        }
        return byteArrayOf(value.toByte(), (value ushr 8).toByte())
    }

    fun e3(command: Byte, payload: ByteArray): ByteArray {
        val message = byteArrayOf(command) + payload
        return message + crc16(message)
    }

    fun v2(message: ByteArray, payloadSize: Int): ByteArray {
        val capacity = payloadSize - 2
        val chunks = (message.size + capacity - 1) / capacity
        // Preserve the transmitter protocol's existing empty-ciphertext framing.
        if (chunks == 0) return byteArrayOf(0)
        val output = ByteArrayOutputStream(message.size + 2 * chunks + 1)
        for (chunk in 0 until chunks) {
            output.write(chunk + 1)
            output.write(chunks)
            if (chunk == 0) output.write(1)
            val offset = chunk * capacity
            output.write(message, offset, minOf(capacity, message.size - offset))
        }
        return output.toByteArray()
    }
}
