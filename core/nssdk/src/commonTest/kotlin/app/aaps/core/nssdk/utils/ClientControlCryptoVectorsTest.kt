package app.aaps.core.nssdk.utils

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Golden vectors for the client-control crypto.
 *
 * These are fixed inputs with their exact expected outputs, produced by the original `javax.crypto`
 * implementation. They exist so a **second** implementation - Kotlin/Native for iOS, a desktop
 * target, or a different library - can be checked byte for byte rather than by "it is also
 * HMAC-SHA256".
 *
 * They live in commonTest, so they run on every target the module builds for. The Apple provider is
 * covered by `iosSimulatorArm64Test` on macOS, which is where the Kotlin/Native path gets checked.
 *
 * That matters because the wire format is already frozen: AAPS masters and clients in the field
 * exchange these values today. The primitives themselves are standards and interoperate by
 * definition, but the **packaging** is where implementations differ and where a mismatch is silent:
 *
 * - **GCM tag placement.** JCE returns `ciphertext ‖ tag` from a single `doFinal`. Apple's CryptoKit
 *   models a sealed box with the tag held separately. Concatenate them differently and every unwrap
 *   fails - which this code deliberately reports as "wrong PIN", so it would look like user error.
 * - **IV placement.** The IV is stored beside the ciphertext, not prepended to it. An API that
 *   generates and prepends its own nonce would produce a longer blob that no deployed client reads.
 * - **Hex case.** Signatures are compared as text with a constant-time string compare. One side
 *   emitting uppercase means every signature check fails.
 * - **PIN encoding.** The JVM version passed the PIN as a char array; this one encodes UTF-8. The
 *   PIN is ASCII digits so the two agree - but a non-ASCII PIN would not, which is worth knowing
 *   before anyone widens the alphabet.
 *
 * If a value here ever needs changing, the wire format has changed and every deployed AAPS stops
 * being able to pair or verify. Treat a failure as a real incompatibility, not a stale test.
 */
class ClientControlCryptoVectorsTest {

    private fun hex(bytes: ByteArray) = ClientControlCrypto.bytesToHex(bytes)
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    /** 32 bytes, 0x00..0x1f - the HMAC secret length the protocol uses. */
    private val secret = ByteArray(32) { it.toByte() }

    /** 16 bytes, 0xa0.. - a fixed stand-in for a generated salt. */
    private val salt = ByteArray(16) { (0xa0 + it).toByte() }

    /** 12 bytes, 0xb0.. - a fixed stand-in for a generated IV. */
    private val iv = ByteArray(12) { (0xb0 + it).toByte() }

    /**
     * A second IV, for a test that encrypts with the same PIN and salt as another test.
     *
     * Reusing one IV with one key is exactly what GCM forbids, and the provider enforces it
     * ("Cannot reuse iv for GCM encryption"). The original javax implementation built a fresh Cipher
     * on every call, so the reuse went unnoticed. Production never hits this - newIv() and newSalt()
     * are generated per offer - but the tests have to hold to the same rule. Every (PIN, salt, IV)
     * combination below is used exactly once.
     */
    private val iv2 = ByteArray(12) { (0xc0 + it).toByte() }

    /** A third, for the same reason. */
    private val iv3 = ByteArray(12) { (0xd0 + it).toByte() }

    // ================================================================ HMAC-SHA256

    /**
     * The signature over a canonical envelope string. Every command and every ack rides on this.
     */
    @Test
    fun `hmac vector - canonical command string`() {
        val canonical = "clientId=abc|counter=5|type=SceneStop|timestamp=1785992179555"

        val signature = ClientControlCrypto.sign(secret, canonical)

        assertEquals("e2ac7455db1ee06145fe8432e115eaca1a4e81375703ad907251cbb9d223d216", signature)
    }

    /** An empty payload, to pin the degenerate case as well. */
    @Test
    fun `hmac vector - empty string`() {
        assertEquals("d38b42096d80f45f826b44a9d5607de72496a415d3f4a1a8c88e3bb9da8dc1cb", ClientControlCrypto.sign(secret, ""))
    }

