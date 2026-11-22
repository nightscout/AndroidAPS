package app.aaps.plugins.libre3.crypto

import java.math.BigInteger

/*
 * The Android half of the point multiply of the first pairing scheme, on the P-256 curve.
 *
 * Ported from LibreCRKit `Crypto/P256ScalarMultiplier.swift` at pin `a86b92f`.
 *
 * The Swift carries its own field arithmetic in four sixty four bit limbs. This port uses
 * `BigInteger` instead. That is not a change of behaviour: both compute the same point of the same
 * curve, and the published vectors are what proves it. Plain arithmetic is also far easier to
 * review than hand written limb code.
 *
 * The scalar is the sensor maker's own seventy byte window, low byte first. It is **not** reduced
 * first: the original walks every bit it finds, and so does this.
 *
 * This multiply is **not** constant time: it branches on the bits of the scalar and inverts a
 * field element on every step. That is the same shape as the upstream code and is accepted here,
 * because the pairing key this leads to is itself kept in the app's own settings file, so a
 * reader who could time this loop already has an easier way in. It is written down so the next
 * reader does not have to work it out.
 */

private val p256Prime = BigInteger(
    "115792089210356248762697446949407573530086143415290314195533631308867097853951"
)
private val p256B = BigInteger(
    "41058363725152142129326129780047268409114441015993725554835256314039467401291"
)
private val threeModP: BigInteger = BigInteger.valueOf(3)

/** One point of the curve, or the point at infinity. */
private class P256Point(val x: BigInteger?, val y: BigInteger?) {

    val isInfinity: Boolean get() = x == null
}

private val p256Infinity = P256Point(null, null)

private fun modP(value: BigInteger): BigInteger = value.mod(p256Prime)

private fun p256Double(point: P256Point): P256Point {
    if (point.isInfinity) return p256Infinity
    val x = point.x!!
    val y = point.y!!
    if (y.signum() == 0) return p256Infinity
    val lambda = modP(
        modP(threeModP * x * x - threeModP) * modP(BigInteger.TWO * y).modInverse(p256Prime)
    )
    val x3 = modP(lambda * lambda - BigInteger.TWO * x)
    val y3 = modP(lambda * (x - x3) - y)
    return P256Point(x3, y3)
}

private fun p256Add(point: P256Point, addend: P256Point): P256Point {
    if (point.isInfinity) return addend
    if (addend.isInfinity) return point
    val x1 = point.x!!
    val y1 = point.y!!
    val x2 = addend.x!!
    val y2 = addend.y!!
    if (x1 == x2) {
        return if (modP(y1 + y2).signum() == 0) p256Infinity else p256Double(point)
    }
    val lambda = modP(modP(y2 - y1) * modP(x2 - x1).modInverse(p256Prime))
    val x3 = modP(lambda * lambda - x1 - x2)
    val y3 = modP(lambda * (x1 - x3) - y1)
    return P256Point(x3, y3)
}

/** The seventy byte window of one coordinate: thirty two bytes low first, then zeros. */
private fun littleEndianPadded70(value: BigInteger): ByteArray {
    val out = ByteArray(70)
    val big = value.toByteArray()
    // A big number can carry a leading zero byte for its sign, and can be shorter than the curve.
    val trimmed = if (big.size > 32) big.copyOfRange(big.size - 32, big.size) else big
    for (index in trimmed.indices) {
        out[trimmed.size - 1 - index] = trimmed[index]
    }
    return out
}

/**
 * Multiplies the sensor's point by the scalar of the seventy byte window.
 */
internal actual fun libre3P256ScalarMultiply(
    scalarWindowLE: ByteArray,
    sensorPointXYBE: ByteArray,
): Libre3P256Window70 {
    if (scalarWindowLE.size < 70) {
        throw Libre3CryptoException("the scalar window must be at least 70 bytes, not ${scalarWindowLE.size}")
    }
    if (sensorPointXYBE.size < 64) {
        throw Libre3CryptoException("the sensor point must be at least 64 bytes, not ${sensorPointXYBE.size}")
    }

    val x = BigInteger(1, sensorPointXYBE.copyOfRange(0, 32))
    val y = BigInteger(1, sensorPointXYBE.copyOfRange(32, 64))
    // The original refuses a point that is not on the curve, and so does this.
    val rhs = modP(x * x * x - threeModP * x + p256B)
    if (modP(y * y) != rhs) {
        throw Libre3CryptoException("the sensor point is not on the P-256 curve")
    }
    val point = P256Point(x, y)

    val scalar = BigInteger(1, scalarWindowLE.copyOfRange(0, 70).reversedArray())
    if (scalar.signum() == 0) {
        throw Libre3CryptoException("the scalar of the first pairing window is zero")
    }

    var result = p256Infinity
    for (bit in scalar.bitLength() - 1 downTo 0) {
        result = p256Double(result)
        if (scalar.testBit(bit)) {
            result = p256Add(result, point)
        }
    }
    if (result.isInfinity) {
        throw Libre3CryptoException("the point multiply of the first pairing ended at infinity")
    }

    return Libre3P256Window70(littleEndianPadded70(result.x!!), littleEndianPadded70(result.y!!))
}
