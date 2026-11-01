/*
 * KEKS J-PAKE / ExtraData authentication for Dexcom G7 / ONE+.
 *
 * Shared replacement for java.math.BigInteger, written for this port. The KEKS sources it serves
 * are translated to Kotlin from NightscoutFoundation/xDrip
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
 * A whole number that is never negative, stored as 32 bit words, least significant word first.
 *
 * This exists because Kotlin has no shared `BigInteger`. It is deliberately small: it does only
 * what the KEKS handshake asks of `java.math.BigInteger`, and every operation is pinned by golden
 * values taken from the Java code that ships on Android today (see `KeksBigIntTest`).
 *
 * Values are immutable. The word array is normalised, so the last word is never zero and the
 * number zero is an empty array.
 */
class KeksUInt private constructor(private val w: IntArray) : Comparable<KeksUInt> {

    val isZero: Boolean get() = w.isEmpty()

    val isOne: Boolean get() = w.size == 1 && w[0] == 1

    val isEven: Boolean get() = w.isEmpty() || (w[0] and 1) == 0

    /** Number of bits needed to write this value, so zero is 0 and one is 1. */
    val bitLength: Int
        get() = if (w.isEmpty()) 0 else w.size * 32 - w[w.size - 1].countLeadingZeroBits()

    fun testBit(index: Int): Boolean {
        if (index < 0) return false
        val word = index ushr 5
        if (word >= w.size) return false
        return (w[word] ushr (index and 31)) and 1 == 1
    }

    override fun compareTo(other: KeksUInt): Int {
        if (w.size != other.w.size) return if (w.size < other.w.size) -1 else 1
        for (i in w.indices.reversed()) {
            val a = w[i].lo()
            val b = other.w[i].lo()
            if (a != b) return if (a < b) -1 else 1
        }
        return 0
    }

    override fun equals(other: Any?): Boolean = other is KeksUInt && compareTo(other) == 0

    override fun hashCode(): Int = w.contentHashCode()

    operator fun plus(other: KeksUInt): KeksUInt {
        val a = w
        val b = other.w
        val size = maxOf(a.size, b.size) + 1
        val out = IntArray(size)
        var carry = 0L
        for (i in 0 until size) {
            val sum = (if (i < a.size) a[i].lo() else 0L) + (if (i < b.size) b[i].lo() else 0L) + carry
            out[i] = sum.toInt()
            carry = sum ushr 32
        }
        return of(out)
    }

    /** Subtraction. The caller must have checked that this is not smaller than [other]. */
    operator fun minus(other: KeksUInt): KeksUInt {
        require(this >= other) { "KeksUInt cannot go below zero" }
        val a = w
        val b = other.w
        val out = IntArray(a.size)
        var borrow = 0L
        for (i in a.indices) {
            val diff = a[i].lo() - (if (i < b.size) b[i].lo() else 0L) - borrow
            out[i] = diff.toInt()
            borrow = if (diff < 0) 1L else 0L
        }
        return of(out)
    }

    operator fun times(other: KeksUInt): KeksUInt {
        if (isZero || other.isZero) return ZERO
        val a = w
        val b = other.w
        val out = IntArray(a.size + b.size)
        for (i in a.indices) {
            var carry = 0L
            val ai = a[i].lo()
            for (j in b.indices) {
                val t = ai * b[j].lo() + out[i + j].lo() + carry
                out[i + j] = t.toInt()
                carry = t ushr 32
            }
            out[i + b.size] = carry.toInt()
        }
        return of(out)
    }

    fun shl(bits: Int): KeksUInt {
        require(bits >= 0)
        if (isZero || bits == 0) return this
        val words = bits ushr 5
        val rest = bits and 31
        val out = IntArray(w.size + words + 1)
        if (rest == 0) {
            for (i in w.indices) out[i + words] = w[i]
        } else {
            for (i in w.indices) {
                out[i + words] = out[i + words] or (w[i] shl rest)
                out[i + words + 1] = w[i] ushr (32 - rest)
            }
        }
        return of(out)
    }

    fun shr(bits: Int): KeksUInt {
        require(bits >= 0)
        if (isZero || bits == 0) return this
        val words = bits ushr 5
        if (words >= w.size) return ZERO
        val rest = bits and 31
        val out = IntArray(w.size - words)
        if (rest == 0) {
            for (i in out.indices) out[i] = w[i + words]
        } else {
            for (i in out.indices) {
                var v = w[i + words] ushr rest
                if (i + words + 1 < w.size) v = v or (w[i + words + 1] shl (32 - rest))
                out[i] = v
            }
        }
        return of(out)
    }

