package app.aaps.pump.eopatch.core.ble

import io.reactivex.rxjava3.core.Single
import java.nio.ByteBuffer
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

class Cipher : ICipher {

    private var sharedKey: ByteArray? = null
    @Volatile private var seq = -1
    private var secretKeySpec: SecretKeySpec? = null

    override fun updateEncryptionParam(sharedKey: ByteArray) {
        if (this.sharedKey?.contentEquals(sharedKey) == true) return
        if (sharedKey.isNotEmpty()) {
            this.sharedKey = sharedKey
            secretKeySpec = SecretKeySpec(sharedKey, "AES")
        } else {
            this.sharedKey = null
            secretKeySpec = null
        }
    }

    override fun encrypt(bytes: ByteArray, function: PatchFunc): Single<ByteArray> =
        Single.fromCallable { encryptInner(bytes, function) }

    override fun decrypt(bytes: ByteArray, function: PatchFunc): Single<ByteArray> =
        Single.fromCallable { decryptInner(bytes, function) }

    override fun onPacketSent(bytes: ByteArray, function: PatchFunc) {
        if (isEncrypted(bytes)) increaseSequence()
    }

    override fun setSeq(seq: Int) {
        this.seq = seq
    }

    override fun getSequence(): Int = seq

    private fun isEncrypted(bytes: ByteArray?): Boolean =
        bytes != null && (bytes[0].toInt() and 0x80) != 0

    @Synchronized private fun increaseSequence() {
        seq++
    }

    private fun encryptInner(bytes: ByteArray, func: PatchFunc): ByteArray {
        if (func.noCrypt) return bytes

        val seq15 = getSequence()
        val keySpec = secretKeySpec
        if (keySpec == null || seq15 < 0) return bytes

        val encOut = bytes.copyOf()
        val crc15 = getCRC15(encOut)
        val xor = seq15 xor crc15

        encOut[0] = (((xor and 0xFF00) shr 8) or 0x80).toByte()
        encOut[1] = (xor and 0xFF).toByte()

        val ivSpec = getIvSpec(seq15)
        val cipher = javax.crypto.Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, ivSpec)

        val len = encOut.size - 1
        val encoded = ByteArray(cipher.getOutputSize(len))
        var encryptLength = cipher.update(encOut, 1, len, encoded, 0)
        encryptLength += cipher.doFinal(encoded, encryptLength)

        ByteBuffer.wrap(encOut).apply {
            position(ICipher.ENC_START_INDEX)
            put(encoded)
        }
        return encOut
    }

    private fun decryptInner(bytes: ByteArray, func: PatchFunc): ByteArray {
        if (!isEncrypted(bytes)) return bytes

        val size = min(bytes.size - ICipher.ENC_START_INDEX, ICipher.ENC_END_INDEX)
        if (size <= 0) return bytes

        val seq15 = getSequence()
        val keySpec = secretKeySpec
        if (keySpec == null || seq15 < 0) return bytes

        val ivSpec = getIvSpec(seq15)
        val cipher = javax.crypto.Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, ivSpec)

        val decoded = ByteArray(cipher.getOutputSize(size))
        var decryptLength = cipher.update(bytes, ICipher.ENC_START_INDEX, size, decoded, 0)
        decryptLength += cipher.doFinal(decoded, decryptLength)

        ByteBuffer.wrap(bytes).apply {
            position(ICipher.ENC_START_INDEX)
            put(decoded)
        }
        return bytes
    }

    private fun getIvSpec(seq15: Int): IvParameterSpec {
        val buffer = ByteBuffer.allocate(16)
        buffer.putShort(seq15.toShort())
        return IvParameterSpec(buffer.array())
    }

    private fun getCRC15(input: ByteArray): Int {
        var crc = 0
        var i = ICipher.CRC_START_INDEX
        while (i < input.size) {
            crc = canCrcNext(crc, input[i])
            i++
        }
        while (i <= ICipher.ENC_END_INDEX) {
            crc = canCrcZero(crc)
            i++
        }
        return crc
    }

    private fun canCrcNext(crc: Int, data: Byte): Int =
        canCrcZero(crc xor ((data.toInt() and 0xFF) shl 7))

    private fun canCrcZero(crc: Int): Int {
        var c = crc
        repeat(8) {
            c = c shl 1
            if (c and 0x8000 != 0) c = c xor 0xc599
        }
        return c and 0x7fff
    }
}
