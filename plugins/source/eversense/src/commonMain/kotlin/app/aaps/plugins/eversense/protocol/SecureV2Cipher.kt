package app.aaps.plugins.eversense.protocol

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES

/**
 * Stateless authenticated encryption; session identity and sequence ownership stay with the caller.
 * A failure (for a decrypt: the frame does not pass its check) gives null; the caller logs it.
 *
 * The 365 uses AES-CCM (RFC 3610) with an 8 byte nonce, an 8 byte tag and the 2 byte frame prefix as associated data.
 * No platform library offers CCM on both Android and iOS, so it is built here from the plain AES block, which they all
 * have. `SecureChannelCompatibilityTest` checks it byte for byte against the BouncyCastle CCM of the first version.
 */
internal object SecureV2Cipher {

    private const val BLOCK = 16

    fun prefix(sequence: Long): ByteArray = BinaryFields.littleEndian(sequence shl 2, 2)

    fun nonce(seed: ByteArray, sequence: Long): ByteArray {
        val original = BinaryFields.unsigned(seed.toUByteArray(), 0, 8)
        return BinaryFields.littleEndian((original and -16384L) or sequence, 8)
    }

    fun encrypt(data: ByteArray, key: ByteArray, prefix: ByteArray, nonce: ByteArray): ByteArray? = try {
        val aes = AesBlock(key)
        val tagSize = nonce.size
        val tag = mac(aes, data, prefix, nonce, tagSize)
        val stream = keyStream(aes, nonce, data.size)
        val cipherText = ByteArray(data.size) { (data[it].toInt() xor stream[BLOCK + it].toInt()).toByte() }
        val sealedTag = ByteArray(tagSize) { (tag[it].toInt() xor stream[it].toInt()).toByte() }
        prefix + cipherText + sealedTag
    } catch (_: Exception) {
        null
    }

    fun decrypt(data: ByteArray, key: ByteArray, prefix: ByteArray, nonce: ByteArray): ByteArray? = try {
        val tagSize = nonce.size
        if (data.size < tagSize) null
        else {
            val aes = AesBlock(key)
            val length = data.size - tagSize
            val stream = keyStream(aes, nonce, length)
            val plain = ByteArray(length) { (data[it].toInt() xor stream[BLOCK + it].toInt()).toByte() }
            val received = ByteArray(tagSize) { (data[length + it].toInt() xor stream[it].toInt()).toByte() }
            val expected = mac(aes, plain, prefix, nonce, tagSize)
            if (sameBytes(received, expected)) plain else null
        }
    } catch (_: Exception) {
        null
    }

    /** CBC-MAC over B0, the associated data and the message, cut to [tagSize] bytes. */
    private fun mac(aes: AesBlock, message: ByteArray, associated: ByteArray, nonce: ByteArray, tagSize: Int): ByteArray {
        val lengthSize = 15 - nonce.size
        val flags = (if (associated.isNotEmpty()) 0x40 else 0) or (((tagSize - 2) / 2) shl 3) or (lengthSize - 1)
        val input = ByteSink(BLOCK * 4 + message.size)
        input.write(flags)
        input.write(nonce)
        input.write(bigEndian(message.size.toLong(), lengthSize))
        if (associated.isNotEmpty()) {
            // Associated data shorter than 0xFF00 bytes carries a 2 byte length, as here
            input.write(bigEndian(associated.size.toLong(), 2))
            input.write(associated)
            padToBlock(input)
        }
        input.write(message)
        padToBlock(input)
        val blocks = input.toByteArray()
        var chain = ByteArray(BLOCK)
        for (offset in blocks.indices step BLOCK) {
            chain = aes.encrypt(ByteArray(BLOCK) { (chain[it].toInt() xor blocks[offset + it].toInt()).toByte() })
        }
        return chain.copyOf(tagSize)
    }

    /** The counter blocks A0, A1, ... encrypted: A0 seals the tag, A1 onwards the message. */
    private fun keyStream(aes: AesBlock, nonce: ByteArray, length: Int): ByteArray {
        val lengthSize = 15 - nonce.size
        val blocks = 1 + (length + BLOCK - 1) / BLOCK
        val counters = ByteSink(blocks * BLOCK)
        for (counter in 0 until blocks) {
            counters.write(lengthSize - 1)
            counters.write(nonce)
            counters.write(bigEndian(counter.toLong(), lengthSize))
        }
        return aes.encrypt(counters.toByteArray())
    }

    private fun padToBlock(sink: ByteSink) {
        while (sink.size % BLOCK != 0) sink.write(0)
    }

    private fun bigEndian(value: Long, width: Int): ByteArray = ByteArray(width) { position ->
        (value ushr ((width - 1 - position) * 8)).toByte()
    }

    /** Compares the whole length, so the time taken does not tell where a forged tag differs. */
    private fun sameBytes(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var difference = 0
        for (index in a.indices) difference = difference or (a[index].toInt() xor b[index].toInt())
        return difference == 0
    }

    /** AES on whole 16 byte blocks with no chaining (ECB): the building block of CCM, not a mode used by itself. */
    @OptIn(DelicateCryptographyApi::class)
    private class AesBlock(key: ByteArray) {

        private val cipher = CryptographyProvider.Default.get(AES.ECB).keyDecoder()
            .decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
            .cipher(padding = false)

        fun encrypt(blocks: ByteArray): ByteArray = cipher.encryptBlocking(blocks)
    }
}
