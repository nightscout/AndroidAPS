package app.aaps.pump.omnipod.omnipod5.bledriver.pod.util

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec

/**
 * Shared encode/decode helpers between NIST P-256 raw key representations (as used by
 * Apple's CryptoKit, and therefore by Insulet's Omnipod 5 protocol) and the JCE's
 * ECPrivateKey/ECPublicKey types.
 *
 * Used by both [P256KeyGenerator] (ECDH key agreement, for pairing) and O5CertificateStore
 * (ECDSA signing/verification, for pod command authentication) since both operate on raw
 * keys over the same curve and need the same raw-bytes <-> JCE-key-object conversions.
 *
 * Raw encodings match CryptoKit's `rawRepresentation` for P-256 keys:
 * - Private key: the 32-byte big-endian scalar.
 * - Public key: the 64-byte plain X || Y coordinate concatenation, **without** the 0x04
 *   uncompressed-point prefix byte. This is deliberately different from the 65-byte ANSI
 *   X9.63 format (CryptoKit's separate `x963Representation`) - confirmed against both
 *   Apple's swift-crypto source (rawRepresentation and x963Representation are distinct
 *   properties there) and OmnipodKit's own O5KeyExchange.swift, which hardcodes
 *   `PUBLIC_KEY_SIZE = 64` for exactly this value.
 */
object P256Codec {

    const val CURVE_NAME = "secp256r1"
    const val SCALAR_LENGTH_BYTES = 32

    /** Raw public key length: 64 bytes (X || Y), no 0x04 prefix. See class doc. */
    const val RAW_POINT_LENGTH_BYTES = 64

    fun ecParameterSpec(): ECParameterSpec {
        val algorithmParameters = AlgorithmParameters.getInstance("EC")
        algorithmParameters.init(ECGenParameterSpec(CURVE_NAME))
        return algorithmParameters.getParameterSpec(ECParameterSpec::class.java)
    }

    fun decodePrivateKey(rawScalar: ByteArray): ECPrivateKey {
        require(rawScalar.size == SCALAR_LENGTH_BYTES) {
            "P-256 private key scalar must be $SCALAR_LENGTH_BYTES bytes, got ${rawScalar.size}"
        }
        val params = ecParameterSpec()
        val s = BigInteger(1, rawScalar)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePrivate(ECPrivateKeySpec(s, params)) as ECPrivateKey
    }

    /** Decodes a raw 64-byte (X || Y, no prefix) public key. */
    fun decodePublicKey(rawPoint: ByteArray): ECPublicKey {
        require(rawPoint.size == RAW_POINT_LENGTH_BYTES) {
            "P-256 public key must be $RAW_POINT_LENGTH_BYTES raw bytes (X || Y, no prefix), got ${rawPoint.size}"
        }
        val x = BigInteger(1, rawPoint.copyOfRange(0, SCALAR_LENGTH_BYTES))
        val y = BigInteger(1, rawPoint.copyOfRange(SCALAR_LENGTH_BYTES, RAW_POINT_LENGTH_BYTES))
        val params = ecParameterSpec()
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(ECPublicKeySpec(ECPoint(x, y), params)) as ECPublicKey
    }

    /** Encodes a point as raw 64 bytes (X || Y, no 0x04 prefix). */
    fun encodePoint(point: ECPoint): ByteArray {
        val x = point.affineX.toFixedLengthBytes(SCALAR_LENGTH_BYTES)
        val y = point.affineY.toFixedLengthBytes(SCALAR_LENGTH_BYTES)
        return x + y
    }

    fun encodePublicKey(publicKey: ECPublicKey): ByteArray = encodePoint(publicKey.w)

    /**
     * Normalizes a BigInteger's magnitude to exactly [length] bytes, big-endian.
     * [BigInteger.toByteArray] may prepend a 0x00 sign byte, or be shorter than
     * expected for small values; this pads/strips as needed.
     */
    fun BigInteger.toFixedLengthBytes(length: Int): ByteArray {
        val raw = this.toByteArray()
        return when {
            raw.size == length                             -> raw
            raw.size == length + 1 && raw[0] == 0.toByte() -> raw.copyOfRange(1, raw.size)
            raw.size < length                               -> ByteArray(length - raw.size) + raw
            else                                             -> throw IllegalStateException(
                "Value does not fit in $length bytes (got ${raw.size})"
            )
        }
    }
}
