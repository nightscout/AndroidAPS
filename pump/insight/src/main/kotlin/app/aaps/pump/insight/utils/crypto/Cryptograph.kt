package app.aaps.pump.insight.utils.crypto

import app.aaps.pump.insight.utils.ByteBuf
import org.spongycastle.crypto.Digest
import org.spongycastle.crypto.InvalidCipherTextException
import org.spongycastle.crypto.digests.MD5Digest
import org.spongycastle.crypto.digests.SHA1Digest
import org.spongycastle.crypto.encodings.OAEPEncoding
import org.spongycastle.crypto.engines.RSAEngine
import org.spongycastle.crypto.engines.TwofishEngine
import org.spongycastle.crypto.generators.RSAKeyPairGenerator
import org.spongycastle.crypto.macs.HMac
import org.spongycastle.crypto.modes.CBCBlockCipher
import org.spongycastle.crypto.params.*
import java.math.BigInteger
import java.security.SecureRandom
import kotlin.experimental.xor

object Cryptograph {

    private const val keySeed = "master secret"
    private const val verificationSeed = "finished"
    private fun getHmac(secret: ByteArray, data: ByteArray, algorithm: Digest): ByteArray {
        val hmac = HMac(algorithm)
        hmac.init(KeyParameter(secret))
        val result = ByteArray(hmac.macSize)
        hmac.update(data, 0, data.size)
        hmac.doFinal(result, 0)
        return result
    }

    private fun getMultiHmac(secret: ByteArray, data: ByteArray, bytes: Int, algorithm: Digest): ByteArray {
        var nuData = data
        val output = ByteArray(bytes)
        var size = 0
        while (size < bytes) {
            nuData = getHmac(secret, nuData, algorithm)
            val preOutput = getHmac(secret, combine(nuData, data), algorithm)
            System.arraycopy(preOutput, 0, output, size, Math.min(bytes - size, preOutput.size))
            size += preOutput.size
        }
        return output
    }

    private fun sha1MultiHmac(secret: ByteArray, data: ByteArray, bytes: Int): ByteArray {
        return getMultiHmac(secret, data, bytes, SHA1Digest())
    }

    private fun md5MultiHmac(secret: ByteArray, data: ByteArray, bytes: Int): ByteArray {
        return getMultiHmac(secret, data, bytes, MD5Digest())
    }

    @JvmStatic fun getServicePasswordHash(servicePassword: String, salt: ByteArray): ByteArray {
        return multiHashXOR(servicePassword.toByteArray(), combine("service pwd".toByteArray(), salt), 16)
    }

    private fun byteArrayXOR(array1: ByteArray, array2: ByteArray): ByteArray {
        val length = Math.min(array1.size, array2.size)
        val xor = ByteArray(length)
        for (i in 0 until length) {
            xor[i] = (array1[i] xor array2[i])
        }
        return xor
    }

    private fun multiHashXOR(secret: ByteArray, seed: ByteArray, bytes: Int): ByteArray {
        val array1 = ByteArray(secret.size / 2)
        val array2 = ByteArray(array1.size)
        System.arraycopy(secret, 0, array1, 0, array1.size)
        System.arraycopy(secret, array1.size, array2, 0, array2.size)
        val md5 = md5MultiHmac(array1, seed, bytes)
        val sha1 = sha1MultiHmac(array2, seed, bytes)
        return byteArrayXOR(md5, sha1)
    }

    @JvmStatic
    fun deriveKeys(verificationSeed: ByteArray, secret: ByteArray, random: ByteArray, peerRandom: ByteArray): DerivedKeys {
        val result = multiHashXOR(secret, combine(combine(keySeed.toByteArray(), random), peerRandom), 32)
        val derivedKeys = DerivedKeys()
        derivedKeys.incomingKey = ByteArray(result.size / 2)
        derivedKeys.outgoingKey = ByteArray(derivedKeys.incomingKey.size)
        System.arraycopy(result, 0, derivedKeys.incomingKey, 0, derivedKeys.incomingKey.size)
        System.arraycopy(result, derivedKeys.incomingKey.size, derivedKeys.outgoingKey, 0, derivedKeys.outgoingKey.size)
        derivedKeys.verificationString = calculateVerificationString(verificationSeed, result)
        return derivedKeys
    }

    private fun calculateVerificationString(verificationSeed: ByteArray, key: ByteArray): String {
        val verificationData = multiHashXOR(key, combine(Cryptograph.verificationSeed.toByteArray(), verificationSeed), 8)
        var value: Long = 0
        for (i in 7 downTo 0) {
            var byteValue = verificationData[i].toLong()
            if (byteValue < 0) byteValue += 256
            value = value or (byteValue shl i * 8)
        }
        val stringBuilder = StringBuilder()
        for (index in 0..9) {
            if (index == 3 || index == 6) stringBuilder.append(" ")
            stringBuilder.append(VerificationString.TABLE[value.toInt() and 63])
            value = value shr 6
        }
        return stringBuilder.toString()
    }

