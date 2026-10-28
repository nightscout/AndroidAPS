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
import kotlin.concurrent.Volatile

/**
 * JamOrHam
 */
open class BaseMessage {

    var data: KeksBuffer? = null

    @Volatile
    var byteSequence: ByteArray? = null

    internal fun init(opcode: Byte, length: Int) {
        data = KeksBuffer.allocate(length, littleEndian = true)
        data!!.put(opcode)
        if (length == 1) {
            fillByteSequence()
        }
    }

    /** Was `getByteSequence()` in the Java; renamed because [byteSequence] now has its own one. */
    fun fillByteSequence(): ByteArray {
        val array = data!!.array()
        byteSequence = array
        return array
    }
}