    /**
     * Quotient and remainder, by Knuth's algorithm D (the schoolbook long division every big
     * number library uses). The single word divisor is a separate, simpler path, exactly as in
     * the published algorithm.
     */
    fun divRem(divisor: KeksUInt): Pair<KeksUInt, KeksUInt> {
        require(!divisor.isZero) { "KeksUInt division by zero" }
        if (this < divisor) return ZERO to this
        val n = divisor.w.size
        val m = w.size
        if (n == 1) {
            val d = divisor.w[0].toUInt().toULong()
            val q = IntArray(m)
            var k = 0UL
            for (j in m - 1 downTo 0) {
                val cur = (k shl 32) or w[j].toUInt().toULong()
                q[j] = (cur / d).toInt()
                k = cur % d
            }
            return of(q) to of(intArrayOf(k.toInt()))
        }

        // Normalise so the top word of the divisor has its high bit set; that is what makes the
        // two word estimate of each quotient digit good enough.
        val shift = divisor.w[n - 1].countLeadingZeroBits()
        val vn = IntArray(n)
        if (shift == 0) {
            divisor.w.copyInto(vn)
        } else {
            for (i in n - 1 downTo 1) vn[i] = (divisor.w[i] shl shift) or (divisor.w[i - 1] ushr (32 - shift))
            vn[0] = divisor.w[0] shl shift
        }
        val un = IntArray(m + 1)
        if (shift == 0) {
            w.copyInto(un)
        } else {
            un[m] = w[m - 1] ushr (32 - shift)
            for (i in m - 1 downTo 1) un[i] = (w[i] shl shift) or (w[i - 1] ushr (32 - shift))
            un[0] = w[0] shl shift
        }

        val base = 1UL shl 32
        val q = IntArray(m - n + 1)
        val top = vn[n - 1].toUInt().toULong()
        val second = vn[n - 2].toUInt().toULong()
        for (j in m - n downTo 0) {
            val numerator = (un[j + n].toUInt().toULong() shl 32) or un[j + n - 1].toUInt().toULong()
            var qhat = numerator / top
            var rhat = numerator % top
            while (qhat >= base || qhat * second > (rhat shl 32) + un[j + n - 2].toUInt().toULong()) {
                qhat--
                rhat += top
                if (rhat >= base) break
            }

            // Multiply the divisor by the estimate and take it away from the running remainder.
            var carry = 0UL
            var borrow = 0L
            for (i in 0 until n) {
                val p = qhat * vn[i].toUInt().toULong() + carry
                carry = p shr 32
                val diff = un[i + j].lo() - (p and 0xFFFFFFFFUL).toLong() - borrow
                un[i + j] = diff.toInt()
                borrow = if (diff < 0) 1L else 0L
            }
            val last = un[j + n].lo() - carry.toLong() - borrow
            un[j + n] = last.toInt()
            q[j] = qhat.toInt()

            if (last < 0) {
                // The estimate was one too big, which the algorithm says happens very rarely.
                q[j] = q[j] - 1
                var addCarry = 0L
                for (i in 0 until n) {
                    val t = un[i + j].lo() + vn[i].lo() + addCarry
                    un[i + j] = t.toInt()
                    addCarry = t ushr 32
                }
                un[j + n] = (un[j + n].lo() + addCarry).toInt()
            }
        }

        val rem = IntArray(n)
        if (shift == 0) {
            un.copyInto(rem, 0, 0, n)
        } else {
            for (i in 0 until n) rem[i] = (un[i] ushr shift) or (un[i + 1] shl (32 - shift))
        }
        return of(q) to of(rem)
    }

    fun mod(modulus: KeksUInt): KeksUInt = divRem(modulus).second

    fun addMod(other: KeksUInt, modulus: KeksUInt): KeksUInt {
        val sum = this + other
        // Both sides are nearly always already reduced, so one compare usually replaces a divide.
        if (sum < modulus) return sum
        val once = sum - modulus
        return if (once < modulus) once else sum.mod(modulus)
    }

    fun subMod(other: KeksUInt, modulus: KeksUInt): KeksUInt {
        val a = mod(modulus)
        val b = other.mod(modulus)
        return if (a >= b) a - b else a + modulus - b
    }

    fun mulMod(other: KeksUInt, modulus: KeksUInt): KeksUInt = (this * other).mod(modulus)

