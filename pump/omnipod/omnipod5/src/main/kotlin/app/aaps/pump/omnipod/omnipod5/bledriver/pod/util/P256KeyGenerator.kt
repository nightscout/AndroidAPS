package app.aaps.pump.omnipod.omnipod5.bledriver.pod.util

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import javax.crypto.KeyAgreement
import dev.zacsweers.metro.Inject

/**
 * NIST P-256 (secp256r1) key generation and ECDH key agreement for Omnipod 5 pairing.
 *
 * Ported from OmnipodKit's P256KeyGenerator.swift (loopandlearn/OmnipodKit), which wraps
 * Apple's CryptoKit P256.KeyAgreement. There is no equivalent to CryptoKit available on
 * Android, so this uses the standard java.security / javax.crypto EC APIs directly, via
 * the shared raw-key encode/decode helpers in [P256Codec] (see that file's doc comment
 * for why public keys here are 64 raw bytes, not 65).
 *
 * The shared secret returned by [computeSharedSecret] is the raw ECDH result (the X
 * coordinate of the agreed point), matching CryptoKit's `sharedSecretFromKeyAgreement`,
 * which also returns the raw (un-hashed) agreed secret. Any KDF/hashing on top of this
 * shared secret is the caller's responsibility, same as with the Swift original.
 */
class P256KeyGenerator @Inject constructor() {

    fun generatePrivateKey(): ByteArray {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec(P256Codec.CURVE_NAME))
        val keyPair = keyPairGenerator.generateKeyPair()
        val privateKey = keyPair.private as ECPrivateKey
        with(P256Codec) { return privateKey.s.toFixedLengthBytes(P256Codec.SCALAR_LENGTH_BYTES) }
    }

    /** Returns the raw 64-byte (X || Y, no 0x04 prefix) public key. */
    fun publicFromPrivate(privateKey: ByteArray): ByteArray {
        require(privateKey.size == P256Codec.SCALAR_LENGTH_BYTES) {
            "P-256 private key scalar must be ${P256Codec.SCALAR_LENGTH_BYTES} bytes, got ${privateKey.size}"
        }
        val params = P256Codec.ecParameterSpec()
        val s = BigInteger(1, privateKey)
        val publicPoint = multiplyPoint(params.generator, s, params)
        return P256Codec.encodePoint(publicPoint)
    }

    fun computeSharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val ecPrivateKey = P256Codec.decodePrivateKey(privateKey)
        val ecPublicKey = P256Codec.decodePublicKey(publicKey)

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(ecPrivateKey)
        keyAgreement.doPhase(ecPublicKey, true)
        return keyAgreement.generateSecret()
    }


    private val POINT_AT_INFINITY = ECPoint(BigInteger.valueOf(-1), BigInteger.valueOf(-1))

    private fun isInfinity(point: ECPoint) = point == POINT_AT_INFINITY

    private fun multiplyPoint(point: ECPoint, scalar: BigInteger, params: ECParameterSpec): ECPoint {
        val p = (params.curve.field as ECFieldFp).p
        val a = params.curve.a

        var result = POINT_AT_INFINITY
        var addend = point
        var k = scalar

        while (k.signum() > 0) {
            if (k.testBit(0)) {
                result = addPoints(result, addend, p, a)
            }
            addend = addPoints(addend, addend, p, a)
            k = k.shiftRight(1)
        }
        return result
    }

    private fun addPoints(p1: ECPoint, p2: ECPoint, p: BigInteger, a: BigInteger): ECPoint {
        if (isInfinity(p1)) return p2
        if (isInfinity(p2)) return p1

        val lambda: BigInteger
        if (p1.affineX == p2.affineX) {
            if ((p1.affineY.add(p2.affineY)).mod(p) == BigInteger.ZERO) {
                return POINT_AT_INFINITY
            }
            val numerator = p1.affineX.pow(2).multiply(BigInteger.valueOf(3)).add(a).mod(p)
            val denominator = p1.affineY.multiply(BigInteger.TWO).mod(p)
            lambda = numerator.multiply(denominator.modInverse(p)).mod(p)
        } else {
            val numerator = p2.affineY.subtract(p1.affineY).mod(p)
            val denominator = p2.affineX.subtract(p1.affineX).mod(p)
            lambda = numerator.multiply(denominator.modInverse(p)).mod(p)
        }

        val x3 = lambda.pow(2).subtract(p1.affineX).subtract(p2.affineX).mod(p)
        val y3 = lambda.multiply(p1.affineX.subtract(x3)).subtract(p1.affineY).mod(p)
        return ECPoint(x3, y3)
    }
}
