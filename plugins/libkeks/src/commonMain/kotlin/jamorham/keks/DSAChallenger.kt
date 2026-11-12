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

import jamorham.keks.crypto.KeksEcdsa
import jamorham.keks.crypto.KeksUInt
import jamorham.keks.util.Log

/**
 * JamOrHam
 *
 * Signs a challenge from the sensor with the key out of the configuration QR code.
 *
 * On Android this extended BouncyCastle's `SignatureSpi.ecDSA256`, took the DER sequence it
 * produced and immediately pulled the two 32 byte numbers back out of it. The two numbers are all
 * that goes on the wire, so this writes them directly and the bytes are the same.
 */
class DSAChallenger(private val key: KeksUInt?) {

    fun response(challenge: ByteArray): ByteArray? {
        try {
            val privateKey = key ?: return null
            return KeksEcdsa.sign(privateKey, challenge)
        } catch (e: Exception) {
            Log.l("Exception in challenger response: $e")
        }
        return null
    }
}
