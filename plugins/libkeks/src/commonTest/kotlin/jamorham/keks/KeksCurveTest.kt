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

import jamorham.keks.crypto.KeksEcPoint
import jamorham.keks.crypto.KeksP256
import jamorham.keks.crypto.KeksUInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The secp256r1 point arithmetic against values BouncyCastle gave on Android.
 */
class KeksCurveTest {

    @Test
    fun curveNumbersMatchBouncyCastle() {
        assertEquals(KeksGolden.curveQ, KeksP256.N.toHex())
        assertEquals(KeksGolden.curveQM1, Curve.QM1.toHex())
        assertEquals(KeksGolden.curveP, KeksP256.P.toHex())
        assertEquals(KeksGolden.curveA, KeksP256.A.toHex())
        assertEquals(KeksGolden.curveB, KeksP256.B.toHex())
        assertEquals(KeksGolden.curveG, KeksP256.G.getEncoded().hex())
        assertEquals(KeksGolden.curveBits, Curve.CURVE_BITS)
        assertEquals(KeksGolden.fieldSize, Curve.FIELD_SIZE)
        assertEquals(KeksGolden.packetSize, Curve.PACKET_SIZE)
        assertEquals("secp256r1", Curve.name)
    }

    @Test
    fun baseScalarMultiplicationMatches() {
        for (row in KeksGolden.scalarMulG) {
            assertEquals(row[1], KeksP256.G.multiply(bigOf(row[0])).getEncoded().hex(), "k=${row[0]}")
        }
    }

    @Test
    fun additionMatches() {
        for (row in KeksGolden.pointAdd) {
            assertEquals(row[2], pointOf(row[0]).add(pointOf(row[1])).getEncoded().hex())
        }
    }

    @Test
    fun subtractionMatches() {
        for (row in KeksGolden.pointSubtract) {
            assertEquals(row[2], pointOf(row[0]).subtract(pointOf(row[1])).getEncoded().hex())
        }
    }

    @Test
    fun multiplicationOfAnyPointMatches() {
        for (row in KeksGolden.pointMultiply) {
            assertEquals(row[2], pointOf(row[0]).multiply(bigOf(row[1])).getEncoded().hex())
        }
    }

    @Test
    fun coordinateBytesMatch() {
        for (row in KeksGolden.jecPointBytes) {
            assertEquals(row[1], JECPoint(pointOf(row[0])).toBytes().hex())
        }
    }

    @Test
    fun infinityBehavesTheWayBouncyCastleDid() {
        val g = KeksP256.G
        assertTrue(g.multiply(KeksUInt.ZERO).isInfinity)
        assertTrue(g.add(g.negate()).isInfinity)
        assertEquals(g.getEncoded().hex(), g.add(KeksEcPoint.INFINITY).getEncoded().hex())
        assertEquals("00", KeksEcPoint.INFINITY.getEncoded().hex())
        assertFailsWith<NullPointerException> { KeksEcPoint.INFINITY.xEncoded() }
    }

    @Test
    fun doublingAgreesWithAddingToItself() {
        for (row in KeksGolden.scalarMulG) {
            val point = pointOf(row[1])
            assertEquals(point.add(point).getEncoded().hex(), point.twice().getEncoded().hex())
        }
    }

    @Test
    fun aPointOffTheCurveIsOnlyRefusedWhenItIsUsed() {
        // BouncyCastle's createPoint did not check, and multiply did. The ONE+ driver depends on
        // that: it catches IllegalStateException to call an out of step handshake a failed one.
        val good = pointOf(KeksGolden.curveG)
        val bent = KeksEcPoint.createPoint(good.x, good.y.subMod(KeksUInt.ONE, KeksP256.P))
        assertFailsWith<IllegalStateException> { bent.multiply(KeksUInt.of(5)) }
    }

    @Test
    fun aCoordinateOutsideTheFieldIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            KeksEcPoint.createPoint(KeksP256.P, KeksUInt.ONE)
        }
    }
}
