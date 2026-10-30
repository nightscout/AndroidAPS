package app.aaps.plugins.libre3.crypto

/**
 * Phase 5, the message this phone sends to the sensor during pairing.
 *
 * Ported from LibreCRKit `Pairing/ChallengeMessage.swift` at pin `a86b92f`.
 *
 * Shape on the wire:
 * - plain text 36 bytes: `R1 (16) || R2 (16) || tail (4)`. R1 is the sensor's own random part,
 *   R2 is made by this phone, and the tail is the PIN that came from the NFC step
 * - encrypted 36 bytes, then a 4 byte tag
 * - padded with 14 zero bytes to 54 bytes, so the fixed three writes of 18 bytes line up
 *
 * The block maker for this message is **not** the ordinary AES of the phone. Mixing the two
 * planes is a safety rule of this project, so the block maker is always passed in.
 */
data class Libre3Phase5Challenge(val ciphertext: ByteArray, val tag: ByteArray) {

    init {
        require(ciphertext.size == CIPHERTEXT_SIZE) { "the Phase 5 message must be 36 bytes" }
        require(tag.size == TAG_SIZE) { "the Phase 5 tag must be 4 bytes" }
    }

    /** The 40 bytes that carry meaning. */
    val logicalBytes: ByteArray get() = ciphertext + tag

    /** The 54 bytes that go on the wire, tail filled with zeros. */
    val wireBytes: ByteArray get() = logicalBytes + ByteArray(WIRE_SIZE - LOGICAL_SIZE)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Libre3Phase5Challenge) return false
        return ciphertext.contentEquals(other.ciphertext) && tag.contentEquals(other.tag)
    }

    override fun hashCode(): Int = 31 * ciphertext.contentHashCode() + tag.contentHashCode()

    companion object {

        const val PLAINTEXT_SIZE = 36
        const val CIPHERTEXT_SIZE = 36
        const val TAG_SIZE = 4
        const val LOGICAL_SIZE = 40
        const val WIRE_SIZE = 54
        const val NONCE_SIZE = 7

        /**
         * Builds the 36 byte plain text.
         *
         * @param sensorR1 the first 16 bytes of the 23 byte message the **sensor** sent. It is not
         *   made by this phone, it is echoed back to prove which sensor we are talking to.
         * @param phoneR2 16 fresh random bytes from this phone.
         * @param blePin the four PIN bytes that the NFC step produced.
         */
        fun plaintext(sensorR1: ByteArray, phoneR2: ByteArray, blePin: ByteArray): ByteArray {
            require(sensorR1.size == 16) { "R1 must be 16 bytes" }
            require(phoneR2.size == 16) { "R2 must be 16 bytes" }
            require(blePin.size == 4) { "the PIN must be 4 bytes" }
            return sensorR1 + phoneR2 + blePin
        }

        /**
         * @param nonce the 7 byte tail of the 23 byte message the sensor sent first.
         * @param aes the sensor's own block maker, never the ordinary AES of the data plane.
         */
        fun encrypt(plaintext: ByteArray, nonce: ByteArray, aes: Libre3AesBlock): Libre3Phase5Challenge {
            require(plaintext.size == PLAINTEXT_SIZE) { "the Phase 5 plain text must be 36 bytes" }
            require(nonce.size == NONCE_SIZE) { "the Phase 5 nonce must be 7 bytes" }
            val sealed = Libre3AesCcm.encrypt(nonce = nonce, plaintext = plaintext, tagLength = TAG_SIZE, aes = aes)
            return Libre3Phase5Challenge(sealed.ciphertext, sealed.tag)
        }

        /** Accepts the 54 byte wire form or the 40 byte short form. */
        fun decode(raw: ByteArray): Libre3Phase5Challenge {
            if (raw.size != WIRE_SIZE && raw.size != LOGICAL_SIZE) {
                throw Libre3CryptoException("a Phase 5 message must be 54 or 40 bytes, not ${raw.size}")
            }
            return Libre3Phase5Challenge(raw.copyOfRange(0, 36), raw.copyOfRange(36, 40))
        }
    }
}

/**
 * What Phase 6 gives back: the keys of the new session.
 *
 * [phoneR2] and [sensorR1] are echoes. They must match what was sent in Phase 5, otherwise the
 * sensor on the other side is not the one this phone was talking to.
 */
