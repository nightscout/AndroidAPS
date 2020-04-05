package info.nightscout.androidaps.utils

import org.spongycastle.util.encoders.Base64
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val HEX_CHARS = "0123456789abcdef"
private val HEX_CHARS_ARRAY = "0123456789abcdef".toCharArray()

fun String.hexStringToByteArray() : ByteArray {

    val upperCased = this.toLowerCase()
    val result = ByteArray(length / 2)
    for (i in 0 until length step 2) {
        val firstIndex = HEX_CHARS.indexOf(upperCased[i]);
        val secondIndex = HEX_CHARS.indexOf(upperCased[i + 1]);

        val octet = firstIndex.shl(4).or(secondIndex)
        result.set(i.shr(1), octet.toByte())
    }

    return result
}

fun ByteArray.toHex() : String{
    val result = StringBuffer()

    forEach {
        val octet = it.toInt()
        val firstIndex = (octet and 0xF0).ushr(4)
        val secondIndex = octet and 0x0F
        result.append(HEX_CHARS_ARRAY[firstIndex])
        result.append(HEX_CHARS_ARRAY[secondIndex])
    }

    return result.toString()
}

object CryptoUtil {

    private const val IV_LENGTH_BYTE = 12
    private const val TAG_LENGTH_BIT = 128
    private const val AES_KEY_SIZE_BIT = 256
    private const val PBKDF2_ITERATIONS = 50000 // check delays it cause on real device
    private const val SALT_SIZE_BYTE = 32

    private val secureRandom: SecureRandom = SecureRandom()

    fun sha256(source: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashRaw = digest.digest(source.toByteArray())
        return hashRaw.toHex()
    }

    fun hmac256(str: String, secret: String): String? {
        val sha256_HMAC = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        sha256_HMAC.init(secretKey)
        return sha256_HMAC.doFinal(str.toByteArray()).toHex()
    }

    private fun prepCipherKey(passPhrase: String, salt:ByteArray, iterationCount:Int = PBKDF2_ITERATIONS, keyStrength:Int = AES_KEY_SIZE_BIT): SecretKeySpec {
        val factory: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2withHmacSHA1")
        val spec: KeySpec = PBEKeySpec(passPhrase.toCharArray(), salt, iterationCount, keyStrength)
        val tmp: SecretKey = factory.generateSecret(spec)
        return SecretKeySpec(tmp.getEncoded(), "AES")
    }

    fun mineSalt(len :Int = SALT_SIZE_BYTE): ByteArray {
        val salt = ByteArray(len)
        secureRandom.nextBytes(salt)
        return salt
    }

    fun encrypt(passPhrase: String, salt:ByteArray, rawData: String ): String? {
        val iv: ByteArray?
        val encrypted: ByteArray?
        return try {
            iv = ByteArray(IV_LENGTH_BYTE)
            secureRandom.nextBytes(iv)
            val cipherEnc: Cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipherEnc.init(Cipher.ENCRYPT_MODE, prepCipherKey(passPhrase, salt), GCMParameterSpec(TAG_LENGTH_BIT, iv))
            encrypted = cipherEnc.doFinal(rawData.toByteArray())
            val byteBuffer: ByteBuffer = ByteBuffer.allocate(1 + iv.size + encrypted.size)
            byteBuffer.put(iv.size.toByte())
            byteBuffer.put(iv)
            byteBuffer.put(encrypted)
            String(Base64.encode(byteBuffer.array()))
        } catch (e: Exception) {
           null
        }
    }

    fun decrypt(passPhrase: String, salt:ByteArray, encryptedData: String): String? {
        val iv: ByteArray?
        val encrypted: ByteArray?
        return try {
            val byteBuffer = ByteBuffer.wrap(Base64.decode(encryptedData))
            val ivLength = byteBuffer.get().toInt()
            iv = ByteArray(ivLength)
            byteBuffer[iv]
            encrypted = ByteArray(byteBuffer.remaining())
            byteBuffer[encrypted]
            val cipherDec: Cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipherDec.init(Cipher.DECRYPT_MODE, prepCipherKey(passPhrase, salt), GCMParameterSpec(TAG_LENGTH_BIT, iv))
            val dec = cipherDec.doFinal(encrypted)
            String(dec)
        } catch (e: Exception) {
            null
        }
    }

    fun checkPassword(password: String, referenceHash: String): Boolean {
        return if (referenceHash.startsWith("hmac:")) {
            val hashSegments = referenceHash.split(":")
            if (hashSegments.size != 3)
                return false
            return hmac256(password, hashSegments[1]) == hashSegments[2]
        } else {
            password == referenceHash
        }
    }

    fun hashPassword(password: String): String {
        return if (!password.startsWith("hmac:")) {
            val salt = mineSalt().toHex()
            return "hmac:${salt}:${hmac256(password, salt)}"
        } else {
            password
        }
    }

}