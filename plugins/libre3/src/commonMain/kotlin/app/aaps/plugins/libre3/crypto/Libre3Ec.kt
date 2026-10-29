package app.aaps.plugins.libre3.crypto

/**
 * A P-256 key pair as plain bytes.
 *
 * @param privateScalarBE the private scalar, 32 bytes, high byte first.
 * @param publicKey65 the public point, 65 bytes, `0x04` then X then Y.
 */
internal class Libre3P256KeyPairBytes(val privateScalarBE: ByteArray, val publicKey65: ByteArray)

/** The two coordinates of a point, each in the seventy byte window the first pairing uses. */
internal class Libre3P256Window70(val x70: ByteArray, val y70: ByteArray)

/**
 * The elliptic curve work this driver needs, on the platform it is running on.
 *
 * Everything around these five calls - the message shapes, the DER wrapping, the checks on sizes
 * and on the leading `0x04`, the rule that a first pairing key pair has no ordinary shared secret -
 * is shared code. Only the curve arithmetic itself is asked of the platform.
 *
 * ⚠️ None of these may answer with something made up. A wrong point or a wrong secret does not
 * fail loudly, it pairs wrongly, and a wrongly paired sensor can report a glucose value that is
 * not the one the body has. Where a platform cannot do one of these, it must throw
 * [Libre3CryptoException] saying so, which stops the pairing where it stands.
 */

/** A fresh random key pair, from the platform's own secure random. */
internal expect fun libre3P256GenerateKeyPair(): Libre3P256KeyPairBytes

/**
 * Checks that a scalar and a point are usable key material of the P-256 curve.
 *
 * This is what building the two keys through the phone's security provider used to do as a side
 * effect: a scalar outside the group order, or a point that is not on the curve, was refused
 * there. It is stated on purpose now, so the refusal cannot be lost in a port.
 *
 * @throws Libre3CryptoException when either is not usable.
 */
internal expect fun libre3P256ValidateKeyMaterial(privateScalarBE: ByteArray, publicPoint65: ByteArray)

/**
 * The ECDH shared secret of this phone's scalar and the sensor's point.
 *
 * @param privateScalarBE 32 bytes, high byte first.
 * @param peerPoint65 65 bytes, `0x04` then X then Y. The caller has already checked the shape.
 * @return the raw X coordinate of the product point, which is what `KeyAgreement("ECDH")` gives.
 */
internal expect fun libre3P256SharedSecret(privateScalarBE: ByteArray, peerPoint65: ByteArray): ByteArray

/**
 * True when [derSignature] over [payload] was made by the key whose point is [signerPoint65].
 *
 * @param derSignature SHA-256 with ECDSA, in the tagged form, as `Libre3SensorCert` builds it.
 * @return false, never a throw, when the platform cannot read the key or the signature. A key this
 *   phone cannot read is not a verified one.
 */
internal expect fun libre3VerifyEcdsaSha256(
    signerPoint65: ByteArray,
    payload: ByteArray,
    derSignature: ByteArray,
): Boolean

/**
 * The point multiply of the first pairing scheme.
 *
 * This one is plain arithmetic and not a service of the phone. It is asked of the platform only
 * because Kotlin has no shared big whole number type, so the modular arithmetic of the curve has
 * nowhere to live in common code. If one ever lands here, this call can move with it and nothing
 * around it has to change.
 *
 * @param scalarWindowLE the sensor maker's seventy byte window, low byte first. It is **not**
 *   reduced first: the original walks every bit it finds.
 * @param sensorPointXYBE the sensor's point as X then Y, high byte first, at least 64 bytes.
 * @throws Libre3CryptoException on a point that is not on the curve, a zero scalar, or a product
 *   that ends at infinity.
 */
internal expect fun libre3P256ScalarMultiply(
    scalarWindowLE: ByteArray,
    sensorPointXYBE: ByteArray,
): Libre3P256Window70
