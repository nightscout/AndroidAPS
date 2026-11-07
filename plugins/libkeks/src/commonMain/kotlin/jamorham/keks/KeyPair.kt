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

import jamorham.keks.crypto.KeksDer
import jamorham.keks.crypto.KeksEcPoint
import jamorham.keks.crypto.KeksEcdsa
import jamorham.keks.crypto.KeksP256
import jamorham.keks.crypto.KeksUInt
import jamorham.keks.util.Log

/**
 * JamOrHam
 *
 * KeyPair data holder
 */
class KeyPair(val privateKey: KeksUInt, val publicKey: KeksEcPoint) {

    /** A pair built from one secret number, with the public point worked out from it. */
    constructor(scalar: KeksUInt) : this(scalar, KeksP256.G.multiply(scalar))

    /** A fresh pair, with the secret number drawn from the platform's secure generator. */
    constructor() : this(KeksEcdsa.randomInRange(KeksUInt.ONE, Curve.QM1))

    /** An X.509 public key and a PKCS#8 private key, the way the configuration QR code holds them. */
    constructor(publicBytes: ByteArray, privateBytes: ByteArray) : this(
        KeksDer.readPrivateKeyScalar(privateBytes),
        KeksDer.readPublicPoint(publicBytes),
    )

    constructor(bytes: Array<ByteArray>) : this(bytes[0], bytes[1])

    companion object {

        fun fromBytes(bytes: Array<ByteArray>): KeyPair? {
            try {
                return KeyPair(bytes[0], bytes[1])
            } catch (e: Exception) {
                Log.l("Failure to generate KeyPair from bytes array")
            }
            return null
        }

        /**
         * The private number out of a PKCS#8 key. This stands in for `KeyFactorySpi.EC`, and
         * answers null on bad input exactly as the Java did, with the same log line.
         */
        fun fromBytes(privateBytes: ByteArray): KeksUInt? {
            try {
                return KeksDer.readPrivateKeyScalar(privateBytes)
            } catch (e: Exception) {
                Log.l("Failure to generate Key from bytes array")
            }
            return null
        }
    }
}
