package app.aaps.cgm.dexcomg7.protocol.crypto

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPrivateKeySpec

/**
 * Answers the sensor's key challenge, the last proof of identity before it agrees to bond.
 *
 * The sensor sends a 0x0C acknowledgement with 16 bytes to sign. We return an ECDSA P-256 signature
 * over exactly those bytes, made with the fixed key whose certificate we sent just before, as raw
 * `r || s` (64 bytes).
 */
object G7ChallengeSigner {

    val p256Parameters: ECParameterSpec by lazy {
        AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }.getParameterSpec(ECParameterSpec::class.java)
    }

    private val privateKey: PrivateKey by lazy {
        KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(BigInteger(1, G7DexcomCredentials.challengePrivateKey), p256Parameters))
    }

    fun sign(challengeAcknowledgement: ByteArray): ByteArray {
        require(challengeAcknowledgement.size >= 18) { "Key challenge too short: ${challengeAcknowledgement.size}" }
        val payload = challengeAcknowledgement.copyOfRange(2, 18)
        val der = Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(payload)
            sign()
        }
        return derToRaw(der)
    }

    /** DER `SEQUENCE { INTEGER r, INTEGER s }` to fixed 32-byte `r || s`. */
    fun derToRaw(der: ByteArray): ByteArray {
        var index = 0
        require(der[index++].toInt() == 0x30) { "Not a DER sequence" }
        index += if (der[index].toInt() and 0x80 != 0) 1 + (der[index].toInt() and 0x7f) else 1
        fun readInteger(): BigInteger {
            require(der[index++].toInt() == 0x02) { "Not a DER integer" }
            val length = der[index++].toInt() and 0xff
            return BigInteger(1, der.copyOfRange(index, index + length)).also { index += length }
        }
        val r = readInteger()
        val s = readInteger()
        return r.toFixedBytes(32) + s.toFixedBytes(32)
    }
}
