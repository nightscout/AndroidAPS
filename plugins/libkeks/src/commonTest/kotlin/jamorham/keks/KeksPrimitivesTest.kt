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

import jamorham.keks.util.AESwrapper
import jamorham.keks.util.Util
import jamorham.libkeks.Digest
import jamorham.libkeks.SHA256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The parts of the handshake that have one right answer: the digest, the block cipher and the
 * small helpers. Every value came from the Java that ships on Android today.
 */
class KeksPrimitivesTest {

    @Test
    fun digestMatchesJava() {
        for (row in KeksGolden.sha256) {
            assertEquals(row[1], SHA256.hash(hexBytes(row[0])).hex(), "input of ${row[0].length / 2} bytes")
        }
        assertTrue(KeksGolden.sha256.size >= 18)
    }

    @Test
    fun runningDigestMatchesJava() {
        for (row in KeksGolden.digest) {
            val destination = ByteArray(32)
            val digest = Digest(destination)
            for (chunk in row[0].split('|')) {
                if (chunk.isNotEmpty()) digest.update(hexBytes(chunk))
            }
            if (row[0].isEmpty()) digest.update(ByteArray(0))
            digest.doFinal()
            assertEquals(row[1], destination.hex(), "chunks ${row[0]}")
        }
    }

    @Test
    fun blockCipherMatchesJava() {
        for (row in KeksGolden.aesEcb) {
            assertEquals(row[2], AESwrapper(hexBytes(row[0])).aes(hexBytes(row[1]))!!.hex())
        }
    }

    @Test
    fun challengeHashMatchesJava() {
        for (row in KeksGolden.calculateHash) {
            val context = Context()
            context.savedKey = hexBytes(row[0])
            context.challenge = hexBytes(row[1])
            assertEquals(row[2], Calc.calculateHash(context)!!.hex())
        }
    }

    @Test
    fun passwordHandlingMatchesJava() {
        for (row in KeksGolden.passwordBytes) {
            val context = Context()
            context.password = row[0]
            assertEquals(row[1], context.ensurePasswordBytes().hex(), "password of ${row[0].length} digits")
            assertEquals(row[2], context.getPasswordBigInteger().toHex())
        }
    }

    @Test
    fun configurationBytesMatchJava() {
        val byName = KeksGolden.config.associate { it[0] to it[1] }
        assertEquals(Config.Get.entries.size, byName.size)
        for (entry in Config.Get.entries) {
            assertEquals(byName[entry.name], entry.bytes.hex(), "config ${entry.name}")
        }
        // The nibble swap in hexStringToByteArray is what makes these read as words.
        assertEquals("client", Config.Get.ALICE.bytes.decodeToString())
        assertEquals("server", Config.Get.BOB.bytes.decodeToString())
    }

    @Test
    fun hexHelpersMatchJava() {
        for (row in KeksGolden.hexStringToByteArray) {
            val result = Util.hexStringToByteArray(row[0])
            if (row[1] == "null") assertNull(result, "input ${row[0]}") else assertEquals(row[1], result!!.hex())
        }
        for (row in KeksGolden.tolerantHexStringToByteArray) {
            val result = Util.tolerantHexStringToByteArray(row[0])
            if (row[1] == "null") assertNull(result, "input ${row[0]}") else assertEquals(row[1], result!!.hex())
        }
        for (row in KeksGolden.bytesToHex) {
            assertEquals(row[1], Util.bytesToHex(hexBytes(row[0])))
        }
        assertEquals("", Util.bytesToHex(null))
        for (row in KeksGolden.intToByteArray) {
            assertEquals(row[1], Util.intToByteArray(row[0].toInt()).hex())
        }
    }

    @Test
    fun arrayHelpersMatchJava() {
        assertEquals("0102030405", Util.arrayAppend(hexBytes("0102"), hexBytes("030405")).hex())
        assertEquals("0102", Util.arrayAppend(hexBytes("0102"), ByteArray(0)).hex())
        assertEquals("0102", Util.arrayReduce(hexBytes("01020304"), 2)!!.hex())
        assertNull(Util.arrayReduce(null, 2))
    }
}