    @Throws(InvalidCipherTextException::class)
    private fun processRSA(key: AsymmetricKeyParameter, data: ByteArray, encrypt: Boolean): ByteArray {
        val cipher = OAEPEncoding(RSAEngine())
        cipher.init(encrypt, key)
        return cipher.processBlock(data, 0, data.size)
    }

    @JvmStatic @Throws(InvalidCipherTextException::class)
    fun decryptRSA(key: RSAPrivateCrtKeyParameters, data: ByteArray): ByteArray {
        return processRSA(key, data, false)
    }

    @JvmStatic fun generateRSAKey(): KeyPair {
        val generator = RSAKeyPairGenerator()
        generator.init(RSAKeyGenerationParameters(BigInteger.valueOf(65537), SecureRandom(), 2048, 8))
        val ackp = generator.generateKeyPair()
        val keyPair = KeyPair()
        keyPair.privateKey = (ackp.private as RSAPrivateCrtKeyParameters)
        keyPair.publicKey = (ackp.public as RSAKeyParameters)
        return keyPair
    }

    @JvmStatic fun combine(array1: ByteArray, array2: ByteArray): ByteArray {
        val combined = ByteArray(array1.size + array2.size)
        System.arraycopy(array1, 0, combined, 0, array1.size)
        System.arraycopy(array2, 0, combined, array1.size, array2.size)
        return combined
    }

    private fun produceCCMPrimitive(headerByte: Byte, nonce: ByteArray, number: Short): ByteArray {
        val byteBuf = ByteBuf(16)
        byteBuf.putByte(headerByte)
        byteBuf.putBytes(nonce)
        byteBuf.putShort(number)
        return byteBuf.bytes
    }

    private fun produceIV(nonce: ByteArray, payloadSize: Short): ByteArray {
        return produceCCMPrimitive(0x59.toByte(), nonce, payloadSize)
    }

    private fun produceCTRBlock(nonce: ByteArray, counter: Short): ByteArray {
        return produceCCMPrimitive(0x01.toByte(), nonce, counter)
    }

    private fun blockCipherZeroPad(input: ByteArray): ByteArray {
        val modulus = input.size % 16
        if (modulus == 0) return input
        val append = ByteArray(16 - modulus)
        for (i in 0 until 16 - modulus) {
            append[i] = 0x00
        }
        return combine(input, append)
    }

    fun encryptDataCTR(data: ByteArray, key: ByteArray?, nonce: ByteArray): ByteArray {
        val padded = blockCipherZeroPad(data)
        val length = padded.size shr 4
        val result = ByteArray(length * 16)
        val engine = TwofishEngine()
        engine.init(true, KeyParameter(key))
        for (i in 0 until length) {
            engine.processBlock(produceCTRBlock(nonce, (i + 1).toShort()), 0, result, i * 16)
        }
        val xor = byteArrayXOR(padded, result)
        val copy = ByteArray(Math.min(data.size, xor.size))
        System.arraycopy(xor, 0, copy, 0, copy.size)
        return copy
    }

    private fun processHeader(header: ByteArray): ByteArray {
        val byteBuf: ByteBuf = ByteBuf(2 + header.size)
        byteBuf.putShort(header.size.toShort())
        byteBuf.putBytes(header)
        return byteBuf.bytes
    }

    fun produceCCMTag(nonce: ByteArray, payload: ByteArray, header: ByteArray, key: ByteArray?): ByteArray {
        val engine = TwofishEngine()
        engine.init(true, KeyParameter(key))
        val initializationVector = ByteArray(engine.blockSize)
        engine.processBlock(produceIV(nonce, payload.size.toShort()), 0, initializationVector, 0)
        val cbc = CBCBlockCipher(TwofishEngine())
        cbc.init(true, ParametersWithIV(KeyParameter(key), initializationVector))
        val processedHeader = blockCipherZeroPad(processHeader(header))
        val processedPayload = blockCipherZeroPad(payload)
        val combine = combine(processedHeader, blockCipherZeroPad(processedPayload))
        val result = ByteArray(combine.size)
        for (i in 0 until combine.size / 16) cbc.processBlock(combine, i * 16, result, i * 16)
        val result2 = ByteArray(8)
        System.arraycopy(result, result.size - 16, result2, 0, 8)
        val ctr = ByteArray(engine.blockSize)
        engine.processBlock(produceCTRBlock(nonce, 0.toShort()), 0, ctr, 0)
        return byteArrayXOR(result2, ctr)
    }

    fun calculateCRC(bytes: ByteArray): Int {
        var crc = 0xffff
        for (b in bytes) {
            crc = crc ushr 8 xor CRC.TABLE[crc xor b.toInt() and 0xff]
        }
        return crc
    }
}