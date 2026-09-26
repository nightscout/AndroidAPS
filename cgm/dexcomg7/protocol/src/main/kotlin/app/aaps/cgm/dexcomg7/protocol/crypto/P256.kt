package app.aaps.cgm.dexcomg7.protocol.crypto

import java.math.BigInteger

/** A point on P-256 in affine coordinates. [INFINITY] is the neutral element. */
data class P256Point(val x: BigInteger, val y: BigInteger, val isInfinity: Boolean = false) {

    /** `04 || x || y`, 65 bytes. */
    val uncompressedBytes: ByteArray get() = byteArrayOf(0x04) + x.toFixedBytes(32) + y.toFixedBytes(32)

    companion object {

        val INFINITY = P256Point(BigInteger.ZERO, BigInteger.ZERO, isInfinity = true)
    }
}

/**
 * Plain P-256 (secp256r1) point maths, which the key exchange needs and the platform crypto does not
 * expose. Not constant time. That is acceptable here: the secrets are short lived and the only other
 * party is a sensor on the body.
 */
object P256 {

    val p = BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16)
    val a: BigInteger = p - BigInteger.valueOf(3)
    val b = BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16)
    val order = BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16)
    val generator = P256Point(
        BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16),
        BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16)
    )

    fun isOnCurve(point: P256Point): Boolean {
        if (point.isInfinity) return true
        val left = point.y.modPow(BigInteger.TWO, p)
        val right = (point.x.modPow(BigInteger.valueOf(3), p) + a * point.x + b).mod(p)
        return left == right
    }

    fun negate(point: P256Point): P256Point = if (point.isInfinity) point else P256Point(point.x, (p - point.y).mod(p))

    fun add(first: P256Point, second: P256Point): P256Point {
        if (first.isInfinity) return second
        if (second.isInfinity) return first
        val lambda = if (first.x == second.x) {
            if ((first.y + second.y).mod(p).signum() == 0) return P256Point.INFINITY
            // Doubling.
            (BigInteger.valueOf(3) * first.x * first.x + a) * (BigInteger.TWO * first.y).modInverse(p)
        } else {
            (second.y - first.y) * (second.x - first.x).mod(p).modInverse(p)
        }.mod(p)
        val x = (lambda * lambda - first.x - second.x).mod(p)
        val y = (lambda * (first.x - x) - first.y).mod(p)
        return P256Point(x, y)
    }

    fun multiply(point: P256Point, scalar: BigInteger): P256Point {
        val k = scalar.mod(order)
        var result = P256Point.INFINITY
        var addend = point
        for (i in 0 until k.bitLength()) {
            if (k.testBit(i)) result = add(result, addend)
            addend = add(addend, addend)
        }
        return result
    }

    fun multiplyGenerator(scalar: BigInteger): P256Point = multiply(generator, scalar)
}

/** Big endian unsigned bytes of exactly [length], padded with leading zeros. */
fun BigInteger.toFixedBytes(length: Int): ByteArray {
    val raw = toByteArray()
    return when {
        raw.size == length -> raw
        raw.size > length  -> raw.copyOfRange(raw.size - length, raw.size) // drops the sign byte
        else               -> ByteArray(length - raw.size) + raw
    }
}

fun unsignedBigInteger(bytes: ByteArray): BigInteger = BigInteger(1, bytes)
