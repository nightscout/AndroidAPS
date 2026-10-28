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
import jamorham.keks.message.AuthChallengeTxMessage
import jamorham.keks.message.AuthRequestTxMessage2
import jamorham.keks.message.AuthStatusRxMessage
import jamorham.keks.message.CertInfoRxMessage
import jamorham.keks.message.SignChallengeTxMessage
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The bytes of each message, which is the part of a port that breaks quietly.
 */
class KeksMessageTest {

    @AfterTest
    fun tidy() = KeksReplay.clear()

    @Test
    fun transmittedMessagesMatchJava() {
        val byName = KeksGolden.txMessages.associate { it[0] to Pair(it[1], it[2]) }
        val authChallenge = byName.getValue("AuthChallengeTx")
        assertEquals(authChallenge.second, AuthChallengeTxMessage(hexBytes(authChallenge.first)).byteSequence!!.hex())
        val signChallenge = byName.getValue("SignChallengeTx")
        assertEquals(signChallenge.second, SignChallengeTxMessage(hexBytes(signChallenge.first)).byteSequence!!.hex())
    }

    @Test
    fun authRequestMatchesJava() {
        for (row in KeksGolden.authRequestTx2) {
            KeksReplay.clear()
            KeksReplay.randomKeys.addLast(hexBytes("00112233445566778899aabbccddeeff"))
            val message = AuthRequestTxMessage2(8, row[0].toBoolean(), hexBytes(row[1]))
            assertEquals(row[2], message.singleUseToken.hex(), "token for alt=${row[0]} chal=${row[1]}")
            assertEquals(row[3], message.byteSequence!!.hex(), "message for alt=${row[0]} chal=${row[1]}")
        }
    }

    @Test
    fun authStatusIsReadTheSameWay() {
        for (row in KeksGolden.authStatusRx) {
            val message = AuthStatusRxMessage(hexBytes(row[0]))
            assertEquals(row[1].toInt(), message.authenticated, "authenticated of ${row[0]}")
            assertEquals(row[2].toInt(), message.bonded, "bonded of ${row[0]}")
            assertEquals(row[3].toBoolean(), message.isAuthenticated())
            assertEquals(row[4].toBoolean(), message.isBonded())
            assertEquals(row[5].toBoolean(), message.needsRefresh())
        }
    }

    @Test
    fun certificateInfoIsReadTheSameWay() {
        for (row in KeksGolden.certInfoRx) {
            val message = CertInfoRxMessage(hexBytes(row[0]))
            assertEquals(row[1].toInt(), message.state, "state of ${row[0]}")
            assertEquals(row[2].toInt(), message.which, "which of ${row[0]}")
            assertEquals(row[3].toInt(), message.size, "size of ${row[0]}")
            assertEquals(row[4].toBoolean(), message.valid(), "valid of ${row[0]}")
        }
    }
}
