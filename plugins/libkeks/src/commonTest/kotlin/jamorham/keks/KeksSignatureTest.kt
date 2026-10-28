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

import jamorham.keks.crypto.KeksDer
import jamorham.keks.crypto.KeksEcdsa
import jamorham.keks.crypto.KeksP256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The signature the sensor asks for during four digit pairing.
 *
 * BouncyCastle drew a fresh random number for every signature, so two signatures over the same
 * challenge were already different bytes on Android and no signature can be pinned as it stands.
 * What is pinned instead is the random number itself, worked back out of a real Java signature
 * with k = (e + r * d) / s, and checked at the time it was recorded. Given that one number the
 * whole signing path is fixed, so the bytes below really are Java's.
 */
class KeksSignatureTest {

    private val privateKey = KeksDer.readPrivateKeyScalar(hexBytes(KeksGolden.partC))

    @Test
    fun theKeyIsReadOutOfThePkcs8TheSameWay() {
        assertEquals(KeksGolden.partCScalar, privateKey.toHex())
        assertEquals(KeksGolden.partCPublic, KeksP256.G.multiply(privateKey).getEncoded().hex())
        assertEquals(KeksGolden.partCScalar, KeyPair.fromBytes(hexBytes(KeksGolden.partC))!!.toHex())
    }

    @Test
    fun badKeyMaterialAnswersNullRatherThanThrowing() {
        assertEquals(null, KeyPair.fromBytes(hexBytes("30030201ff")))
        assertEquals(null, KeyPair.fromBytes(ByteArray(0)))
    }

    @Test
    fun signingWithTheRecordedRandomNumberGivesJavaBytes() {
        for (row in KeksGolden.ecdsaSign) {
            assertEquals("true", row[5], "the recorded k was checked when it was written down")
            assertEquals("false", row[2], "BouncyCastle signatures were never repeatable")
            val signature = KeksEcdsa.signWith(privateKey, hexBytes(row[0]), bigOf(row[4]))
            assertNotNull(signature)
            assertEquals(row[1], signature.hex(), "signature over ${row[0]}")
        }
    }

    @Test
    fun javaSignaturesVerifyAndTamperedOnesDoNot() {
        val publicKey = KeksP256.G.multiply(privateKey)
        for (row in KeksGolden.ecdsaVerify) {
            assertTrue(KeksEcdsa.verify(publicKey, hexBytes(row[0]), hexBytes(row[1])), "java signature")
            assertFalse(KeksEcdsa.verify(publicKey, hexBytes(row[2]), hexBytes(row[1])), "wrong message")
            assertFalse(KeksEcdsa.verify(publicKey, hexBytes(row[0]), hexBytes(row[2])), "bent signature")
        }
    }

    @Test
    fun ourOwnSignaturesVerify() {
        val publicKey = KeksP256.G.multiply(privateKey)
        for (row in KeksGolden.ecdsaSign) {
            val challenge = hexBytes(row[0])
            val signature = DSAChallenger(privateKey).response(challenge)
            assertNotNull(signature)
            assertEquals(64, signature.size)
            assertTrue(KeksEcdsa.verify(publicKey, challenge, signature))
        }
    }

    @Test
    fun challengerTakesSixteenBytesFromOffsetTwo() {
        for (row in KeksGolden.challenger) {
            val incoming = hexBytes(row[0])
            val signature = Calc.challenger(hexBytes(KeksGolden.partC), incoming)
            assertNotNull(signature)
            // The Java bytes cannot be compared because of the random number, but the message that
            // was signed can: it is exactly bytes 2 through 17 of what arrived.
            val publicKey = KeksP256.G.multiply(privateKey)
            assertTrue(KeksEcdsa.verify(publicKey, incoming.copyOfRange(2, 18), signature))
            assertFalse(KeksEcdsa.verify(publicKey, incoming.copyOfRange(1, 17), signature))
        }
    }

    @Test
    fun aShortChallengeFailsTheWayTheJavaDid() {
        var raised = false
        try {
            Calc.challenger(hexBytes(KeksGolden.partC), hexBytes("050101"))
        } catch (e: IndexOutOfBoundsException) {
            raised = true
        }
        assertTrue(raised, "a three byte packet where a challenge was expected has to raise")
    }
}
