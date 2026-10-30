package app.aaps.plugins.libre3.crypto

/**
 * One AES block, 16 bytes in and 16 bytes out.
 *
 * The Libre 3 work uses two different block makers behind the same shape:
 * the ordinary AES of the phone for the glucose data, and the sensor's own
 * table driven block for the pairing messages. Keeping them apart is a safety
 * rule of this project: the two planes must never be mixed.
 */
fun interface Libre3AesBlock {

    fun encryptBlock(input: ByteArray): ByteArray
}

/** Raised when an encrypted message does not match its own tag, or a size is wrong. */
class Libre3CryptoException(message: String) : Exception(message)

/**
 * AES-CCM as written in NIST SP 800-38C, with the block maker given by the caller.
 *
 * Ported from LibreCRKit `Crypto/AESCCM.swift` at pin `a86b92f`. Checked against the packet vector
 * number one of RFC 3610, the same vector the upstream tests use.
 */
object Libre3AesCcm {

    private const val BLOCK_SIZE = 16
    private val ALLOWED_TAG_SIZES = intArrayOf(4, 6, 8, 10, 12, 14, 16)

    /** Result of one encryption: the encrypted bytes and the short tag that protects them. */
    data class Sealed(val ciphertext: ByteArray, val tag: ByteArray) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Sealed) return false
            return ciphertext.contentEquals(other.ciphertext) && tag.contentEquals(other.tag)
        }

        override fun hashCode(): Int = 31 * ciphertext.contentHashCode() + tag.contentHashCode()
    }

    fun encrypt(
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray = ByteArray(0),
        tagLength: Int = 8,
        aes: Libre3AesBlock,
    ): Sealed {
        checkParameters(nonce, tagLength, plaintext.size)
        val mac = cbcMac(nonce, aad, plaintext, tagLength, aes)
        val (ciphertext, firstKeyBlock) = counterMode(plaintext, nonce, aes)
        val tag = xor(mac.copyOf(tagLength), firstKeyBlock.copyOf(tagLength))
        return Sealed(ciphertext, tag)
    }

    /**
     * @return the plain bytes.
     * @throws Libre3CryptoException when the tag does not match, which means the message was
     *   changed on the way or the wrong key was used.
     */
    fun decrypt(
        nonce: ByteArray,
        ciphertext: ByteArray,
        tag: ByteArray,
        aad: ByteArray = ByteArray(0),
        aes: Libre3AesBlock,
    ): ByteArray {
        checkParameters(nonce, tag.size, ciphertext.size)
        val (plaintext, firstKeyBlock) = counterMode(ciphertext, nonce, aes)
        val mac = cbcMac(nonce, aad, plaintext, tag.size, aes)
        val expected = xor(mac.copyOf(tag.size), firstKeyBlock.copyOf(tag.size))
        if (!constantTimeEquals(expected, tag)) throw Libre3CryptoException("the message does not match its tag")
        return plaintext
    }

    /**
     * The ordinary AES of the phone, used for the glucose data plane only.
     *
     * The block maker itself is the one thing here that each platform has to supply, see
     * [libre3StandardAesBlock]. Everything above - the CCM rule, the nonce, the tag - is shared.
     */
    fun standardAes(key: ByteArray): Libre3AesBlock {
        require(key.size == 16) { "AES-128 needs a 16 byte key" }
        return libre3StandardAesBlock(key)
    }

    private fun checkParameters(nonce: ByteArray, tagLength: Int, plaintextLength: Int) {
        val lengthField = 15 - nonce.size
        if (lengthField < 2 || lengthField > 8) throw Libre3CryptoException("the nonce length is not allowed")
        if (tagLength !in ALLOWED_TAG_SIZES.toList()) throw Libre3CryptoException("the tag length is not allowed")
        if (lengthField < 8) {
            val max = (1L shl (lengthField * 8)) - 1
            if (plaintextLength.toLong() > max) throw Libre3CryptoException("the message is too long for this nonce")
        }
    }

    private fun cbcMac(
        nonce: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
        tagLength: Int,
        aes: Libre3AesBlock,
    ): ByteArray {
        var blocks = formatHeader(nonce, aad, plaintext.size, tagLength) + plaintext
        if (blocks.size % BLOCK_SIZE != 0) blocks += ByteArray(BLOCK_SIZE - blocks.size % BLOCK_SIZE)
        var chain = ByteArray(BLOCK_SIZE)
        for (i in 0 until blocks.size / BLOCK_SIZE) {
            val block = blocks.copyOfRange(i * BLOCK_SIZE, (i + 1) * BLOCK_SIZE)
            chain = aes.encryptBlock(xor(chain, block))
        }
        return chain
    }

    private fun formatHeader(nonce: ByteArray, aad: ByteArray, plaintextLength: Int, tagLength: Int): ByteArray {
        val lengthField = 15 - nonce.size
        val first = ByteArray(BLOCK_SIZE)
        val aadFlag = if (aad.isEmpty()) 0 else 0x40
        val tagFlag = (tagLength - 2) / 2
        first[0] = (aadFlag or (tagFlag shl 3) or (lengthField - 1)).toByte()
        nonce.copyInto(first, 1)
        var length = plaintextLength.toLong()
        for (i in 0 until lengthField) {
            first[15 - i] = (length and 0xFF).toByte()
            length = length shr 8
        }
        if (aad.isEmpty()) return first

        var encodedAad = when {
            aad.size < 0xFF00 -> byteArrayOf(((aad.size shr 8) and 0xFF).toByte(), (aad.size and 0xFF).toByte())
            else              -> byteArrayOf(
                0xFF.toByte(), 0xFE.toByte(),
                ((aad.size shr 24) and 0xFF).toByte(), ((aad.size shr 16) and 0xFF).toByte(),
                ((aad.size shr 8) and 0xFF).toByte(), (aad.size and 0xFF).toByte(),
            )
        } + aad
        if (encodedAad.size % BLOCK_SIZE != 0) encodedAad += ByteArray(BLOCK_SIZE - encodedAad.size % BLOCK_SIZE)
        return first + encodedAad
    }

    /**
     * Counter mode.
     *
     * @return the changed bytes, and the key block of counter zero, which the tag is built from.
     */
    private fun counterMode(input: ByteArray, nonce: ByteArray, aes: Libre3AesBlock): Pair<ByteArray, ByteArray> {
        val lengthField = 15 - nonce.size
        val base = ByteArray(BLOCK_SIZE)
        base[0] = (lengthField - 1).toByte()
        nonce.copyInto(base, 1)
        val firstKeyBlock = aes.encryptBlock(base.copyOf())

        val out = ByteArray(input.size)
        var counter = 1L
        var index = 0
        while (index < input.size) {
            val block = base.copyOf()
            var value = counter
            for (j in 0 until lengthField) {
                block[15 - j] = (value and 0xFF).toByte()
                value = value shr 8
            }
            val keyBlock = aes.encryptBlock(block)
            val take = minOf(BLOCK_SIZE, input.size - index)
            for (k in 0 until take) out[index + k] = (input[index + k].toInt() xor keyBlock[k].toInt()).toByte()
            index += take
            counter++
        }
        return out to firstKeyBlock
    }

    private fun xor(a: ByteArray, b: ByteArray): ByteArray =
        ByteArray(a.size) { (a[it].toInt() xor b[it].toInt()).toByte() }

    /** Compares in a way that always takes the same time, so a wrong tag tells nothing away. */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var difference = 0
        for (i in a.indices) difference = difference or (a[i].toInt() xor b[i].toInt())
        return difference == 0
    }
}
