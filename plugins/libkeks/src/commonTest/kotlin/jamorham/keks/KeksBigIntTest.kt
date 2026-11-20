/*
 * KEKS J-PAKE / ExtraData authentication for Dexcom G7 / ONE+.
 *
 * Tests for the Kotlin translation. They check it against the behaviour of the original Java code in
 * NightscoutFoundation/xDrip
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
package jamorham.keks

import jamorham.keks.crypto.KeksP256
import jamorham.keks.crypto.KeksUInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The shared whole number type against values taken from `java.math.BigInteger` on the JVM.
 *
 * This is the layer the rest of the handshake stands on, so it is checked hardest: every row here
 * was produced by the Java that ships on Android today.
 */
class KeksBigIntTest {

    private val p = KeksP256.P
    private val q = KeksP256.N

    @Test
    fun modulusAgreesWithJava() {
        for (row in KeksGolden.bigMod) {
            val a = bigOf(row[0])
            assertEquals(row[1], a.mod(p).toHex(), "mod P of ${row[0]}")
            assertEquals(row[2], a.mod(q).toHex(), "mod Q of ${row[0]}")
        }
        assertTrue(KeksGolden.bigMod.size >= 60, "not enough rows to be worth anything")
    }

    @Test
    fun arithmeticAgreesWithJava() {
        for (row in KeksGolden.bigArith) {
            val a = bigOf(row[0])
            val b = bigOf(row[1])
            assertEquals(row[2], (a * b).toHex(), "product")
            assertEquals(row[3], a.mulMod(b, p).toHex(), "product mod P")
            assertEquals(row[4], a.mulMod(b, q).toHex(), "product mod Q")
            assertEquals(row[5], (a + b).toHex(), "sum")
            assertEquals(row[6], a.addMod(b, p).toHex(), "sum mod P")
            assertEquals(row[7], a.subMod(b, p).toHex(), "difference mod P")
            assertEquals(row[8], a.subMod(b, q).toHex(), "difference mod Q")
        }
    }

    @Test
    fun inverseAgreesWithJava() {
        for (row in KeksGolden.bigInverse) {
            val a = bigOf(row[0])
            if (row[1].isEmpty()) {
                assertFailsWith<IllegalArgumentException>("zero has no inverse mod P") {
                    a.mod(p).modInverse(p)
                }
            } else {
                assertEquals(row[1], a.mod(p).modInverse(p).toHex(), "inverse mod P of ${row[0]}")
            }
            if (row[2].isEmpty()) {
                assertFailsWith<IllegalArgumentException>("zero has no inverse mod Q") {
                    a.mod(q).modInverse(q)
                }
            } else {
                assertEquals(row[2], a.mod(q).modInverse(q).toHex(), "inverse mod Q of ${row[0]}")
            }
        }
    }

    @Test
    fun powerAgreesWithJava() {
        for (row in KeksGolden.bigModPow) {
            val a = bigOf(row[0])
            val e = bigOf(row[1])
            assertEquals(row[2], a.modPow(e, p).toHex(), "power mod P")
            assertEquals(row[3], a.modPow(e, q).toHex(), "power mod Q")
        }
    }

    @Test
    fun bytesRoundTrip() {
        for (row in KeksGolden.bigMod) {
            val reduced = bigOf(row[1])
            val bytes = reduced.toBytes(32)
            assertEquals(32, bytes.size)
            assertEquals(reduced.toHex(), KeksUInt.fromBytes(bytes).toHex())
        }
    }

    @Test
    fun tooBigForTheRequestedLengthIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            KeksUInt.fromHex("ff00000000000000000000000000000000").toBytes(16)
        }
    }

    @Test
    fun shiftsMatchTheObviousAnswer() {
        val value = bigOf("123456789abcdef0fedcba9876543210")
        for (bits in 0..130) {
            val up = value.shl(bits)
            assertEquals(value.toHex(), up.shr(bits).toHex(), "shift by $bits and back")
        }
        assertEquals("0", value.shr(1000).toHex())
    }

    @Test
    fun divisionIsRightForDivisorsTheHandshakeNeverUses() {
        // Every value above divides by P or by Q, and both of those are eight words long with the
        // top bit set. That leaves two branches of the divider untouched: the single word divisor,
        // and the normalising shift. They are checked here against the definition of division
        // rather than against Java, because Java was never asked these questions.
        val divisors = listOf(
            "1", "2", "3", "ff", "100", "ffffffff", "100000000", "7fffffff",
            "0001000000000000", "123456789", "8000000000000000", "0fffffffffffffffffffffffffffffff",
        ).map { bigOf(it) }
        val values = KeksGolden.bigMod.map { bigOf(it[0]) } + listOf(KeksUInt.ZERO, KeksUInt.ONE, p, q)
        for (value in values) {
            for (divisor in divisors) {
                val (quotient, remainder) = value.divRem(divisor)
                assertTrue(remainder < divisor, "remainder of ${value.toHex()} / ${divisor.toHex()}")
                assertEquals(
                    value.toHex(),
                    (quotient * divisor + remainder).toHex(),
                    "quotient times divisor plus remainder for ${value.toHex()} / ${divisor.toHex()}",
                )
            }
        }
    }

    @Test
    fun dividingByZeroIsRefused() {
        assertFailsWith<IllegalArgumentException> { bigOf("ff").divRem(KeksUInt.ZERO) }
    }

    @Test
    fun comparisonIsByValue() {
        assertTrue(bigOf("ff") < bigOf("100"))
        assertTrue(bigOf("100") > bigOf("ff"))
        assertEquals(bigOf("0"), KeksUInt.ZERO)
        assertTrue(KeksUInt.ZERO.isZero)
        assertTrue(KeksUInt.ONE.isOne)
    }
}
