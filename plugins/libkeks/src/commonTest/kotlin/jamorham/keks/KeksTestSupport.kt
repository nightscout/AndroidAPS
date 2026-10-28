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

import jamorham.keks.crypto.KeksEcPoint
import jamorham.keks.crypto.KeksUInt

/** Lower case hex, which is how every value in `KeksGolden` is written. */
internal fun ByteArray.hex(): String {
    val digits = "0123456789abcdef"
    val out = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        out.append(digits[v ushr 4]).append(digits[v and 0x0F])
    }
    return out.toString()
}

internal fun hexBytes(text: String): ByteArray {
    require(text.length % 2 == 0) { "odd hex: $text" }
    val out = ByteArray(text.length / 2)
    for (i in out.indices) {
        out[i] = ((hexDigit(text[i * 2]) shl 4) or hexDigit(text[i * 2 + 1])).toByte()
    }
    return out
}

private fun hexDigit(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else        -> throw IllegalArgumentException("not hex: $c")
}

internal fun bigOf(text: String): KeksUInt = KeksUInt.fromHex(text)

internal fun pointOf(encoded: String): KeksEcPoint = KeksEcPoint.decodePoint(hexBytes(encoded))
