package app.aaps.plugins.eversense.protocol

import app.aaps.plugins.eversense.util.EversenseLogger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.CCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter

/** Stateless authenticated encryption; session identity and sequence ownership stay with the caller. */
internal object SecureV2Cipher {
    fun prefix(sequence: Long): ByteArray = BinaryFields.littleEndian(sequence shl 2, 2)
    fun nonce(seed: ByteArray, sequence: Long): ByteArray {
        val original = ByteBuffer.wrap(seed).order(ByteOrder.LITTLE_ENDIAN).long
        return BinaryFields.littleEndian((original and -16384L) or sequence, 8)
    }
    fun encrypt(data: ByteArray, key: ByteArray, prefix: ByteArray, nonce: ByteArray): ByteArray? =
        transform(data, key, prefix, nonce, true)?.let { prefix + it }
    fun decrypt(data: ByteArray, key: ByteArray, prefix: ByteArray, nonce: ByteArray): ByteArray? =
        transform(data, key, prefix, nonce, false)

    private fun transform(data: ByteArray, key: ByteArray, prefix: ByteArray, nonce: ByteArray, encrypt: Boolean): ByteArray? = try {
        val engine = CCMBlockCipher.newInstance(AESEngine.newInstance())
        engine.init(encrypt, AEADParameters(KeyParameter(key), nonce.size * 8, nonce, prefix))
        val output = ByteArray(engine.getOutputSize(data.size))
        val written = engine.processBytes(data, 0, data.size, output, 0)
        engine.doFinal(output, written)
        output
    } catch (error: Exception) {
        EversenseLogger.error("EversenseSecureChannel", "Authenticated encryption failed: $error")
        EversenseLogger.debug("EversenseSecureChannel") { error.stackTraceToString() }
        null
    }
}
