package app.aaps.plugins.libre3.crypto

/**
 * Kind of a data packet. The three bytes go into the nonce and tell the sensor and the phone
 * which stream a packet belongs to.
 *
 * Ported from LibreCRKit `DataPlane/DataPlaneCrypto.swift` at pin `a86b92f`.
 */
enum class Libre3PacketKind(val descriptor: ByteArray) {

    KIND_0(byteArrayOf(0x00, 0x00, 0x00)),
    HANDSHAKE(byteArrayOf(0x00, 0x00, 0x0F)),
    KIND_2(byteArrayOf(0x00, 0x00, 0xF0.toByte())),
    KIND_3(byteArrayOf(0x00, 0x0F, 0x00)),
    KIND_4(byteArrayOf(0x00, 0xF0.toByte(), 0x00)),
    KIND_5(byteArrayOf(0x0F, 0x00, 0x00)),
    KIND_6(byteArrayOf(0xF0.toByte(), 0x00, 0x00)),
    PATCH_DATA(byteArrayOf(0x44, 0x00, 0x00)),
}

/** A packet that was read and checked. */
data class Libre3DecryptedPacket(val kind: Libre3PacketKind, val plaintext: ByteArray) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Libre3DecryptedPacket) return false
        return kind == other.kind && plaintext.contentEquals(other.plaintext)
    }

    override fun hashCode(): Int = 31 * kind.hashCode() + plaintext.contentHashCode()
}

/**
 * Reads and writes the glucose data packets of a live session.
 *
 * This is the **data plane**. It uses ordinary AES with the session key from the handshake. The
 * pairing messages use a different block maker and must never be sent through here.
 *
 * @param kEnc session key, 16 bytes, new after every handshake.
 * @param ivEnc second half of the nonce, 8 bytes, also new after every handshake.
 */
class Libre3DataPlaneCrypto(private val kEnc: ByteArray, private val ivEnc: ByteArray) {

    init {
        require(kEnc.size == KEY_SIZE) { "the session key must be 16 bytes" }
        require(ivEnc.size == IV_SIZE) { "the session nonce part must be 8 bytes" }
    }

    private val aes = Libre3AesCcm.standardAes(kEnc)

    /** `sequence` little endian, then the three descriptor bytes, then the stored nonce part. */
    fun nonce(sequence: Int, kind: Libre3PacketKind): ByteArray =
        byteArrayOf((sequence and 0xFF).toByte(), ((sequence shr 8) and 0xFF).toByte()) +
            kind.descriptor +
            ivEnc

    /**
     * @param payload the encrypted bytes with the four tag bytes at the end.
     * @throws Libre3CryptoException when the packet does not match its tag.
     */
    fun decrypt(payload: ByteArray, sequence: Int, kind: Libre3PacketKind): ByteArray {
        if (payload.size < TAG_SIZE) throw Libre3CryptoException("the packet is too short to hold a tag")
        val tagStart = payload.size - TAG_SIZE
        return Libre3AesCcm.decrypt(
            nonce = nonce(sequence, kind),
            ciphertext = payload.copyOfRange(0, tagStart),
            tag = payload.copyOfRange(tagStart, payload.size),
            aes = aes,
        )
    }

    /**
     * Tries every known kind and keeps the one whose tag matches.
     *
     * The tag is four bytes, so a wrong kind almost never matches by luck, and a packet that
     * matches nothing is dropped instead of being read as glucose.
     */
    fun decryptTryingAllKinds(payload: ByteArray, sequence: Int): Libre3DecryptedPacket {
        for (kind in Libre3PacketKind.entries) {
            try {
                return Libre3DecryptedPacket(kind, decrypt(payload, sequence, kind))
            } catch (_: Libre3CryptoException) {
                // Wrong kind for this packet. Try the next one.
            }
        }
        throw Libre3CryptoException("no packet kind matched the tag of this packet")
    }

    /** Only used by tests in v1. The driver never writes to the sensor. */
    fun encrypt(plaintext: ByteArray, sequence: Int, kind: Libre3PacketKind): ByteArray {
        val sealed = Libre3AesCcm.encrypt(
            nonce = nonce(sequence, kind),
            plaintext = plaintext,
            tagLength = TAG_SIZE,
            aes = aes,
        )
        return sealed.ciphertext + sealed.tag
    }

    companion object {

        const val TAG_SIZE = 4
        const val KEY_SIZE = 16
        const val IV_SIZE = 8
    }
}
