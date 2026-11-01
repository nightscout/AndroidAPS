package app.aaps.plugins.libre3.crypto

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import javax.crypto.KeyAgreement

/**
 * The Android half of the curve work, the same `java.security` calls the driver always made. The
 * only change is that keys arrive and leave as bytes, so the shared code never holds a JVM key.
 */

/** 32 bytes, the size of one P-256 coordinate and of a private scalar. */
private const val COORDINATE_SIZE = 32

/** 65 bytes, an uncompressed point: `0x04` then X then Y. */
private const val PUBLIC_KEY_SIZE = 65

/** The P-256 numbers, asked of the phone's own provider without making a key. */
private fun curveParameters(): ECParameterSpec {
    val parameters = AlgorithmParameters.getInstance("EC")
    parameters.init(ECGenParameterSpec("secp256r1"))
    return parameters.getParameterSpec(ECParameterSpec::class.java)
}

private fun publicKeyFor(point65: ByteArray, parameters: ECParameterSpec): ECPublicKey {
    val x = BigInteger(1, point65.copyOfRange(1, 1 + COORDINATE_SIZE))
    val y = BigInteger(1, point65.copyOfRange(1 + COORDINATE_SIZE, PUBLIC_KEY_SIZE))
    return KeyFactory.getInstance("EC")
        .generatePublic(ECPublicKeySpec(ECPoint(x, y), parameters)) as ECPublicKey
}

/** A big number as a fixed width coordinate, high byte first. */
private fun fixedLength(value: ByteArray): ByteArray {
    // A big number can carry a leading zero byte for its sign, or be shorter than the curve.
    val trimmed =
        if (value.size > COORDINATE_SIZE) value.copyOfRange(value.size - COORDINATE_SIZE, value.size) else value
    return ByteArray(COORDINATE_SIZE - trimmed.size) + trimmed
}

internal actual fun libre3P256GenerateKeyPair(): Libre3P256KeyPairBytes {
    val generator = KeyPairGenerator.getInstance("EC")
    generator.initialize(ECGenParameterSpec("secp256r1"))
    val pair = generator.generateKeyPair()
    val point = (pair.public as ECPublicKey).w
    val public65 = byteArrayOf(0x04) +
        fixedLength(point.affineX.toByteArray()) +
        fixedLength(point.affineY.toByteArray())
    val scalar = fixedLength((pair.private as ECPrivateKey).s.toByteArray())
    return Libre3P256KeyPairBytes(privateScalarBE = scalar, publicKey65 = public65)
}

internal actual fun libre3P256ValidateKeyMaterial(privateScalarBE: ByteArray, publicPoint65: ByteArray) {
    val parameters = curveParameters()
    try {
        KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(BigInteger(1, privateScalarBE), parameters))
        publicKeyFor(publicPoint65, parameters)
    } catch (e: GeneralSecurityException) {
        throw Libre3CryptoException("this key material does not belong to the P-256 curve: ${e.message}")
    }
}

internal actual fun libre3P256SharedSecret(privateScalarBE: ByteArray, peerPoint65: ByteArray): ByteArray {
    val parameters = curveParameters()
    val privateKey = KeyFactory.getInstance("EC")
        .generatePrivate(ECPrivateKeySpec(BigInteger(1, privateScalarBE), parameters))
    val agreement = KeyAgreement.getInstance("ECDH")
    agreement.init(privateKey)
    agreement.doPhase(publicKeyFor(peerPoint65, parameters), true)
    return agreement.generateSecret()
}

internal actual fun libre3VerifyEcdsaSha256(
    signerPoint65: ByteArray,
    payload: ByteArray,
    derSignature: ByteArray,
): Boolean =
    try {
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKeyFor(signerPoint65, curveParameters()))
        verifier.update(payload)
        verifier.verify(derSignature)
    } catch (_: GeneralSecurityException) {
        // A key or a signature this phone's provider cannot read is not a verified one.
        false
    }