    /**
     * The value that multiplies this back to one, modulo an **odd** [modulus]. Both numbers KEKS
     * works with - the field prime and the group order of secp256r1 - are odd primes.
     *
     * Throws when there is no inverse, the way `BigInteger.modInverse` does, because a silent
     * wrong answer here would pair with the sensor and then read glucose through a wrong key.
     */
    fun modInverse(modulus: KeksUInt): KeksUInt {
        require(!modulus.isEven) { "KeksUInt.modInverse needs an odd modulus" }
        var u = mod(modulus)
        require(!u.isZero) { "KeksUInt has no inverse: zero" }
        var v = modulus
        var x1 = ONE
        var x2 = ZERO
        while (!u.isOne && !v.isOne) {
            while (u.isEven) {
                u = u.shr(1)
                x1 = if (x1.isEven) x1.shr(1) else (x1 + modulus).shr(1)
            }
            while (v.isEven) {
                v = v.shr(1)
                x2 = if (x2.isEven) x2.shr(1) else (x2 + modulus).shr(1)
            }
            if (u >= v) {
                u -= v
                x1 = x1.subMod(x2, modulus)
            } else {
                v -= u
                x2 = x2.subMod(x1, modulus)
            }
            // Either side reaching zero means the two numbers share a factor, so there is no
            // inverse. Without this the halving loop above would spin on zero for ever.
            require(!u.isZero && !v.isZero) { "KeksUInt has no inverse" }
        }
        val answer = if (u.isOne) x1.mod(modulus) else x2.mod(modulus)
        require(mulMod(answer, modulus).isOne) { "KeksUInt has no inverse" }
        return answer
    }

    /** Square and multiply. Only the tests use it; the handshake itself never needs a power. */
    fun modPow(exponent: KeksUInt, modulus: KeksUInt): KeksUInt {
        if (modulus.isOne) return ZERO
        var result = ONE
        var base = mod(modulus)
        for (i in 0 until exponent.bitLength) {
            if (exponent.testBit(i)) result = result.mulMod(base, modulus)
            base = base.mulMod(base, modulus)
        }
        return result
    }

    /**
     * Big endian bytes, left padded with zeros to exactly [length].
     *
     * This is `BigIntegers.asUnsignedByteArray(length, value)`, including its refusal to shorten a
     * value that does not fit.
     */
    fun toBytes(length: Int): ByteArray {
        val out = ByteArray(length)
        for (i in w.indices) {
            val word = w[i]
            for (b in 0 until 4) {
                val index = length - 1 - (i * 4 + b)
                val byte = ((word ushr (b * 8)) and 0xFF).toByte()
                if (index < 0) {
                    require(byte.toInt() == 0) { "standard length exceeded for value" }
                } else {
                    out[index] = byte
                }
            }
        }
        return out
    }

    /** Lower case, no leading zeros, "0" for zero - what `BigInteger.toString(16)` gives. */
    fun toHex(): String {
        if (isZero) return "0"
        val sb = StringBuilder()
        for (i in w.indices.reversed()) {
            val part = w[i].toUInt().toString(16)
            if (sb.isEmpty()) sb.append(part) else sb.append(part.padStart(8, '0'))
        }
        return sb.toString()
    }

    override fun toString(): String = toHex()

    companion object {

        val ZERO = KeksUInt(IntArray(0))
        val ONE = KeksUInt(intArrayOf(1))
        val TWO = KeksUInt(intArrayOf(2))

        private fun of(words: IntArray): KeksUInt {
            var size = words.size
            while (size > 0 && words[size - 1] == 0) size--
            if (size == 0) return ZERO
            return KeksUInt(if (size == words.size) words else words.copyOf(size))
        }

        fun of(value: Int): KeksUInt {
            require(value >= 0) { "KeksUInt is never negative" }
            return if (value == 0) ZERO else KeksUInt(intArrayOf(value))
        }

        /** `BigIntegers.fromUnsignedByteArray` - big endian, no sign bit. */
        fun fromBytes(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): KeksUInt {
            require(offset >= 0 && length >= 0 && offset + length <= bytes.size) {
                "KeksUInt.fromBytes out of range"
            }
            if (length == 0) return ZERO
            val words = IntArray((length + 3) / 4)
            for (i in 0 until length) {
                val value = bytes[offset + length - 1 - i].toInt() and 0xFF
                words[i ushr 2] = words[i ushr 2] or (value shl ((i and 3) * 8))
            }
            return of(words)
        }

        fun fromHex(text: String): KeksUInt {
            if (text.isEmpty()) return ZERO
            val padded = if (text.length % 2 == 0) text else "0$text"
            val bytes = ByteArray(padded.length / 2)
            for (i in bytes.indices) {
                val high = hexDigit(padded[i * 2])
                val low = hexDigit(padded[i * 2 + 1])
                bytes[i] = ((high shl 4) or low).toByte()
            }
            return fromBytes(bytes)
        }

        private fun hexDigit(c: Char): Int = when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> throw IllegalArgumentException("not a hex digit: $c")
        }
    }
}

/** The word read as an unsigned 32 bit number. */
private fun Int.lo(): Long = toLong() and 0xFFFFFFFFL
