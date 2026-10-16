package app.aaps.core.objects.crypto

/**
 * The cryptographic operations AAPS needs, from whatever the platform provides.
 *
 * `CryptoUtil` is `jvmSharedMain` because it is built on `javax.crypto`, so the encrypted settings
 * export exists on Android and the desktop and not on iOS - Kotlin/Native has no JVM crypto. This is
 * the seam that lets the *format* be written once: the salts, the iteration count, the wire layout
 * and the hash checks are ordinary Kotlin, and only these five operations are platform work.
 *
 * ## Every parameter here is load-bearing
 *
 * An export written on one platform has to open on another, so these are not choices an
 * implementation may make differently. In particular the key derivation uses **HMAC-SHA1** as its
 * PRF, which is not the obvious modern choice and is easy to "improve" by accident - doing so
 * produces a key that is wrong in a way nothing detects until a user cannot open their own backup.
 *
 * The constants live with the format that owns them; an implementation is told, never assumes.
 *
 * ## What an implementation must not do
 *
 * Invent. If a platform cannot provide one of these from a vetted library, the answer is to say so
 * rather than hand-roll a cipher: this protects a file holding someone's therapy settings.
 */
interface CryptoPrimitives {

    /** SHA-256 of [source]'s UTF-8 bytes, lower-case hex. */
    fun sha256(source: String): String

    /**
     * SHA-256 of [source], as bytes.
     *
     * The same digest [sha256] returns, before it is written as hex. PKCE needs the raw bytes - its
     * code challenge is base64url of the digest, and going through hex and back would be a longer
     * way to the same place with one more thing to get wrong.
     */
    fun sha256Bytes(source: ByteArray): ByteArray

    /** HMAC-SHA256 of [message] under [secret], both UTF-8, lower-case hex. */
    fun hmac256(message: String, secret: String): String

    /**
     * PBKDF2 with an **HMAC-SHA1** PRF - the JVM's `PBKDF2withHmacSHA1`.
     *
     * @param keyBits length of the derived key in bits, not bytes.
     */
    fun pbkdf2(passphrase: String, salt: ByteArray, iterations: Int, keyBits: Int): ByteArray

    /**
     * AES-GCM encryption. Returns ciphertext with the authentication tag appended, as the JVM does.
     *
     * @param tagBits authentication tag length in bits.
     */
    fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, tagBits: Int): ByteArray

    /** The inverse of [aesGcmEncrypt]. Null when the tag does not verify - a wrong password, or a damaged file. */
    fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertextAndTag: ByteArray, tagBits: Int): ByteArray?

    /** Cryptographically secure random bytes, for salts and IVs. */
    fun randomBytes(length: Int): ByteArray
}
