/*
 * KEKS J-PAKE / ExtraData authentication for Dexcom G7 / ONE+.
 *
 * Translated to Kotlin from NightscoutFoundation/xDrip
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
package jamorham.keks.crypto

/**
 * The two random draws of a handshake, held back so a recorded one can be played again.
 *
 * **This is for this module's own tests and nothing else.** It is `internal`, so no other module
 * can reach it, and both queues are empty unless a test fills them. When a queue is empty the real
 * secure generator is used, which is the only path production ever takes.
 *
 * It exists because the proof this port needs is a handshake reproduced byte for byte against one
 * the Java and BouncyCastle code produced on Android, and a handshake is only reproducible if the
 * random numbers in it are the same ones.
 *
 * Never fill these outside a test. A handshake with a known random number is a handshake anyone
 * watching can work the shared key out of.
 */
internal object KeksReplay {

    /** Values `Curve.getExponent` hands out instead of drawing, oldest first. */
    val exponents: ArrayDeque<KeksUInt> = ArrayDeque()

    /** Values `Util.getRandomKey` hands out instead of drawing, oldest first. */
    val randomKeys: ArrayDeque<ByteArray> = ArrayDeque()

    fun nextExponent(): KeksUInt? = exponents.removeFirstOrNull()

    fun nextRandomKey(): ByteArray? = randomKeys.removeFirstOrNull()

    fun clear() {
        exponents.clear()
        randomKeys.clear()
    }
}
