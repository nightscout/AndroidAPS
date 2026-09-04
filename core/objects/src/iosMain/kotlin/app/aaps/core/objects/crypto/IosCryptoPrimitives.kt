package app.aaps.core.objects.crypto

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA1
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom

/**
 * [CryptoPrimitives] on Kotlin/Native, through the same library the pairing and keychain code uses.
 *
 * The library is a thin wrapper: on iOS these calls end up in CommonCrypto and CryptoKit, which are
 * Apple's own vetted implementations. Nothing here implements an algorithm, which is the point - a
 * hand-rolled cipher in the path that protects someone's therapy settings would be the wrong trade
 * even if it were correct today.
 *
 * The one call that deserves a note is [pbkdf2]. It passes [SHA1] deliberately: the exported file
 * format derives its key with an HMAC-SHA1 PRF, so SHA-256 here would produce a different key and
 * every backup written on Android would stop opening. `CryptoPrimitivesVectorsTest` pins that with
 * RFC 6070 vectors and with a ciphertext taken from the shipping Android build.
 */
@OptIn(DelicateCryptographyApi::class)
class IosCryptoPrimitives : CryptoPrimitives {

    private val provider = CryptographyProvider.Default
    private val sha by lazy { provider.get(SHA256).hasher() }
    private val hmac by lazy { provider.get(HMAC) }
    private val aes by lazy { provider.get(AES.GCM) }

    override fun sha256(source: String): String = sha.hashBlocking(source.encodeToByteArray()).toHex()

    override fun hmac256(message: String, secret: String): String {
        val key = hmac.keyDecoder(SHA256).decodeFromByteArrayBlocking(HMAC.Key.Format.RAW, secret.encodeToByteArray())
        return key.signatureGenerator().generateSignatureBlocking(message.encodeToByteArray()).toHex()
    }

    override fun pbkdf2(passphrase: String, salt: ByteArray, iterations: Int, keyBits: Int): ByteArray =
        provider.get(PBKDF2)
            .secretDerivation(digest = SHA1, iterations = iterations, outputSize = keyBits.bits, salt = salt)
            .deriveSecretToByteArrayBlocking(passphrase.encodeToByteArray())

    override fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, tagBits: Int): ByteArray =
        keyFrom(key).cipher(tagBits.bits).encryptWithIvBlocking(iv, plaintext)

    // A wrong password and a damaged file both land here, and both are ordinary things a user does.
    // GCM reports them by failing the tag check, which the library raises as an exception; the
    // format wants an answer instead, so it becomes null.
    override fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertextAndTag: ByteArray, tagBits: Int): ByteArray? =
        try {
            keyFrom(key).cipher(tagBits.bits).decryptWithIvBlocking(iv, ciphertextAndTag)
        } catch (_: Throwable) {
            null
        }

    // CryptographyRandom, not kotlin.random.Random: the latter is a plain PRNG seeded from the
    // clock, so every salt and IV would be predictable to anyone who could guess when it was made.
    override fun randomBytes(length: Int): ByteArray = CryptographyRandom.nextBytes(length)

    private fun keyFrom(key: ByteArray): AES.GCM.Key =
        aes.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)

    private fun ByteArray.toHex(): String =
        joinToString("") { b -> HEX[(b.toInt() shr 4) and 0xF].toString() + HEX[b.toInt() and 0xF] }

    private companion object {

        private const val HEX = "0123456789abcdef"
    }
}
