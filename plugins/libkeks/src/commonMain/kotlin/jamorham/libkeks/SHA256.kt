/*
 * MIT License
 *
 * Copyright (c) 2017 Fabian Meyer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * Translated to Kotlin from SHA256.java in NightscoutFoundation/xDrip
 *   https://github.com/NightscoutFoundation/xDrip
 *   pin commit 1e86d9a2a52577ed2c30fbf7b69d75fd56e6918f (tag 2026.07.15)
 * See plugins/libkeks/NOTICE. The algorithm is unchanged. The two working arrays that were shared
 * statics guarded by `synchronized` are now local to each call: same result, no lock needed.
 *
 * SPDX-License-Identifier: MIT
 */
package jamorham.libkeks

object SHA256 {

    private val K = intArrayOf(
        0x428a2f98u.toInt(), 0x71374491u.toInt(), 0xb5c0fbcfu.toInt(), 0xe9b5dba5u.toInt(),
        0x3956c25bu.toInt(), 0x59f111f1u.toInt(), 0x923f82a4u.toInt(), 0xab1c5ed5u.toInt(),
        0xd807aa98u.toInt(), 0x12835b01u.toInt(), 0x243185beu.toInt(), 0x550c7dc3u.toInt(),
        0x72be5d74u.toInt(), 0x80deb1feu.toInt(), 0x9bdc06a7u.toInt(), 0xc19bf174u.toInt(),
        0xe49b69c1u.toInt(), 0xefbe4786u.toInt(), 0x0fc19dc6u.toInt(), 0x240ca1ccu.toInt(),
        0x2de92c6fu.toInt(), 0x4a7484aau.toInt(), 0x5cb0a9dcu.toInt(), 0x76f988dau.toInt(),
        0x983e5152u.toInt(), 0xa831c66du.toInt(), 0xb00327c8u.toInt(), 0xbf597fc7u.toInt(),
        0xc6e00bf3u.toInt(), 0xd5a79147u.toInt(), 0x06ca6351u.toInt(), 0x14292967u.toInt(),
        0x27b70a85u.toInt(), 0x2e1b2138u.toInt(), 0x4d2c6dfcu.toInt(), 0x53380d13u.toInt(),
        0x650a7354u.toInt(), 0x766a0abbu.toInt(), 0x81c2c92eu.toInt(), 0x92722c85u.toInt(),
        0xa2bfe8a1u.toInt(), 0xa81a664bu.toInt(), 0xc24b8b70u.toInt(), 0xc76c51a3u.toInt(),
        0xd192e819u.toInt(), 0xd6990624u.toInt(), 0xf40e3585u.toInt(), 0x106aa070u.toInt(),
        0x19a4c116u.toInt(), 0x1e376c08u.toInt(), 0x2748774cu.toInt(), 0x34b0bcb5u.toInt(),
        0x391c0cb3u.toInt(), 0x4ed8aa4au.toInt(), 0x5b9cca4fu.toInt(), 0x682e6ff3u.toInt(),
        0x748f82eeu.toInt(), 0x78a5636fu.toInt(), 0x84c87814u.toInt(), 0x8cc70208u.toInt(),
        0x90befffau.toInt(), 0xa4506cebu.toInt(), 0xbef9a3f7u.toInt(), 0xc67178f2u.toInt(),
        0x6a09e667u.toInt(), 0xbb67ae85u.toInt(), 0x3c6ef372u.toInt(), 0xa54ff53au.toInt(),
        0x510e527fu.toInt(), 0x9b05688cu.toInt(), 0x1f83d9abu.toInt(), 0x5be0cd19u.toInt(),
    )

    private const val BLOCK_BITS = 512
    private const val BLOCK_BYTES = BLOCK_BITS / 8

