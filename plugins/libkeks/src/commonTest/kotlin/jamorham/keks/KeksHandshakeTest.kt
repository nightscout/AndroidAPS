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

import jamorham.keks.crypto.KeksReplay
import jamorham.keks.crypto.KeksUInt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A whole four digit pairing, replayed against the one the Java and BouncyCastle code ran.
 *
 * The other side of the recorded handshake was a second KEKS context driven by the same Java
 * classes, so these bytes are a real J-PAKE exchange and not something made up for a test. The six
 * random numbers the Java drew were recorded with it, which is what makes the replay exact: every
 * message this port writes has to be the same bytes, and the key it ends up with has to be the key
 * the other side worked out.
 */
class KeksHandshakeTest {

    private val t = KeksGolden.transcript

    @AfterTest
    fun tidy() = KeksReplay.clear()

    @Test
    fun theWholeHandshakeIsReproducedByteForByte() {
        val plugin = Plugin(t.getValue("password"))
        // The two key pairs are normally drawn at random inside the constructor. They are replaced
        // here with the ones the Java drew, which the context holds openly.
        plugin.context.keyA = KeyPair(KeksUInt.fromHex(t.getValue("scalarA")))
        plugin.context.KeyB = KeyPair(KeksUInt.fromHex(t.getValue("scalarB")))
        KeksReplay.clear()
        KeksReplay.exponents.addLast(KeksUInt.fromHex(t.getValue("exponent1")))
        KeksReplay.exponents.addLast(KeksUInt.fromHex(t.getValue("exponent2")))
        KeksReplay.exponents.addLast(KeksUInt.fromHex(t.getValue("exponent3")))
        KeksReplay.randomKeys.addLast(hexBytes(t.getValue("tokenKey")))

        plugin.amConnected()

        val start = assertNotNull(plugin.aNext())
        assertEquals(t.getValue("a0_cmd"), start[0]!!.hex(), "the round start command")
        assertEquals(null, start[1])

        assertTrue(plugin.receivedData(hexBytes(t.getValue("bobRound1"))), "round 1 from the sensor")
        val round1 = assertNotNull(plugin.aNext())
        assertEquals(t.getValue("a1_cmd"), round1[0]!!.hex())
        assertEquals(t.getValue("a1_data"), round1[1]!!.hex(), "our round 1 packet")

        assertTrue(plugin.receivedData(hexBytes(t.getValue("bobRound2"))), "round 2 from the sensor")
        val round2 = assertNotNull(plugin.aNext())
        assertEquals(t.getValue("a2_cmd"), round2[0]!!.hex())
        assertEquals(t.getValue("a2_data"), round2[1]!!.hex(), "our round 2 packet")

        assertTrue(plugin.receivedData(hexBytes(t.getValue("bobRound3"))), "round 3 from the sensor")
        val round3 = assertNotNull(plugin.aNext())
        assertEquals(t.getValue("a3_auth"), round3[0]!!.hex(), "the authentication request")
        assertEquals(t.getValue("a3_data"), round3[1]!!.hex(), "our round 3 packet")

        assertTrue(plugin.receivedResponse(hexBytes(t.getValue("bobAuthResponse"))), "the sensor's challenge")
        val challenge = assertNotNull(plugin.aNext())
        assertEquals(t.getValue("a4_authChallenge"), challenge[0]!!.hex(), "our answer to the challenge")

        assertTrue(plugin.receivedResponse(hexBytes(t.getValue("authStatus"))), "authentication status")
        val done = assertNotNull(plugin.aNext())
        assertEquals(t.getValue("a5_len").toInt(), done.size, "a single entry means authenticated")
        assertEquals(t.getValue("a5_cmd"), done[0]!!.hex())

        assertEquals(t.getValue("persistence1"), plugin.getPersistence(1)!!.hex(), "the shared key")
        assertEquals(t.getValue("sharedKeyShort"), Calc.getShortSharedKey(plugin.context)!!.hex())
        assertEquals(t.getValue("sharedKeyFull"), Calc.getSharedKey(plugin.context)!!.hex())
        assertTrue(KeksReplay.exponents.isEmpty(), "every recorded random number was used")
        assertTrue(KeksReplay.randomKeys.isEmpty(), "every recorded random number was used")
    }

    @Test
    fun ourPacketsAreAcceptedByTheSameCheckThatAcceptsTheSensorsOnes() {
        // The sensor side of the recording checked each of our packets with the Java code and said
        // yes. The same check here has to say yes as well, in both directions.
        assertEquals("true", t.getValue("bobValidatesAliceRound1"))
        assertEquals("true", t.getValue("bobValidatesAliceRound2"))
        assertEquals("true", t.getValue("bobValidatesAliceRound3"))
        val ours = Packet.parse(hexBytes(t.getValue("a1_data")))!!
        assertTrue(Calc.validateRound1Packet(ours, Config.Get.ALICE.bytes), "our own round 1 proof")
        val theirs = Packet.parse(hexBytes(t.getValue("bobRound1")))!!
        assertTrue(Calc.validateRound1Packet(theirs, Config.Get.BOB.bytes), "the sensor's round 1 proof")
        assertTrue(!Calc.validateRound1Packet(theirs, Config.Get.ALICE.bytes), "under the wrong party")
    }

    @Test
    fun aFreshHandshakeAlsoWorksWithRealRandomNumbers() {
        // Nothing is pinned here. It only proves the drawing paths are wired up and that a packet
        // this port builds passes this port's own check - which the replay above ties to Java.
        val plugin = Plugin("1234")
        plugin.amConnected()
        assertNotNull(plugin.aNext())
        val first = Calc.getRound1Packet(plugin.context)
        assertTrue(Calc.validateRound1Packet(first, Config.Get.ALICE.bytes))
        assertEquals(KeksGolden.packetSize, first.output().size)
    }
}
