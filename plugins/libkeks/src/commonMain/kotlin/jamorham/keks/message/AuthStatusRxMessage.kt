/*
 * KEKS J-PAKE / ExtraData authentication for Dexcom G7 / ONE+.
 *
 * Translated to Kotlin from NightscoutFoundation/xDrip
 *   https://github.com/NightscoutFoundation/xDrip
 *   pin commit 1e86d9a2a52577ed2c30fbf7b69d75fd56e6918f (tag 2026.07.15)
 * Authorship per upstream comments: JamOrHam / jamorham. Upstream carries no
 * formal per-file "Copyright (C) <year> <name>" line; none is asserted or
 * invented here. See plugins/libkeks/NOTICE.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Part of AndroidAPS (AGPL-3.0-or-later); these KEKS sources retain their
 * upstream GPL-3.0 terms. Free software, distributed WITHOUT ANY WARRANTY.
 * See https://www.gnu.org/licenses/gpl-3.0.html
 */
package jamorham.keks.message

import jamorham.keks.util.KeksBuffer

/**
 * JamOrHam
 */
class AuthStatusRxMessage(packet: ByteArray) : BaseMessage() {

    var authenticated: Int = 0
    var bonded: Int = 0

    init {
        if (packet.size >= 3) {
            if (packet[0].toInt() == opcode) {
                val buffer = KeksBuffer.wrap(packet)
                data = buffer
                authenticated = buffer.get(1).toInt()
                bonded = buffer.get(2).toInt()
            }
        }
    }

    fun isAuthenticated(): Boolean = authenticated == 1

    fun isBonded(): Boolean = bonded == 1

    fun needsRefresh(): Boolean = bonded == 3

    companion object {

        const val opcode = 0x5
    }
}
