package app.aaps.plugins.libre3.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Known answer tests for the parts of the glucose data plane that every platform now shares.
 *
 * They live in `commonTest` on purpose, so they run on every target the module has. The block
 * maker is the one piece each platform supplies for itself, and these vectors are what prove that
 * a platform's answer is the right one. Without them a wrong block would look exactly like a
 * working driver until a reading came out wrong.
 */
class Libre3SharedCryptoTest {

    /** FIPS-197 appendix B: the AES-128 example block, key and plaintext from the standard. */
    @Test
    fun standardAesMatchesTheFips197Block() {
        val key = hex("2b7e151628aed2a6abf7158809cf4f3c")
        val plaintext = hex("3243f6a8885a308d313198a2e0370734")
        val block = Libre3AesCcm.standardAes(key)
        assertEquals("3925841d02dc09fbdc118597196a0b32", block.encryptBlock(plaintext).toLowerHex())
    }

    /** FIPS-197 appendix C.1: the all zero to fifteen block, a second independent vector. */
    @Test
    fun standardAesMatchesTheFips197AppendixCBlock() {
        val key = hex("000102030405060708090a0b0c0d0e0f")
        val plaintext = hex("00112233445566778899aabbccddeeff")
        val block = Libre3AesCcm.standardAes(key)
        assertEquals("69c4e0d86a7b0430d8cdb78070b4c55a", block.encryptBlock(plaintext).toLowerHex())
    }

    @Test
    fun standardAesRefusesAKeyThatIsNotSixteenBytes() {
        assertFailsWith<IllegalArgumentException> { Libre3AesCcm.standardAes(ByteArray(15)) }
        assertFailsWith<IllegalArgumentException> { Libre3AesCcm.standardAes(ByteArray(32)) }
    }

    /** RFC 3610 packet vector number one, the vector the upstream project checks itself against. */
    @Test
    fun ccmMatchesRfc3610PacketVectorOne() {
        val key = hex("c0c1c2c3c4c5c6c7c8c9cacbcccdcecf")
        val nonce = hex("00000003020100a0a1a2a3a4a5")
        val aad = hex("0001020304050607")
        val plaintext = hex("08090a0b0c0d0e0f101112131415161718191a1b1c1d1e")
        val sealed = Libre3AesCcm.encrypt(
            nonce = nonce,
            plaintext = plaintext,
            aad = aad,
            tagLength = 8,
            aes = Libre3AesCcm.standardAes(key),
        )
        assertEquals("588c979a61c663d2f066d0c2c0f989806d5f6b61dac384", sealed.ciphertext.toLowerHex())
        assertEquals("17e8d12cfdf926e0", sealed.tag.toLowerHex())
    }

    @Test
    fun ccmReadsBackWhatItWrote() {
        val key = hex("c0c1c2c3c4c5c6c7c8c9cacbcccdcecf")
        val nonce = hex("00000003020100a0a1a2a3a4a5")
        val aad = hex("0001020304050607")
        val plaintext = hex("08090a0b0c0d0e0f101112131415161718191a1b1c1d1e")
        val aes = Libre3AesCcm.standardAes(key)
        val sealed = Libre3AesCcm.encrypt(nonce, plaintext, aad, tagLength = 8, aes = aes)
        val read = Libre3AesCcm.decrypt(nonce, sealed.ciphertext, sealed.tag, aad, aes)
        assertEquals(plaintext.toLowerHex(), read.toLowerHex())
    }

    @Test
    fun ccmRefusesAMessageWhoseTagWasChanged() {
        val key = hex("c0c1c2c3c4c5c6c7c8c9cacbcccdcecf")
        val nonce = hex("00000003020100a0a1a2a3a4a5")
        val plaintext = hex("08090a0b0c0d0e0f")
        val aes = Libre3AesCcm.standardAes(key)
        val sealed = Libre3AesCcm.encrypt(nonce, plaintext, tagLength = 8, aes = aes)
        val brokenTag = sealed.tag.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        assertFailsWith<Libre3CryptoException> {
            Libre3AesCcm.decrypt(nonce, sealed.ciphertext, brokenTag, aes = aes)
        }
    }

    /** The data plane wraps CCM with its own nonce and a four byte tag. */
    @Test
    fun dataPlaneReadsBackItsOwnPacketAndFindsItsKind() {
        val crypto = Libre3DataPlaneCrypto(kEnc = ByteArray(16) { it.toByte() }, ivEnc = ByteArray(8) { 0x5A })
        val plaintext = ByteArray(20) { (it * 7).toByte() }
        val packet = crypto.encrypt(plaintext, sequence = 42, kind = Libre3PacketKind.PATCH_DATA)
        val read = crypto.decryptTryingAllKinds(packet, sequence = 42)
        assertEquals(Libre3PacketKind.PATCH_DATA, read.kind)
        assertEquals(plaintext.toLowerHex(), read.plaintext.toLowerHex())
    }

    @Test
    fun dataPlaneRefusesAPacketThatMatchesNoKind() {
        val crypto = Libre3DataPlaneCrypto(kEnc = ByteArray(16) { it.toByte() }, ivEnc = ByteArray(8) { 0x5A })
        val packet = crypto.encrypt(ByteArray(8), sequence = 1, kind = Libre3PacketKind.KIND_0)
        assertFailsWith<Libre3CryptoException> { crypto.decryptTryingAllKinds(packet, sequence = 2) }
    }

    /** The platform's own secure random has to be there and has to move. */
    @Test
    fun secureRandomFillsTheWholeArrayAndDoesNotRepeatItself() {
        val random = Libre3RandomSource.secure()
        val first = ByteArray(32).also { random.nextBytes(it) }
        val second = ByteArray(32).also { random.nextBytes(it) }
        assertTrue(first.any { it != 0.toByte() }, "a secure random that returns only zeros is not one")
        assertTrue(!first.contentEquals(second), "two draws of 32 bytes must not be the same")
    }

    private fun hex(text: String) = ByteArray(text.length / 2) {
        text.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }

    private fun ByteArray.toLowerHex() = joinToString("") {
        val v = it.toInt() and 0xFF
        "0123456789abcdef"[v shr 4].toString() + "0123456789abcdef"[v and 0x0F]
    }
}
