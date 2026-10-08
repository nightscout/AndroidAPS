package app.aaps.core.objects.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The values every platform must produce, so an export written on one opens on another.
 *
 * A second implementation of cryptography is only safe if something compares it with the first,
 * continuously rather than once by hand. These are that comparison. They live in `commonTest`, so
 * the same assertions run against `JvmCryptoPrimitives` and `IosCryptoPrimitives` without being
 * written twice - which is the only arrangement where the two cannot quietly drift apart.
 *
 * The expectations are fixed strings wherever they can be, not encrypt-then-decrypt round trips. A
 * round trip only proves an implementation agrees with **itself** - which is exactly what a
 * wrong-but-self-consistent implementation does, while writing files no other platform can read.
 *
 * The two hash vectors are copied from `CryptoUtilTest`, so they pin what AAPS already ships. The
 * PBKDF2 and GCM vectors come from RFC 6070 and the NIST GCM test set, so they are external to this
 * project and cannot drift with it.
 */
class CryptoPrimitivesVectorsTest {

    private val sut: CryptoPrimitives = platformCryptoPrimitives()

    private val payload = "{what:payloadYouWantToProtect}"

    @Test
    fun `sha256 matches the value AAPS already ships`() {
        assertEquals("a1aafe3ed6cc127e6d102ddbc40a205147230e9cfd178daf108c83543bbdcd13", sut.sha256(payload))
    }

