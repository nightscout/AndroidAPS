/*
 * KEKS J-PAKE / ExtraData authentication for Dexcom G7 / ONE+.
 *
 * Shared replacement for the BouncyCastle secp256r1 point arithmetic the translated KEKS sources
 * use. Translated to Kotlin from NightscoutFoundation/xDrip
 *   https://github.com/NightscoutFoundation/xDrip
 *   pin commit 1e86d9a2a52577ed2c30fbf7b69d75fd56e6918f (tag 2026.07.15)
 * See plugins/libkeks/NOTICE.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Part of AndroidAPS (AGPL-3.0-or-later); these KEKS sources retain their
 * upstream GPL-3.0 terms. Free software, distributed WITHOUT ANY WARRANTY.
 * See https://www.gnu.org/licenses/gpl-3.0.html
 */
package jamorham.keks.crypto

/**
 * The curve secp256r1 (also called P-256 or prime256v1), the only curve KEKS uses.
 *
 * The numbers are the published ones. They are written here as text so a reader can compare them
 * with the standard, and `KeksCurveTest` checks them against the values BouncyCastle gave on
 * Android.
 */
object KeksP256 {

    /** The field prime. */
    val P: KeksUInt = KeksUInt.fromHex("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff")

    /** The order of the base point, called Q by the KEKS sources. */
    val N: KeksUInt = KeksUInt.fromHex("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551")

    /** The curve's a, which is p - 3. The point doubling below relies on that. */
    val A: KeksUInt = KeksUInt.fromHex("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc")

    val B: KeksUInt = KeksUInt.fromHex("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b")

    /** Bits in the field, as `ECCurve.getFieldSize()` reports it. */
    val fieldBits: Int = P.bitLength

    /** Bytes one coordinate takes, which is what the KEKS packet layout is built on. */
    val fieldSize: Int = (fieldBits + 7) / 8

    /**
     * The base point. Built lazily so that reading it cannot run while this object is still
     * setting [P] and [fieldSize] up, which is what it needs to be built at all.
     */
    val G: KeksEcPoint by lazy {
        KeksEcPoint.createPoint(
            KeksUInt.fromHex("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296"),
            KeksUInt.fromHex("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5"),
        )
    }

    /** Is this pair of numbers a point of the curve? */
    fun isOnCurve(x: KeksUInt, y: KeksUInt): Boolean {
        val left = y.mulMod(y, P)
        val right = x.mulMod(x, P).mulMod(x, P).addMod(A.mulMod(x, P), P).addMod(B, P)
        return left == right
    }
}

/**
 * A point of secp256r1, held the plain way: an x and a y, or the point at infinity.
 *
 * This takes the place of `org.bouncycastle.math.ec.ECPoint`, and copies two of its behaviours on
 * purpose, because the KEKS code and its caller both depend on them:
 *
 * - Building a point does **not** check that it lies on the curve, exactly as
 *   `ECCurve.createPoint` does not. A coordinate outside the field is still refused, with the same
 *   `IllegalArgumentException`.
 * - [multiply] checks the answer and throws `IllegalStateException("Invalid result")` when it is
 *   not a point of the curve. That is BouncyCastle's own `implCheckResult`, and the ONE+ driver
 *   catches that exception by name to turn a packet from an out of step handshake into a failed
 *   authentication rather than a crash.
 */
