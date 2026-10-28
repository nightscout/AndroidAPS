/*
 * KEKS J-PAKE / ExtraData authentication for Dexcom G7 / ONE+.
 *
 * Shared replacement for the BouncyCastle ECDSA signer the translated KEKS sources use.
 * Translated to Kotlin from NightscoutFoundation/xDrip
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

import jamorham.libkeks.SHA256

/**
 * ECDSA over secp256r1 with SHA-256, which is what `SignatureSpi.ecDSA256` gave on Android.
 *
 * The signature is written as r then s, each padded to 32 bytes. The Java code reached the same
 * shape the long way round - BouncyCastle produced a DER sequence and `DSAChallenger` immediately
 * took it apart again into two 32 byte numbers - so nothing on the wire changes.
 *
 * **The one value in this whole port that cannot be pinned to the Java is k**, the per signature
 * random number. BouncyCastle drew it at random, so two signatures over the same challenge were
 * already different bytes on Android. The test instead pins k to the value recovered from a real
 * Java signature and checks the port then writes that same signature out.
 */
object KeksEcdsa {

    /** The 32 byte halves a KEKS signature is made of. */
    const val BYTES_PER_INTEGER = 32

    /**
     * The digest read as a whole number, the way BouncyCastle's `ECDSASigner.calculateE` does it:
     * a 256 bit digest against a 256 bit group order needs no shortening and is **not** reduced.
     */
    private fun messageValue(message: ByteArray): KeksUInt {
        val digest = SHA256.hash(message)
        val digestBits = digest.size * 8
        val orderBits = KeksP256.N.bitLength
        val value = KeksUInt.fromBytes(digest)
        return if (orderBits < digestBits) value.shr(digestBits - orderBits) else value
    }

    /** Signs with a caller supplied k. Used by the tests to reproduce a known Java signature. */
    fun signWith(privateKey: KeksUInt, message: ByteArray, k: KeksUInt): ByteArray? {
        val n = KeksP256.N
        if (k.isZero || k >= n) return null
        val point = KeksP256.G.multiply(k)
        if (point.isInfinity) return null
        val r = point.x.mod(n)
        if (r.isZero) return null
        val e = messageValue(message)
        val s = k.modInverse(n).mulMod(e.addMod(r.mulMod(privateKey, n), n), n)
        if (s.isZero) return null
        return r.toBytes(BYTES_PER_INTEGER) + s.toBytes(BYTES_PER_INTEGER)
    }

    /** Signs with a fresh random k, as BouncyCastle's `ecDSA256` did. */
    fun sign(privateKey: KeksUInt, message: ByteArray): ByteArray? {
        repeat(MAX_ATTEMPTS) {
            val signature = signWith(privateKey, message, randomInRange(KeksUInt.ONE, KeksP256.N - KeksUInt.ONE))
            if (signature != null) return signature
        }
        return null
    }

    /**
     * Checks a signature. The handshake itself never verifies - the sensor does that - but the
     * tests need it to show that what this port signs is the same thing BouncyCastle signed.
     */
    fun verify(publicKey: KeksEcPoint, message: ByteArray, signature: ByteArray): Boolean {
        if (signature.size != BYTES_PER_INTEGER * 2) return false
        val n = KeksP256.N
        val r = KeksUInt.fromBytes(signature, 0, BYTES_PER_INTEGER)
        val s = KeksUInt.fromBytes(signature, BYTES_PER_INTEGER, BYTES_PER_INTEGER)
        if (r.isZero || r >= n || s.isZero || s >= n) return false
        val e = messageValue(message)
        val w = s.modInverse(n)
        val u1 = e.mulMod(w, n)
        val u2 = r.mulMod(w, n)
        val point = KeksP256.G.multiply(u1).add(publicKey.multiply(u2))
        if (point.isInfinity) return false
        return point.x.mod(n) == r
    }

    /**
     * A random whole number between [min] and [max], both ends included. This is
     * `BigIntegers.createRandomInRange`: draw and try again, so the result is evenly spread.
     */
    fun randomInRange(min: KeksUInt, max: KeksUInt): KeksUInt {
        require(min <= max) { "empty range" }
        val span = max - min
        val bits = span.bitLength
        if (bits == 0) return min
        val byteCount = (bits + 7) / 8
        val extraBits = byteCount * 8 - bits
        repeat(MAX_ATTEMPTS) {
            val bytes = ByteArray(byteCount)
            keksRandomBytes(bytes)
            if (extraBits > 0) bytes[0] = (bytes[0].toInt() and (0xFF ushr extraBits)).toByte()
            val candidate = KeksUInt.fromBytes(bytes)
            if (candidate <= span) return min + candidate
        }
        throw IllegalStateException("could not draw a random number in range")
    }

    private const val MAX_ATTEMPTS = 1000
}
