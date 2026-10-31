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
package jamorham.libkeks

/**
 * JamOrHam
 *
 * Collects everything it is given and writes the SHA-256 of the lot into [destination].
 */
class Digest(private val destination: ByteArray) {

    private var store = ByteArray(0)

    fun update(data: ByteArray) {
        val n = ByteArray(store.size + data.size)
        data.copyInto(n, store.size, 0, data.size)
        store.copyInto(n, 0, 0, store.size)
        store = n
    }

    fun doFinal() {
        val result = SHA256.hash(store)
        result.copyInto(destination, 0, 0, destination.size)
    }
}
