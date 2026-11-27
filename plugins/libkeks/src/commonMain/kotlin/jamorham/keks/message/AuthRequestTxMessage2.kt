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
import jamorham.keks.util.Util.getRandomKey

/**
 * JamOrHam
 */
class AuthRequestTxMessage2 : BaseMessage {

    val opcode: Byte = 0x02

    var singleUseToken: ByteArray = ByteArray(0)

    constructor(tokenSize: Int) : this(tokenSize, false, ByteArray(0))

    constructor(tokenSize: Int, alt: Boolean, chal: ByteArray) : this(
        tokenSize,
        (if (alt) endByteAlt else endByteStd).toInt() + (if (chal.size > 2) chal[2].toInt() else 0),
    )

    constructor(tokenSize: Int, slot: Int) : super() {
        init(opcode, tokenSize + 2)
        val randomBytes = getRandomKey()
        val bb = KeksBuffer.allocate(tokenSize)
        bb.put(randomBytes, 0, tokenSize)
        singleUseToken = bb.array()
        data!!.put(singleUseToken)
        data!!.put(slot.toByte())
        byteSequence = data!!.array()
    }

    companion object {

        private const val endByteStd: Byte = 0x2
        private const val endByteAlt: Byte = 0x1
    }
}
