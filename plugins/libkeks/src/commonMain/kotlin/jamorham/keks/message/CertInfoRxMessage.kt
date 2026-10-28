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
class CertInfoRxMessage(packet: ByteArray) : BaseMessage() {

    var size: Int = -1
        private set

    var which: Int = -1
        private set

    var state: Int = 0
        private set

    init {
        val buffer = KeksBuffer.wrap(packet, littleEndian = true)
        data = buffer
        if (packet.size == 7) {
            if (buffer.get().toInt() == opcode.toInt()) {
                state = buffer.get().toInt()
                which = buffer.get().toInt()
                // might be an int but just ignore later bytes
                size = buffer.getShort().toInt()
            }
        }
    }

    fun valid(): Boolean = size > 0 && state == 0 && which >= 0

    companion object {

        const val opcode: Byte = 0x0b
    }
}
