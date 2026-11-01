/*
 * KEKS J-PAKE / ExtraData authentication for Dexcom G7 / ONE+.
 *
 * Shared replacement for the BouncyCastle key readers the translated KEKS sources use.
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

/**
 * Just enough DER to read the two key shapes KEKS is handed.
 *
 * On Android this was `KeyFactorySpi.EC.generatePrivate(PrivateKeyInfo)` and
 * `generatePublic(SubjectPublicKeyInfo)`. Only the key material is taken out; the algorithm
 * identifier is walked past but not checked, which is what the old code did too - it always got
 * these bytes from the xDrip configuration QR code, never from the sensor.
 */
object KeksDer {

    private const val TAG_INTEGER = 0x02
    private const val TAG_BIT_STRING = 0x03
    private const val TAG_OCTET_STRING = 0x04
    private const val TAG_SEQUENCE = 0x30

    /**
     * The private number d out of a PKCS#8 PrivateKeyInfo that wraps an RFC 5915 EC private key.
     *
     * Shape: SEQUENCE { INTEGER version, SEQUENCE algorithm, OCTET STRING { SEQUENCE { INTEGER 1,
     * OCTET STRING d, ... } } }.
     */
    fun readPrivateKeyScalar(pkcs8: ByteArray): KeksUInt {
        val outer = Reader(pkcs8).sequence()
        outer.skip(TAG_INTEGER)
        outer.skip(TAG_SEQUENCE)
        val inner = Reader(outer.read(TAG_OCTET_STRING)).sequence()
        inner.skip(TAG_INTEGER)
        val d = inner.read(TAG_OCTET_STRING)
        return KeksUInt.fromBytes(d)
    }

    /**
     * The public point out of an X.509 SubjectPublicKeyInfo.
     *
     * Shape: SEQUENCE { SEQUENCE algorithm, BIT STRING point }. The bit string's first byte is the
     * count of unused bits, which is always zero here.
     */
    fun readPublicPoint(spki: ByteArray): KeksEcPoint {
        val outer = Reader(spki).sequence()
        outer.skip(TAG_SEQUENCE)
        val bits = outer.read(TAG_BIT_STRING)
        require(bits.isNotEmpty() && bits[0].toInt() == 0) { "unexpected unused bits in a key" }
        return KeksEcPoint.decodePoint(bits.copyOfRange(1, bits.size))
    }

    private class Reader(private val bytes: ByteArray, private var at: Int = 0, private val end: Int = bytes.size) {

        fun sequence(): Reader {
            val body = read(TAG_SEQUENCE)
            return Reader(body)
        }

        fun skip(expectedTag: Int) {
            read(expectedTag)
        }

        fun read(expectedTag: Int): ByteArray {
            require(at < end) { "DER ran out of bytes" }
            val tag = bytes[at].toInt() and 0xFF
            require(tag == expectedTag) { "DER expected tag $expectedTag but found $tag" }
            at++
            var length = bytes[at].toInt() and 0xFF
            at++
            if (length and 0x80 != 0) {
                val count = length and 0x7F
                require(count in 1..4) { "DER length is too long" }
                length = 0
                for (i in 0 until count) {
                    length = (length shl 8) or (bytes[at].toInt() and 0xFF)
                    at++
                }
            }
            require(length >= 0 && at + length <= end) { "DER length runs past the end" }
            val body = bytes.copyOfRange(at, at + length)
            at += length
            return body
        }
    }
}
