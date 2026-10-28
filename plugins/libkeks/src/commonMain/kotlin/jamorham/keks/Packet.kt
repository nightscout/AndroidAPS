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

import jamorham.keks.Curve.FIELD_SIZE
import jamorham.keks.Curve.PACKET_SIZE
import jamorham.keks.JECPoint.Companion.pointFromBytes
import jamorham.keks.crypto.KeksEcPoint
import jamorham.keks.crypto.KeksUInt
import jamorham.keks.util.ByteArrayHashMap
import jamorham.keks.util.KeksBuffer

/**
 * JamOrHam
 *
 * Data packet serialization
 */
class Packet(
    val hash: KeksUInt,
    val publicKeyPoint1: KeksEcPoint,
    val publicKeyPoint2: KeksEcPoint,
) {

    constructor(bhm: ByteArrayHashMap) : this(
        KeksUInt.fromBytes(bhm.mget(HBYTES1_ID)),
        pointFromBytes(bhm.mget(POINT1X_ID), bhm.mget(POINT1Y_ID)),
        pointFromBytes(bhm.mget(POINT2X_ID), bhm.mget(POINT2Y_ID)),
    )

    fun output(): ByteArray {
        val packet = KeksBuffer.allocate(PACKET_SIZE)
        packet.put(JECPoint(publicKeyPoint1).toBytes())
        packet.put(JECPoint(publicKeyPoint2).toBytes())
        packet.put(hash.toBytes(FIELD_SIZE))
        val array = packet.array()
        if (array.size != PACKET_SIZE) {
            throw RuntimeException("Invalid size")
        }
        return array
    }

    companion object {

        const val POINT1X_ID = 28082
        const val POINT1Y_ID = 37603
        const val POINT2X_ID = 54247
        const val POINT2Y_ID = 40255
        const val HBYTES1_ID = 65535

        private val ID_LIST = intArrayOf(POINT1X_ID, POINT1Y_ID, POINT2X_ID, POINT2Y_ID, HBYTES1_ID)

        fun parse(packet: ByteArray): Packet? {
            if (packet.size < PACKET_SIZE) return null
            val bhm = ByteArrayHashMap()
            val buf = KeksBuffer.wrap(packet)
            for (id in ID_LIST) {
                buf.get(bhm.mget(id))
            }
            return Packet(bhm)
        }
    }
}
