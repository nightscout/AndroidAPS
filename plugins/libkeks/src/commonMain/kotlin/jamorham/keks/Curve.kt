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

import jamorham.keks.crypto.KeksEcPoint
import jamorham.keks.crypto.KeksEcdsa
import jamorham.keks.crypto.KeksP256
import jamorham.keks.crypto.KeksReplay
import jamorham.keks.crypto.KeksUInt

/**
 * JamOrHam
 *
 * KEKS Elliptic Curve
 */
object Curve {

    const val name = "secp256r1"

    val G: KeksEcPoint get() = KeksP256.G

    /** The order of the base point. */
    val Q: KeksUInt = KeksP256.N

    val QM1: KeksUInt = KeksP256.N - KeksUInt.ONE

    val CURVE_BITS: Int = KeksP256.fieldBits

    val FIELD_SIZE: Int = (CURVE_BITS + 7) / 8

    val PACKET_SIZE: Int = FIELD_SIZE * 5

    fun getExponent(): KeksUInt =
        KeksReplay.nextExponent() ?: KeksEcdsa.randomInRange(KeksUInt.ONE, QM1)
}