    @Test
    fun `sha256 of the empty string is the published constant`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sut.sha256(""))
    }

    @Test
    fun `hmac256 matches the value AAPS already ships`() {
        assertEquals("ea2213953d0f2e55047cae2d23fb4f0de1b805d55e6271efa70d6b85fb692bea", sut.hmac256(payload, "topSikret"))
    }

    /**
     * RFC 6070 case 1, for PBKDF2 with an **HMAC-SHA1** PRF - the one this format uses. SHA-256 is
     * the obvious modern choice and would produce a different key here, which nothing downstream
     * would notice until a user could not open their own backup.
     */
    @Test
    fun `pbkdf2 uses an HMAC-SHA1 prf`() {
        val derived = sut.pbkdf2("password", "salt".encodeToByteArray(), iterations = 1, keyBits = 160)

        assertEquals("0c60c80f961f0e71f3a9b524af6012062fe037a6", derived.toHexString())
    }

    /** RFC 6070 case 2: the same inputs at a different iteration count must move. */
    @Test
    fun `pbkdf2 honours the iteration count`() {
        val derived = sut.pbkdf2("password", "salt".encodeToByteArray(), iterations = 2, keyBits = 160)

        assertEquals("ea6c014dc72d6f8ccd1ed92ace1d41f0d8de8957", derived.toHexString())
    }

    /**
     * A passphrase is whatever the user typed, so it is not going to be ASCII.
     *
     * This is a cross-platform vector rather than a smoke test. Apple's PBKDF2 takes the password as
     * a **string**, so the iOS path decodes the bytes to UTF-16 and back where the JVM passes bytes
     * straight through - a round trip that would be an obvious place to lose a character outside the
     * basic plane. The emoji is in the passphrase on purpose: it is a surrogate pair, so a mangled
     * round trip changes the derived key and this fails.
     */
    @Test
    fun `a non ascii passphrase derives the same key on every platform`() {
        val derived = sut.pbkdf2("he\u0161lo\u2192\uD83D\uDE42", ByteArray(32), iterations = 1000, keyBits = 256)

        assertEquals("bb3304d1ad7bf65933d14fc12ceda140f478e55e3da8f2c3157b2ef80cac7bb2", derived.toHexString())
    }

    /**
     * An empty salt is refused rather than quietly derived from, on both platforms. The exception
     * type differs - the JVM rejects it by argument check, Kotlin/Native by index - so this pins the
     * refusal and not the type. Nothing in the format can produce this; the guard is here so that a
     * future caller that manages to gets an error instead of a key derived from no salt at all.
     */
    @Test
    fun `an empty salt is refused`() {
        assertFails { sut.pbkdf2("password", ByteArray(0), iterations = 1000, keyBits = 256) }
    }

    /** The size AAPS actually derives: 256 bits, for AES. */
    @Test
    fun `pbkdf2 returns the requested key length`() {
        val derived = sut.pbkdf2("p", "s".encodeToByteArray(), iterations = 1000, keyBits = 256)

        assertEquals(32, derived.size)
    }

    /**
     * NIST GCM vector: an all-zero 256 bit key and 96 bit IV over empty plaintext give a known tag.
     * It also pins the layout the wire format depends on - the tag is **appended** to the ciphertext
     * rather than returned separately.
     */
    @Test
    fun `aes gcm matches a published vector`() {
        val out = sut.aesGcmEncrypt(key = ByteArray(32), iv = ByteArray(12), plaintext = ByteArray(0), tagBits = 128)

        assertEquals("530f8afbc74536b9a963b4f1c4cb738b", out.toHexString())
    }

    @Test
    fun `what was encrypted can be decrypted`() {
        val key = sut.pbkdf2("secret", "salt".encodeToByteArray(), 1000, 256)
        val iv = sut.randomBytes(12)

        val encrypted = sut.aesGcmEncrypt(key, iv, "the quick brown fox".encodeToByteArray(), 128)
        val decrypted = sut.aesGcmDecrypt(key, iv, encrypted, 128)

        assertNotNull(decrypted)
        assertEquals("the quick brown fox", decrypted.decodeToString())
    }

    /** A wrong password is the ordinary case of a typo, so it must be an answer and not an exception. */
    @Test
    fun `a wrong key does not decrypt`() {
        val iv = sut.randomBytes(12)
        val right = sut.pbkdf2("right", "salt".encodeToByteArray(), 1000, 256)
        val wrong = sut.pbkdf2("wrong", "salt".encodeToByteArray(), 1000, 256)
        val encrypted = sut.aesGcmEncrypt(right, iv, "secret".encodeToByteArray(), 128)

        assertNull(sut.aesGcmDecrypt(wrong, iv, encrypted, 128))
    }

    /** A damaged file must fail the same way, which is what the authentication tag is for. */
    @Test
    fun `tampered data does not decrypt`() {
        val key = sut.pbkdf2("k", "salt".encodeToByteArray(), 1000, 256)
        val iv = sut.randomBytes(12)
        val encrypted = sut.aesGcmEncrypt(key, iv, "secret".encodeToByteArray(), 128)
        encrypted[0] = (encrypted[0] + 1).toByte()

        assertNull(sut.aesGcmDecrypt(key, iv, encrypted, 128))
    }

    @Test
    fun `random bytes are the requested length`() {
        assertEquals(32, sut.randomBytes(32).size)
    }

    /** Two salts in a row must differ, or every export shares one. */
    @Test
    fun `random bytes do not repeat`() {
        assertNotEquals(sut.randomBytes(32).toHexString(), sut.randomBytes(32).toHexString())
    }

    /**
     * The regression guard for the whole exercise: a ciphertext produced by the implementation AAPS
     * ships today, which any replacement must still be able to read.
     *
     * Everything else here checks an implementation against a standard. This checks it against
     * **AAPS**, and it is the only assertion that would catch a change that is correct by the book
     * and still cannot open the backups people already have. It exercises the full chain at the real
     * settings - PBKDF2-HMAC-SHA1 at 50000 iterations, a 256 bit key, AES-GCM with a 128 bit tag.
     *
     * Generated once from `JvmCryptoPrimitives`, deliberately with fixed salt and IV so the answer is
     * deterministic. If this ever fails, exports written by older versions have become unreadable -
     * which is data loss, not a test failure to adjust.
     */
    @Test
    fun `a ciphertext written by the shipping implementation still decrypts`() {
        val key = sut.pbkdf2("correct horse battery staple", PINNED_SALT, iterations = 50000, keyBits = 256)

        val decrypted = sut.aesGcmDecrypt(key, PINNED_IV, PINNED_CIPHERTEXT.fromHexString(), tagBits = 128)

        assertNotNull(decrypted)
        assertEquals("""{"units":"mmol","age":"adult"}""", decrypted.decodeToString())
    }

    /** The same ciphertext must not open under a different password, or the file is not protected. */
    @Test
    fun `the pinned ciphertext does not decrypt under another password`() {
        val key = sut.pbkdf2("incorrect horse battery staple", PINNED_SALT, iterations = 50000, keyBits = 256)

        assertNull(sut.aesGcmDecrypt(key, PINNED_IV, PINNED_CIPHERTEXT.fromHexString(), tagBits = 128))
    }

    private companion object {

        private val PINNED_SALT = ByteArray(32) { it.toByte() }
        private val PINNED_IV = ByteArray(12) { (it * 7).toByte() }
        private const val PINNED_CIPHERTEXT =
            "7e7e2d28fc786cf57dd252706210013c787ba5062523a906a311a90a2cbf2dee4db43b55b895a126dfc1ec1e25d4"
    }
}

/** Hex without a JVM helper, so these expectations read the same on every platform. */
internal fun ByteArray.toHexString(): String =
    joinToString("") { b -> ((b.toInt() and 0xff) + 0x100).toString(16).substring(1) }

/** The inverse, so a pinned expectation can be written as the hex it is usually quoted in. */
internal fun String.fromHexString(): ByteArray =
    chunked(2).map { it.toInt(16).toByte() }.toByteArray()
