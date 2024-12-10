package app.aaps.pump.equil.manager

import android.util.Log
import app.aaps.core.interfaces.logging.LTag
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object AESUtil {

    private fun generateAESKeyFromPassword(password: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val inputBytes = password.toByteArray(StandardCharsets.UTF_8)
        val hashBytes = digest.digest(inputBytes)
        val extractedBytes = ByteArray(16)
        System.arraycopy(hashBytes, 2, extractedBytes, 0, 16)
        return extractedBytes
    }

    fun getEquilPassWord(password: String): ByteArray {
        val plaintextDefault = "Equil" //
        val defaultKey = generateAESKeyFromPassword(plaintextDefault)
        val aesKey = Utils.concat(defaultKey, generateAESKeyFromPassword(password))
        Log.e(LTag.PUMPCOMM.toString(), Utils.bytesToHex(aesKey) + "===" + aesKey.size)
        return aesKey
    }

    fun generateRandomIV(length: Int): ByteArray {
        val secureRandom = SecureRandom()
        val ivBytes = ByteArray(length)
        secureRandom.nextBytes(ivBytes)
        return ivBytes
    }

    fun aesEncrypt(pwd: ByteArray?, data: ByteArray?): EquilCmdModel {
        val iv = generateRandomIV(12)
        val key: SecretKey = SecretKeySpec(pwd, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmParameterSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmParameterSpec)
        val ciphertext = cipher.doFinal(data)
        val authenticationTag = ciphertext.copyOfRange(ciphertext.size - 16, ciphertext.size)
        val encryptedData = ciphertext.copyOfRange(0, ciphertext.size - 16)
        val equilCmdModel = EquilCmdModel()
        equilCmdModel.tag = Utils.bytesToHex(authenticationTag)
        equilCmdModel.iv = Utils.bytesToHex(iv)
        equilCmdModel.ciphertext = Utils.bytesToHex(encryptedData)
        return equilCmdModel
    }

    fun decrypt(equilCmdModel: EquilCmdModel, keyBytes: ByteArray): String {
        val iv = equilCmdModel.iv ?: throw IllegalStateException()
        val ciphertext = equilCmdModel.ciphertext ?: throw IllegalStateException()
        val authenticationTag = equilCmdModel.tag ?: throw IllegalStateException()
        val ivBytes = Utils.hexStringToBytes(iv)
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val decodedCiphertext = Utils.hexStringToBytes(ciphertext)
        val decodedAuthenticationTag = Utils.hexStringToBytes(authenticationTag)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val parameterSpec = GCMParameterSpec(128, ivBytes)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec)
        cipher.update(decodedCiphertext)
        val decrypted = cipher.doFinal(decodedAuthenticationTag)
        val content = Utils.bytesToHex(decrypted)
        return content
    }
}