    /** Non-ASCII input, so the UTF-8 encoding of the signed text is pinned too. */
    @Test
    fun `hmac vector - non ascii`() {
        assertEquals("fcefc8439c7d9f1d149bee6a4dfa2ec4631f8d23a8fd35bc9444c86a4a19b36d", ClientControlCrypto.sign(secret, "poznámka ěščřž"))
    }

    /** The signature is lower-case hex, 64 characters. Compared as text, so case is part of the format. */
    @Test
    fun `signatures are lower case hex of 64 characters`() {
        val signature = ClientControlCrypto.sign(secret, "anything")

        assertEquals(64, signature.length)
        assertEquals(signature.lowercase(), signature)
        assertTrue(signature.all { it.isDigit() || it in 'a'..'f' })
    }

    // ================================================================ PBKDF2 + AES-256-GCM

    /**
     * The wrapped pairing payload: PBKDF2-HMAC-SHA256 (200 000 iterations, 256 bit) over the PIN and
     * salt, then AES-256-GCM with a 12 byte IV and a 128 bit tag.
     */
    @Test
    fun `wrap vector - pairing payload`() {
        val plaintext = """{"clientId":"abc","secret":"00","expiresAt":1785992179555}""".encodeToByteArray()

        val wrapped = ClientControlPairingCrypto.wrap(plaintext, pin = "12345678", salt = salt, iv = iv)

        assertEquals(
            "f864c764382a5e82af17fccf545a14fea3b29eae0784eb2b1630ffe179e58b31c5fc8291afde74f7a0867579b244c6213a12eb9bf1382b1b6c569d2d1f74c25b52c6b253943d87026018",
            hex(wrapped)
        )
    }

    /** A short, fixed plaintext - easier to eyeball, and pins the tag placement on its own. */
    @Test
    fun `wrap vector - short plaintext`() {
        val wrapped = ClientControlPairingCrypto.wrap(bytes(1, 2, 3, 4), pin = "00000000", salt = salt, iv = iv)

        assertEquals("f0ef78af68d41f05d7bfed02b15a252856f3620c", hex(wrapped))
    }

    /**
     * The GCM tag is **appended** to the ciphertext, so the output is 16 bytes longer than the input.
     * An implementation that keeps the tag separate would produce a shorter blob and fail to unwrap.
     */
    @Test
    fun `the gcm tag is appended so the output is 16 bytes longer`() {
        val plaintext = ByteArray(40) { it.toByte() }

        // iv2, not iv: the same PIN and salt as the pairing-payload vector above, so it needs its
        // own IV. This test pins a length, not a byte sequence, so the IV choice is free.
        val wrapped = ClientControlPairingCrypto.wrap(plaintext, pin = "12345678", salt = salt, iv = iv2)

        assertEquals(plaintext.size + 16, wrapped.size)
    }

    // ================================================================ round trip

    @Test
    fun `wrap and unwrap round trip`() {
        val plaintext = "hello pairing".encodeToByteArray()

        val wrapped = ClientControlPairingCrypto.wrap(plaintext, pin = "13572468", salt = salt, iv = iv)
        val unwrapped = ClientControlPairingCrypto.unwrap(wrapped, pin = "13572468", salt = salt, iv = iv)

        // assertContentEquals, not assertEquals: ByteArray equality is reference equality.
        assertContentEquals(plaintext, unwrapped)
    }

    /** A wrong PIN gives null, not an exception - callers cannot tell it from a corrupt blob. */
    @Test
    fun `a wrong pin gives null`() {
        val wrapped = ClientControlPairingCrypto.wrap("x".encodeToByteArray(), pin = "11111111", salt = salt, iv = iv)

        assertNull(ClientControlPairingCrypto.unwrap(wrapped, pin = "22222222", salt = salt, iv = iv))
    }

    /** Tampering with a single byte must fail the auth tag. */
    @Test
    fun `a tampered blob gives null`() {
        val wrapped = ClientControlPairingCrypto.wrap("x".encodeToByteArray(), pin = "11111111", salt = salt, iv = iv3)
        wrapped[0] = (wrapped[0] + 1).toByte()

        assertNull(ClientControlPairingCrypto.unwrap(wrapped, pin = "11111111", salt = salt, iv = iv3))
    }
}
