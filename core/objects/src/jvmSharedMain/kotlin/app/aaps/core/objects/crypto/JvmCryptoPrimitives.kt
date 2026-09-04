package app.aaps.core.objects.crypto

import app.aaps.core.utils.toHex
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.SecretKeyFactory

/**
 * [CryptoPrimitives] from the JVM, which is where AAPS has always got them.
 *
 * Lifted out of `CryptoUtil` unchanged rather than rewritten: this is the behaviour every existing
 * export was written with, so it is the reference the other platforms must match, not a second
 * opinion about how it should be done.
 */
class JvmCryptoPrimitives : CryptoPrimitives {

    private val secureRandom = SecureRandom()

    override fun sha256(source: String): String =
        MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).toHex()

    override fun hmac256(message: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(message.toByteArray()).toHex()
    }

    override fun pbkdf2(passphrase: String, salt: ByteArray, iterations: Int, keyBits: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2withHmacSHA1")
            .generateSecret(PBEKeySpec(passphrase.toCharArray(), salt, iterations, keyBits))
            .encoded

    override fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, tagBits: Int): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(tagBits, iv))
        }.doFinal(plaintext)

    override fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertextAndTag: ByteArray, tagBits: Int): ByteArray? =
        try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(tagBits, iv))
            }.doFinal(ciphertextAndTag)
        } catch (_: Exception) {
            // A failed tag is the expected outcome of a wrong password, not an error to propagate.
            null
        }

    override fun randomBytes(length: Int): ByteArray = ByteArray(length).also { secureRandom.nextBytes(it) }
}
