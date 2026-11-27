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
package jamorham.keks.util

import jamorham.keks.Curve

/**
 * JamOrHam
 *
 * A map that hands out a fresh, field sized, zero filled array for any key it has not seen.
 * [mget] returns the stored array itself, so the caller can read bytes straight into it.
 */
class ByteArrayHashMap {

    private val backing = HashMap<Int, ByteArray>()

    fun mget(key: Int): ByteArray {
        val v = backing[key]
        if (v == null) {
            val fresh = ByteArray(Curve.FIELD_SIZE)
            backing[key] = fresh
            return fresh
        }
        return v
    }
}
