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

import jamorham.keks.crypto.KeksReplay
import jamorham.keks.crypto.keksRandomBytes

/**
 * JamOrHam
 *
 * Generic utility methods
 */
object Util {

    private val hexArray = "0123456789ABCDEF".toCharArray()

    fun bytesToHex(bytes: ByteArray?): String {
        if (bytes == null) return ""
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return hexChars.concatToString()
    }

    fun arrayAppend(existing: ByteArray, addon: ByteArray): ByteArray {
        val bigger = existing.copyOf(existing.size + addon.size)
        addon.copyInto(bigger, existing.size, 0, addon.size)
        return bigger
    }

    fun arrayReduce(existing: ByteArray?, length: Int): ByteArray? {
        if (existing == null) return null
        val dest = ByteArray(length)
        existing.copyInto(dest, 0, 0, length)
        return dest
    }

    fun tolerantHexStringToByteArray(str: String): ByteArray? =
        hexStringToByteArray(Regex("[^A-F0-9]").replace(str.uppercase(), ""))

    /**
     * Hex text to bytes, **with the two halves of each byte the other way round**.
     *
     * That swap is deliberate upstream, not a slip: it is what makes the KEKS party labels in
     * `Config` read as "client" and "server". Anything that is not a hex digit counts as -1, so
     * "zz" gives 0xEF rather than an error, and a text of an odd length ends up running off the
     * end and answering null. All of that is relied on, so it is kept exactly.
     */
    fun hexStringToByteArray(str: String): ByteArray? {
        return try {
            val text = str.uppercase().trim()
            if (text.isEmpty()) return null
            val len = text.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] = ((digit(text[i + 1]) shl 4) + digit(text[i])).toByte()
                i += 2
            }
            data
        } catch (e: Exception) {
            null
        }
    }

    /** Java's `Character.digit(c, 16)`, which answers -1 for anything that is not a hex digit. */
    private fun digit(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'A'..'F' -> c - 'A' + 10
        in 'a'..'f' -> c - 'a' + 10
        else -> -1
    }

    fun intToByteArray(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    fun getRandomKey(): ByteArray {
        KeksReplay.nextRandomKey()?.let { return it }
        val keybytes = ByteArray(16)
        keksRandomBytes(keybytes)
        return keybytes
    }
}
