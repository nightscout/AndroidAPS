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

import jamorham.keks.crypto.KeksArrayIndexOutOfBoundsException
import jamorham.keks.crypto.KeksEcPoint
import jamorham.keks.crypto.KeksUInt
import jamorham.keks.util.AESwrapper
import jamorham.keks.util.KeksBuffer
import jamorham.keks.util.Util.arrayReduce
import jamorham.keks.util.Util.intToByteArray
import jamorham.libkeks.Digest
import jamorham.libkeks.SHA256

/**
 * JamOrHam
 *
 * For reference see J-PAKE as described in the following papers:
 *
 * - https://ia.cr/2010/190
 * - http://grouper.ieee.org/groups/1363/Research/contributions/hao-ryan-2008.pdf
 * - https://datatracker.ietf.org/doc/html/draft-cragie-tls-ecjpake-01
 */
object Calc {

    fun getRound12Packet(context: Context, part2: Boolean): Packet {
        val key = if (part2) context.KeyB!! else context.keyA!!
        val zkp = ZKP(Curve.G, key, context.alice!!)
        return Packet(zkp.getProof(), key.publicKey, zkp.getGv())
    }

    fun getRound1Packet(context: Context): Packet = getRound12Packet(context, false)

    fun getRound2Packet(context: Context): Packet = getRound12Packet(context, true)

    fun validateRound1Packet(p: Packet?, party: ByteArray): Boolean {
        if (p == null) return false
        return validateZeroKnowledgeProof(Curve.G, p.publicKeyPoint1, p.publicKeyPoint2, p.hash, party)
    }

    fun validateRound1Packet(context: Context): Boolean =
        validateRound1Packet(context.getRound1Packet(), context.bob!!)

    fun validateRound2Packet(context: Context): Boolean =
        validateRound1Packet(context.getRound2Packet(), context.bob!!)

    fun getRound3Packet(context: Context): Packet {
        val packet1 = context.getRound1Packet()!!
        val packet2 = context.getRound2Packet()!!
        val x1 = context.keyA!!.publicKey
        val x2 = context.KeyB!!.privateKey
        val x3 = packet1.publicKeyPoint1
        val x4 = packet2.publicKeyPoint1
        val s = context.getPasswordBigInteger()
        val x2s = x2.mulMod(s, Curve.Q)
        val x134 = x1.add(x3).add(x4).normalize()
        val a = x134.multiply(x2s).normalize()
        val zkp = ZKP(x134, KeyPair(x2s, a), context.alice!!)
        return Packet(zkp.getProof(), a, zkp.getGv())
    }

    fun validateRound3Packet(context: Context): Boolean {
        val packet = context.getRound3Packet() ?: return false
        val x1 = context.keyA!!.publicKey
        val x2 = context.KeyB!!.publicKey
        val x3 = context.getRound1Packet()!!.publicKeyPoint1
        val g = x1.add(x2).add(x3).normalize()
        val public1 = packet.publicKeyPoint1
        return validateZeroKnowledgeProof(g, public1, packet.publicKeyPoint2, packet.hash, context.bob!!)
    }

    fun getSharedKey(context: Context): ByteArray? {
        if (context.getRound3Packet() == null) return null
        val point1 = context.getRound3Packet()!!.publicKeyPoint1
        val x2 = context.KeyB!!.privateKey
        val x4 = context.getRound2Packet()!!.publicKeyPoint1
        val s = context.getPasswordBigInteger()
        val key = point1.subtract(x4.multiply(x2.mulMod(s, Curve.Q))).multiply(x2).normalize()
        return SHA256.hash(key.xEncoded())
    }

    fun getShortSharedKey(context: Context): ByteArray? = arrayReduce(getSharedKey(context), 16)

    fun calculateHash(context: Context): ByteArray? {
        val data = context.challenge!!
        val key = context.savedKey ?: getShortSharedKey(context)
        if (key == null) {
            return null
        }
        var bb = KeksBuffer.allocate(16)
        bb.put(data)
        bb.put(data)
        val doubleData = bb.array()
        val aesBytes = AESwrapper(key).aes(doubleData)!!
        bb = KeksBuffer.allocate(8)
        bb.put(aesBytes, 0, 8)
        return bb.array()
    }

    /**
     * A Schnorr proof that the sender knows the secret behind a public point, without sending it.
     */
    class ZKP(
        private val g: KeksEcPoint,
        private val keyPair: KeyPair,
        private val party: ByteArray,
    ) {

        private val exponent: KeksUInt = Curve.getExponent()
        private var gv: KeksEcPoint? = null

        fun getGv(): KeksEcPoint {
            var current = gv
            if (current == null) {
                current = g.multiply(exponent).normalize()
                gv = current
            }
            return current
        }

        fun getProof(): KeksUInt = exponent.subMod(
            getZeroKnowledgeHash(g, getGv(), keyPair.publicKey, party).mulMod(keyPair.privateKey, Curve.Q),
            Curve.Q,
        )
    }

    fun validateZeroKnowledgeProof(
        g: KeksEcPoint,
        publicKey: KeksEcPoint,
        gv: KeksEcPoint,
        b: KeksUInt,
        party: ByteArray,
    ): Boolean {
        val hash = getZeroKnowledgeHash(g, gv, publicKey, party)
        return g.multiply(b)
            .add(publicKey.multiply(hash))
            .normalize() == gv
    }

    fun getZeroKnowledgeHash(
        g: KeksEcPoint,
        gv: KeksEcPoint,
        gx: KeksEcPoint,
        party: ByteArray,
    ): KeksUInt {
        val digestBytes = ByteArray(32)
        val digest = Digest(digestBytes)
        updateDigestIncludingSize(digest, g)
        updateDigestIncludingSize(digest, gv)
        updateDigestIncludingSize(digest, gx)
        updateDigestIncludingSize(digest, party)
        digest.doFinal()
        return KeksUInt.fromBytes(digestBytes).mod(Curve.Q)
    }

    private fun updateDigestIncludingSize(digest: Digest, point: KeksEcPoint) {
        updateDigestIncludingSize(digest, point.getEncoded(false))
    }

    private fun updateDigestIncludingSize(digest: Digest, byteArray: ByteArray) {
        digest.update(intToByteArray(byteArray.size))
        digest.update(byteArray)
    }

    fun challenger(bytes: ByteArray, challenge: ByteArray): ByteArray? {
        val pchallenge = ByteArray(16)
        // The Java copied blind and let System.arraycopy raise on a short packet. The ONE+ driver
        // catches that by name to turn an out of step handshake into a failed authentication, so
        // the same kind of failure is raised here rather than a quietly padded challenge.
        if (challenge.size < 2 + pchallenge.size) {
            throw KeksArrayIndexOutOfBoundsException(
                "challenge is ${challenge.size} bytes, needs ${2 + pchallenge.size}",
            )
        }
        challenge.copyInto(pchallenge, 0, 2, 2 + pchallenge.size)
        return DSAChallenger(KeyPair.fromBytes(bytes))
            .response(pchallenge)
    }
}
