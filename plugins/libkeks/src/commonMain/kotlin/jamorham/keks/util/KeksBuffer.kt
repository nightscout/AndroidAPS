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

/**
 * The small part of `java.nio.ByteBuffer` the KEKS messages use.
 *
 * Three of its behaviours are copied on purpose, because the message classes depend on them:
 * a fresh buffer is zero filled, [array] hands back the whole buffer however far writing got, and
 * reading or writing past the end throws rather than answering a quiet zero. A short packet that
 * read as zeros would look like a real reading.
 */
class KeksBuffer private constructor(
    private val backing: ByteArray,
    private val littleEndian: Boolean,
) {

    private var position = 0

    fun put(value: Byte): KeksBuffer {
        checkRoom(1)
        backing[position++] = value
        return this
    }

    fun put(values: ByteArray): KeksBuffer = put(values, 0, values.size)

    fun put(values: ByteArray, offset: Int, length: Int): KeksBuffer {
        checkRoom(length)
        values.copyInto(backing, position, offset, offset + length)
        position += length
        return this
    }

    fun putInt(value: Int): KeksBuffer {
        checkRoom(4)
        if (littleEndian) {
            for (shift in 0..24 step 8) backing[position++] = (value ushr shift).toByte()
        } else {
            for (shift in 24 downTo 0 step 8) backing[position++] = (value ushr shift).toByte()
        }
        return this
    }

    fun get(): Byte {
        checkRoom(1)
        return backing[position++]
    }

    fun get(index: Int): Byte {
        if (index < 0 || index >= backing.size) {
            throw IndexOutOfBoundsException("KeksBuffer read at $index of ${backing.size}")
        }
        return backing[index]
    }

    fun get(destination: ByteArray): KeksBuffer {
        checkRoom(destination.size)
        backing.copyInto(destination, 0, position, position + destination.size)
        position += destination.size
        return this
    }

    fun getShort(): Short {
        checkRoom(2)
        val a = backing[position].toInt() and 0xFF
        val b = backing[position + 1].toInt() and 0xFF
        position += 2
        return (if (littleEndian) (b shl 8) or a else (a shl 8) or b).toShort()
    }

    fun array(): ByteArray = backing

    private fun checkRoom(count: Int) {
        if (position + count > backing.size) {
            throw IndexOutOfBoundsException(
                "KeksBuffer needs $count bytes at $position of ${backing.size}",
            )
        }
    }

    companion object {

        fun allocate(length: Int, littleEndian: Boolean = false): KeksBuffer =
            KeksBuffer(ByteArray(length), littleEndian)

        /** Backs onto the array it is given, the way `ByteBuffer.wrap` does - no copy. */
        fun wrap(bytes: ByteArray, littleEndian: Boolean = false): KeksBuffer =
            KeksBuffer(bytes, littleEndian)
    }
}
