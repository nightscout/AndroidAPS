package app.aaps.pump.eopatch.core.ble

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Covers [Cipher], the AES/CTR encryption on the link to the patch.
 *
 * Two things here are easy to get wrong and impossible to notice by looking: the initialisation
 * vector is built from the packet sequence number, so encrypt and decrypt must agree on it, and the
 * first two bytes are a header (a sequence/CRC mix with the top bit marking the packet as
 * encrypted) rather than payload. If any of that slipped, packets would still be produced - they
 * would simply be rubbish to the patch, or go out in clear when they were meant to be encrypted.
 */
class CipherTest {

    /** AES needs 16, 24 or 32 bytes. */
    private val key = ByteArray(16) { (it + 1).toByte() }

    /** A packet long enough to have a payload after the two header bytes. */
    private fun packet() = ByteArray(20) { (it * 3 + 7).toByte() }

    private fun readyCipher(seq: Int = 5) = Cipher().apply {
        updateEncryptionParam(key)
        setSeq(seq)
    }

    private fun Cipher.encryptNow(bytes: ByteArray, func: PatchFunc = PatchFunc.SET_KEY) =
        encrypt(bytes, func).blockingGet()

    private fun Cipher.decryptNow(bytes: ByteArray, func: PatchFunc = PatchFunc.SET_KEY) =
        decrypt(bytes, func).blockingGet()

    // ---- the round trip ----

    /** What is encrypted with a sequence must come back when decrypted with the same one. */
    @Test
    fun aPacketDecryptsBackToItsPayload() {
        val original = packet()
        val sut = readyCipher()

        val encrypted = sut.encryptNow(original.copyOf())
        val decrypted = sut.decryptNow(encrypted)

        // Bytes 0 and 1 are the header, so only the payload from CRC_START_INDEX is payload.
        assertThat(decrypted.copyOfRange(ICipher.CRC_START_INDEX, decrypted.size))
            .isEqualTo(original.copyOfRange(ICipher.CRC_START_INDEX, original.size))
    }

    @Test
    fun encryptingActuallyChangesThePayload() {
        val original = packet()

        val encrypted = readyCipher().encryptNow(original.copyOf())

        assertThat(encrypted.copyOfRange(ICipher.CRC_START_INDEX, encrypted.size))
            .isNotEqualTo(original.copyOfRange(ICipher.CRC_START_INDEX, original.size))
    }

    /** The top bit of the first byte is what tells the patch the packet is encrypted. */
    @Test
    fun anEncryptedPacketIsMarkedAsEncrypted() {
        val encrypted = readyCipher().encryptNow(packet())

        assertThat(encrypted[0].toInt() and 0x80).isNotEqualTo(0)
    }

    /** The IV comes from the sequence, so the same payload under a different one must differ. */
    @Test
    fun theSamepacketUnderADifferentSequenceEncryptsDifferently() {
        val first = readyCipher(seq = 5).encryptNow(packet())
        val second = readyCipher(seq = 6).encryptNow(packet())

        assertThat(first).isNotEqualTo(second)
    }

    /** Decrypting with the wrong sequence must not quietly give back the right payload. */
    @Test
    fun theWrongSequenceDoesNotDecryptThePacket() {
        val original = packet()
        val encrypted = readyCipher(seq = 5).encryptNow(original.copyOf())

        val decrypted = readyCipher(seq = 9).decryptNow(encrypted)

        assertThat(decrypted.copyOfRange(ICipher.CRC_START_INDEX, decrypted.size))
            .isNotEqualTo(original.copyOfRange(ICipher.CRC_START_INDEX, original.size))
    }

    // ---- when encryption does not apply ----

    @Test
    fun withoutAKeyThePacketIsLeftAlone() {
        val original = packet()
        val sut = Cipher().apply { setSeq(5) }   // no key

        assertThat(sut.encryptNow(original.copyOf())).isEqualTo(original)
    }

    @Test
    fun withoutASequenceThePacketIsLeftAlone() {
        val original = packet()
        val sut = Cipher().apply { updateEncryptionParam(key) }   // seq stays -1

        assertThat(sut.encryptNow(original.copyOf())).isEqualTo(original)
    }

    /** UPDATE_CONNECTION is sent in clear by design. */
    @Test
    fun aFunctionMarkedNoCryptIsLeftAlone() {
        val original = packet()

        val result = readyCipher().encryptNow(original.copyOf(), PatchFunc.UPDATE_CONNECTION)

        assertThat(result).isEqualTo(original)
        assertThat(PatchFunc.UPDATE_CONNECTION.noCrypt).isTrue()
    }

    @Test
    fun aPacketThatIsNotMarkedEncryptedIsNotDecrypted() {
        val plain = packet().also { it[0] = 0x01 }   // top bit clear

        val result = readyCipher().decryptNow(plain.copyOf())

        assertThat(result).isEqualTo(plain)
    }

    // ---- key handling ----

    @Test
    fun anEmptyKeyClearsTheOneHeldAndStopsEncrypting() {
        val original = packet()
        val sut = readyCipher()

        sut.updateEncryptionParam(ByteArray(0))

        assertThat(sut.encryptNow(original.copyOf())).isEqualTo(original)
    }

    @Test
    fun settingTheSameKeyAgainKeepsEncryptingTheSameWay() {
        val sut = readyCipher()
        val first = sut.encryptNow(packet())

        sut.updateEncryptionParam(key)
        val second = sut.encryptNow(packet())

        assertThat(second).isEqualTo(first)
    }

    // ---- the sequence counter ----

    @Test
    fun theSequenceIsUnsetUntilItIsGiven() {
        assertThat(Cipher().getSequence()).isEqualTo(-1)
    }

    @Test
    fun sendingAnEncryptedPacketMovesTheSequenceOn() {
        val sut = readyCipher(seq = 5)
        val encrypted = sut.encryptNow(packet())

        sut.onPacketSent(encrypted, PatchFunc.SET_KEY)

        assertThat(sut.getSequence()).isEqualTo(6)
    }

    /** A packet that went out in clear does not consume a sequence number. */
    @Test
    fun sendingAPlainPacketLeavesTheSequenceAlone() {
        val sut = readyCipher(seq = 5)
        val plain = packet().also { it[0] = 0x01 }

        sut.onPacketSent(plain, PatchFunc.UPDATE_CONNECTION)

        assertThat(sut.getSequence()).isEqualTo(5)
    }
}
