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

import jamorham.keks.Plugin

/**
 * JamOrHam
 */
class CertInfoTxMessage : BaseMessage() {

    companion object {

        const val opcode: Byte = 0x0b

        fun expectMyCert(plugin: Plugin, which: Int): ByteArray {
            val p = CertInfoTxMessage()
            val c = plugin.context
            p.init(opcode, 6)
            p.data!!.put(which.toByte())
            p.data!!.putInt(if (which == 0) c.partA!!.size else c.partB!!.size)
            return p.fillByteSequence()
        }

        fun expectMyCert1(plugin: Plugin): ByteArray = expectMyCert(plugin, 0)

        fun expectMyCert2(plugin: Plugin): ByteArray = expectMyCert(plugin, 1)
    }
}