data class Libre3SessionMaterial(
    val phoneR2: ByteArray,
    val sensorR1: ByteArray,
    val kEnc: ByteArray,
    val ivEnc: ByteArray,
) {

    init {
        require(phoneR2.size == 16) { "R2 must be 16 bytes" }
        require(sensorR1.size == 16) { "R1 must be 16 bytes" }
        require(kEnc.size == 16) { "the session key must be 16 bytes" }
        require(ivEnc.size == 8) { "the session nonce part must be 8 bytes" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Libre3SessionMaterial) return false
        return phoneR2.contentEquals(other.phoneR2) && sensorR1.contentEquals(other.sensorR1) &&
            kEnc.contentEquals(other.kEnc) && ivEnc.contentEquals(other.ivEnc)
    }

    override fun hashCode(): Int {
        var result = phoneR2.contentHashCode()
        result = 31 * result + sensorR1.contentHashCode()
        result = 31 * result + kEnc.contentHashCode()
        result = 31 * result + ivEnc.contentHashCode()
        return result
    }
}

/**
 * Phase 6, the answer of the sensor.
 *
 * Ported from LibreCRKit `Pairing/Phase6Response.swift` at pin `a86b92f`.
 *
 * Shape on the wire, 67 bytes: encrypted 56, tag 4, then the 7 byte nonce the sensor used.
 * The plain text is `R2 (16) || R1 (16) || kEnc (16) || ivEnc (8)`.
 */
data class Libre3Phase6Response(val ciphertext: ByteArray, val tag: ByteArray, val nonce: ByteArray) {

    init {
        require(ciphertext.size == CIPHERTEXT_SIZE) { "the Phase 6 message must be 56 bytes" }
        require(tag.size == TAG_SIZE) { "the Phase 6 tag must be 4 bytes" }
        require(nonce.size == Libre3Phase5Challenge.NONCE_SIZE) { "the Phase 6 nonce must be 7 bytes" }
    }

    /**
     * Reads the answer and checks the two echoes.
     *
     * @param expectedPhoneR2 what this phone sent as R2 in Phase 5.
     * @param expectedSensorR1 what the sensor sent as R1 before Phase 5.
     * @throws Libre3CryptoException when the tag or an echo does not match.
     */
    fun decrypt(
        aes: Libre3AesBlock,
        expectedPhoneR2: ByteArray,
        expectedSensorR1: ByteArray,
    ): Libre3SessionMaterial {
        val plaintext = Libre3AesCcm.decrypt(nonce = nonce, ciphertext = ciphertext, tag = tag, aes = aes)
        if (plaintext.size != PLAINTEXT_SIZE) {
            throw Libre3CryptoException("the Phase 6 plain text must be 56 bytes, not ${plaintext.size}")
        }
        val material = Libre3SessionMaterial(
            phoneR2 = plaintext.copyOfRange(0, 16),
            sensorR1 = plaintext.copyOfRange(16, 32),
            kEnc = plaintext.copyOfRange(32, 48),
            ivEnc = plaintext.copyOfRange(48, 56),
        )
        // The echoes prove that the sensor answered our own message and not a replayed one.
        if (!material.phoneR2.contentEquals(expectedPhoneR2)) {
            throw Libre3CryptoException("the sensor did not echo the random part this phone sent")
        }
        if (!material.sensorR1.contentEquals(expectedSensorR1)) {
            throw Libre3CryptoException("the sensor did not echo its own random part")
        }
        return material
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Libre3Phase6Response) return false
        return ciphertext.contentEquals(other.ciphertext) && tag.contentEquals(other.tag) &&
            nonce.contentEquals(other.nonce)
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + tag.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        return result
    }

    companion object {

        const val PLAINTEXT_SIZE = 56
        const val CIPHERTEXT_SIZE = 56
        const val TAG_SIZE = 4
        const val LOGICAL_SIZE = 60
        const val WIRE_SIZE = 67

        fun decode(raw: ByteArray): Libre3Phase6Response {
            if (raw.size != WIRE_SIZE) {
                throw Libre3CryptoException("a Phase 6 message must be 67 bytes, not ${raw.size}")
            }
            return Libre3Phase6Response(
                ciphertext = raw.copyOfRange(0, CIPHERTEXT_SIZE),
                tag = raw.copyOfRange(CIPHERTEXT_SIZE, LOGICAL_SIZE),
                nonce = raw.copyOfRange(LOGICAL_SIZE, WIRE_SIZE),
            )
        }
    }
}