class KeksEcPoint private constructor(
    val x: KeksUInt,
    val y: KeksUInt,
    val isInfinity: Boolean,
) {

    /** Kept so the ported code reads like the Java it came from. Points here are always affine. */
    fun normalize(): KeksEcPoint = this

    fun negate(): KeksEcPoint =
        if (isInfinity) this else KeksEcPoint(x, if (y.isZero) y else KeksP256.P - y, false)

    fun add(other: KeksEcPoint): KeksEcPoint {
        if (isInfinity) return other
        if (other.isInfinity) return this
        return jacobianAdd(
            x, y, KeksUInt.ONE,
            other.x, other.y, KeksUInt.ONE,
        ).toAffine()
    }

    fun subtract(other: KeksEcPoint): KeksEcPoint = add(other.negate())

    fun twice(): KeksEcPoint {
        if (isInfinity) return this
        return jacobianDouble(x, y, KeksUInt.ONE).toAffine()
    }

    /**
     * Scalar multiplication, left to right over the bits of [k].
     *
     * A zero scalar gives the point at infinity, which is what BouncyCastle answers too.
     */
    fun multiply(k: KeksUInt): KeksEcPoint {
        if (isInfinity || k.isZero) return INFINITY
        var r = Jacobian(KeksUInt.ONE, KeksUInt.ONE, KeksUInt.ZERO)
        for (i in k.bitLength - 1 downTo 0) {
            r = jacobianDouble(r.x, r.y, r.z)
            if (k.testBit(i)) r = jacobianAdd(r.x, r.y, r.z, x, y, KeksUInt.ONE)
        }
        val result = r.toAffine()
        // BouncyCastle's ECAlgorithms.implCheckResult, kept because the caller depends on it.
        if (!result.isInfinity && !KeksP256.isOnCurve(result.x, result.y)) {
            throw IllegalStateException("Invalid result")
        }
        return result
    }

    /**
     * The point written out, as `ECPoint.getEncoded(false)` writes it: a 0x04 marker then x then
     * y, each padded to the field size. Infinity is a single zero byte, again as BouncyCastle.
     */
    fun getEncoded(compressed: Boolean = false): ByteArray {
        require(!compressed) { "KEKS never sends a compressed point" }
        if (isInfinity) return ByteArray(1)
        val size = KeksP256.fieldSize
        val out = ByteArray(1 + size * 2)
        out[0] = 0x04
        x.toBytes(size).copyInto(out, 1)
        y.toBytes(size).copyInto(out, 1 + size)
        return out
    }

    /**
     * `getXCoord().getEncoded()` - the x padded to the field size.
     *
     * Infinity has no coordinate, so this raises, the way BouncyCastle's `getXCoord()` answered
     * null and the caller then raised on it. The ONE+ driver catches that and calls the handshake
     * failed rather than deriving a key from zeros.
     */
    fun xEncoded(): ByteArray {
        if (isInfinity) throw NullPointerException("the point at infinity has no x")
        return x.toBytes(KeksP256.fieldSize)
    }

    /** `getYCoord().getEncoded()` - the y padded to the field size. */
    fun yEncoded(): ByteArray {
        if (isInfinity) throw NullPointerException("the point at infinity has no y")
        return y.toBytes(KeksP256.fieldSize)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is KeksEcPoint) return false
        if (isInfinity || other.isInfinity) return isInfinity && other.isInfinity
        return x == other.x && y == other.y
    }

    override fun hashCode(): Int = if (isInfinity) 0 else x.hashCode() * 31 + y.hashCode()

    override fun toString(): String = if (isInfinity) "INF" else "(${x.toHex()},${y.toHex()})"

    private class Jacobian(val x: KeksUInt, val y: KeksUInt, val z: KeksUInt) {
        fun toAffine(): KeksEcPoint {
            if (z.isZero) return INFINITY
            val zInv = z.modInverse(KeksP256.P)
            val zInv2 = zInv.mulMod(zInv, KeksP256.P)
            val zInv3 = zInv2.mulMod(zInv, KeksP256.P)
            return KeksEcPoint(x.mulMod(zInv2, KeksP256.P), y.mulMod(zInv3, KeksP256.P), false)
        }
    }

    companion object {

        val INFINITY = KeksEcPoint(KeksUInt.ZERO, KeksUInt.ZERO, true)

        /**
         * `ECCurve.createPoint(x, y)`. The coordinates have to be inside the field; whether they
         * are a point of the curve is **not** checked here, exactly as in BouncyCastle.
         */
        fun createPoint(x: KeksUInt, y: KeksUInt): KeksEcPoint {
            require(x < KeksP256.P) { "x value invalid for SecP256R1FieldElement" }
            require(y < KeksP256.P) { "y value invalid for SecP256R1FieldElement" }
            return KeksEcPoint(x, y, false)
        }

        /** Reads a 0x04 marked uncompressed point, or a single zero byte for infinity. */
        fun decodePoint(bytes: ByteArray): KeksEcPoint {
            val size = KeksP256.fieldSize
            if (bytes.size == 1 && bytes[0].toInt() == 0) return INFINITY
            require(bytes.size == 1 + size * 2 && bytes[0].toInt() == 0x04) {
                "not an uncompressed secp256r1 point"
            }
            return createPoint(
                KeksUInt.fromBytes(bytes, 1, size),
                KeksUInt.fromBytes(bytes, 1 + size, size),
            )
        }

        private val THREE = KeksUInt.of(3)
        private val FOUR = KeksUInt.of(4)
        private val EIGHT = KeksUInt.of(8)

        /** "dbl-2001-b", which is the usual doubling for a curve whose a is p - 3. */
        private fun jacobianDouble(x: KeksUInt, y: KeksUInt, z: KeksUInt): Jacobian {
            val p = KeksP256.P
            if (z.isZero || y.isZero) return Jacobian(KeksUInt.ONE, KeksUInt.ONE, KeksUInt.ZERO)
            val delta = z.mulMod(z, p)
            val gamma = y.mulMod(y, p)
            val beta = x.mulMod(gamma, p)
            val alpha = THREE.mulMod(x.subMod(delta, p).mulMod(x.addMod(delta, p), p), p)
            val x3 = alpha.mulMod(alpha, p).subMod(EIGHT.mulMod(beta, p), p)
            val yz = y.addMod(z, p)
            val z3 = yz.mulMod(yz, p).subMod(gamma, p).subMod(delta, p)
            val y3 = alpha.mulMod(FOUR.mulMod(beta, p).subMod(x3, p), p)
                .subMod(EIGHT.mulMod(gamma.mulMod(gamma, p), p), p)
            return Jacobian(x3, y3, z3)
        }

        /** "add-2007-bl". */
        private fun jacobianAdd(
            x1: KeksUInt, y1: KeksUInt, z1: KeksUInt,
            x2: KeksUInt, y2: KeksUInt, z2: KeksUInt,
        ): Jacobian {
            val p = KeksP256.P
            if (z1.isZero) return Jacobian(x2, y2, z2)
            if (z2.isZero) return Jacobian(x1, y1, z1)
            val z1z1 = z1.mulMod(z1, p)
            val z2z2 = z2.mulMod(z2, p)
            val u1 = x1.mulMod(z2z2, p)
            val u2 = x2.mulMod(z1z1, p)
            val s1 = y1.mulMod(z2, p).mulMod(z2z2, p)
            val s2 = y2.mulMod(z1, p).mulMod(z1z1, p)
            if (u1 == u2) {
                return if (s1 == s2) {
                    jacobianDouble(x1, y1, z1)
                } else {
                    Jacobian(KeksUInt.ONE, KeksUInt.ONE, KeksUInt.ZERO)
                }
            }
            val h = u2.subMod(u1, p)
            val twoH = h.addMod(h, p)
            val i = twoH.mulMod(twoH, p)
            val j = h.mulMod(i, p)
            val r = s2.subMod(s1, p).let { it.addMod(it, p) }
            val v = u1.mulMod(i, p)
            val x3 = r.mulMod(r, p).subMod(j, p).subMod(v.addMod(v, p), p)
            val s1j = s1.mulMod(j, p)
            val y3 = r.mulMod(v.subMod(x3, p), p).subMod(s1j.addMod(s1j, p), p)
            val zz = z1.addMod(z2, p)
            val z3 = zz.mulMod(zz, p).subMod(z1z1, p).subMod(z2z2, p).mulMod(h, p)
            return Jacobian(x3, y3, z3)
        }
    }
}
