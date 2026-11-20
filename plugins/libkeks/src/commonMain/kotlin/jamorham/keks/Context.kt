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
package jamorham.keks

import jamorham.keks.Config.Get.PREFIX
import jamorham.keks.crypto.KeksUInt
import jamorham.keks.util.Log
import jamorham.keks.util.Util.arrayAppend
import kotlin.concurrent.Volatile

/**
 * JamOrHam
 *
 * KEKS context
 */
class Context {

    @Volatile var keyA: KeyPair? = null
    @Volatile var KeyB: KeyPair? = null
    @Volatile var password: String? = null
    @Volatile var passwordBytes: ByteArray? = null
    @Volatile var alice: ByteArray? = null
    @Volatile var bob: ByteArray? = null
    @Volatile var challenge: ByteArray? = null
    @Volatile var savedKey: ByteArray? = null
    @Volatile var packet: Array<Packet?> = arrayOfNulls(4)
    @Volatile var sequence: Int = 0

    @Volatile var partA: ByteArray? = null
    @Volatile var partB: ByteArray? = null
    @Volatile var partC: ByteArray? = null

    fun validateParts(): Boolean {
        val a = partA
        val b = partB
        val c = partC
        return a != null && b != null && c != null && a.size > 100 && b.size > 100 && c.size > 100
    }

    fun reset() {
        savedKey = null
        packet.fill(null)
    }

    fun resetIfNotReady() {
        sequence = 0
        if (savedKey == null && getRound3Packet() == null) {
            reset()
        }
    }

    /**
     * Was `getPasswordBytes()` in the Java. Renamed because [passwordBytes] is now a property of
     * its own and the two would be the same method once compiled.
     */
    fun ensurePasswordBytes(): ByteArray {
        val pw = password
        if (pw == null) {
            Log.l("Context password not set")
            throw RuntimeException()
        }
        var bytes = passwordBytes
        if (bytes == null) {
            bytes = pw.encodeToByteArray()
            if (pw.length == 6) {
                bytes = arrayAppend(PREFIX.bytes, bytes)
            }
            passwordBytes = bytes
        }
        return bytes
    }

    fun getPasswordBigInteger(): KeksUInt = KeksUInt.fromBytes(ensurePasswordBytes())

    fun getRound1Packet(): Packet? = packet[1]

    fun getRound2Packet(): Packet? = packet[2]

    fun getRound3Packet(): Packet? = packet[3]
}