    fun hash(message: ByteArray): ByteArray {
        val h = IntArray(8)
        val w = IntArray(64)
        val temp = IntArray(8)
        // let H = H0, which this table keeps as its own last eight entries
        K.copyInto(h, 0, K.size - 8, K.size)
        val words = pad(message)
        // enumerate all blocks (each containing 16 words)
        var i = 0
        val n = words.size / 16
        while (i < n) {

            // initialize W from the block's words
            words.copyInto(w, 0, i * 16, i * 16 + 16)
            for (t in 16 until w.size) {
                w[t] = smallSig1(w[t - 2]) + w[t - 7] + smallSig0(w[t - 15]) + w[t - 16]
            }

            // let TEMP = H
            h.copyInto(temp, 0, 0, h.size)

            // operate on TEMP
            for (t in w.indices) {
                val t1 = temp[7] + bigSig1(temp[4]) + ch(temp[4], temp[5], temp[6]) + K[t] + w[t]
                val t2 = bigSig0(temp[0]) + maj(temp[0], temp[1], temp[2])
                temp.copyInto(temp, 1, 0, temp.size - 1)
                temp[4] += t1
                temp[0] = t1 + t2
            }

            // add values in TEMP to values in H
            for (t in h.indices) {
                h[t] += temp[t]
            }
            i++
        }
        return toByteArray(h)
    }

    /**
     * **Internal method, no need to call.** Pads the given message to have a length
     * that is a multiple of 512 bits (64 bytes), including the addition of a
     * 1-bit, k 0-bits, and the message length as a 64-bit integer.
     * The result is a 32-bit integer array with big-endian byte representation.
     *
     * @param message The message to pad.
     * @return A new array with the padded message bytes.
     */
    private fun pad(message: ByteArray): IntArray {
        // new message length: original + 1-bit and padding + 8-byte length
        // --> block count: whole blocks + (padding + length rounded up)
        val finalBlockLength = message.size % BLOCK_BYTES
        val blockCount = message.size / BLOCK_BYTES + if (finalBlockLength + 1 + 8 > BLOCK_BYTES) 2 else 1
        val result = IntArray(blockCount * (BLOCK_BYTES / 4))

        // copy as much of the message as possible
        val wholeWords = message.size / 4
        for (i in 0 until wholeWords) {
            result[i] = intAt(message, i * 4)
        }
        // copy the remaining bytes (less than 4) and append 1 bit (rest is zero)
        val remainder = ByteArray(4)
        val left = message.size - wholeWords * 4
        for (i in 0 until left) remainder[i] = message[wholeWords * 4 + i]
        remainder[left] = 0b10000000.toByte()
        result[wholeWords] = intAt(remainder, 0)

        // ignore however many pad bytes (implicitly calculated in the beginning)
        // place original message length as 64-bit integer at the end
        val msgLength = message.size * 8L
        result[result.size - 2] = (msgLength ushr 32).toInt()
        result[result.size - 1] = msgLength.toInt()
        return result
    }

    private fun intAt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    /**
     * Converts the given int array into a byte array via big-endian conversion
     * (1 int becomes 4 bytes).
     */
    private fun toByteArray(ints: IntArray): ByteArray {
        val out = ByteArray(ints.size * 4)
        for (i in ints.indices) {
            out[i * 4] = (ints[i] ushr 24).toByte()
            out[i * 4 + 1] = (ints[i] ushr 16).toByte()
            out[i * 4 + 2] = (ints[i] ushr 8).toByte()
            out[i * 4 + 3] = ints[i].toByte()
        }
        return out
    }

    private fun ch(x: Int, y: Int, z: Int): Int = (x and y) or (x.inv() and z)

    private fun maj(x: Int, y: Int, z: Int): Int = (x and y) or (x and z) or (y and z)

    private fun bigSig0(x: Int): Int = x.rotateRight(2) xor x.rotateRight(13) xor x.rotateRight(22)

    private fun bigSig1(x: Int): Int = x.rotateRight(6) xor x.rotateRight(11) xor x.rotateRight(25)

    private fun smallSig0(x: Int): Int = x.rotateRight(7) xor x.rotateRight(18) xor (x ushr 3)

    private fun smallSig1(x: Int): Int = x.rotateRight(17) xor x.rotateRight(19) xor (x ushr 10)
}
