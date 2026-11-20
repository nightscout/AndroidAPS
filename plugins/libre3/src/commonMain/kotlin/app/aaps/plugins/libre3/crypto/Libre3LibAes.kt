package app.aaps.plugins.libre3.crypto

/**
 * The block maker that protects the pairing messages.
 *
 * Ported from LibreCRKit `Crypto/LibAES.swift` at pin `a86b92f`, which is itself a clean room port
 * of the sensor maker's own routine. It looks like AES but it is not: every step is driven by
 * fixed tables that ship with the app, and ordinary AES gives a different answer.
 *
 * Only the wire block is ported here, because only that one is used by the pairing. The other
 * block in the upstream file serves a path that is out of scope for this version.
 *
 * Kotlin `UInt` is used throughout so the arithmetic wraps exactly like the 32 bit values of the
 * original. Everything is checked against the published vectors, including a live capture.
 */
object Libre3LibAes {

    /** Size of the working area that a key is expanded into. */
    const val CONTEXT_SIZE = 0x10b0

    private const val BLOCK_SIZE = 16

    /** Builds a block maker for one key, ready to be handed to AES-CCM. */
    fun blockMaker(rawKey: ByteArray): Libre3AesBlock {
        val context = keySetup(rawKey)
        return Libre3AesBlock { block -> encryptBlock(block, context) }
    }

    /**
     * Expands a 16 byte key into the working area the block maker reads.
     *
     * @throws Libre3CryptoException when the key is the wrong size or a table is missing.
     */
    fun keySetup(rawKey: ByteArray): ByteArray {
        if (rawKey.size != 16) throw Libre3CryptoException("this key must be 16 bytes, not ${rawKey.size}")
        val tables = Libre3LibAesTables.load()
        val context = ByteArray(CONTEXT_SIZE)
        rawKey.copyInto(context, 0)

        var outOffset = 0x10
        var constA = 0x08
        var constB = 0xa8
        var loopCounter = -4
        var w13 = readU32(context, 0x0c)

        while (true) {
            val w14 = readU32(tables.keyexpConsts, constA - 8)
            loopCounter += 4
            val keepGoing = loopCounter < 0x24

            w13 = w14 xor rotateRight(w13, 24)
            var sub = subword(tables.keyexpTables, w13, 0)
            val prev0 = readU32(context, outOffset - 0x10)
            val prev1 = readU32(context, outOffset - 0x0c)
            var w15 = prev0 xor readU32(tables.keyexpConsts, constB - 8)
            w13 = w15 xor sub
            writeU32(context, outOffset, w13)

            w13 = readU32(tables.keyexpConsts, constA - 4) xor w13
            sub = subword(tables.keyexpTables, w13, 1)
            var w14Mix = prev1 xor readU32(tables.keyexpConsts, constB - 4)
            w13 = w14Mix xor sub
            writeU32(context, outOffset + 0x04, w13)

            w13 = readU32(tables.keyexpConsts, constA) xor w13
            sub = subword(tables.keyexpTables, w13, 2)
            val prev2 = readU32(context, outOffset - 0x08)
            val prev3 = readU32(context, outOffset - 0x04)
            w15 = prev2 xor readU32(tables.keyexpConsts, constB)
            w13 = w15 xor sub
            writeU32(context, outOffset + 0x08, w13)

            w13 = readU32(tables.keyexpConsts, constA + 0x04) xor w13
            constA += 0x10
            sub = subword(tables.keyexpTables, w13, 3)
            w14Mix = prev3 xor readU32(tables.keyexpConsts, constB + 0x04)
            constB += 0x10
            w13 = w14Mix xor sub
            writeU32(context, outOffset + 0x0c, w13)

            outOffset += 0x10
            if (!keepGoing) break
        }

        for (group in 0 until 4) {
            val offset = 0xa0 + group * 4
            writeU32(context, offset, subword(tables.finalKeyTables, readU32(context, offset), group))
        }

        for (tableIndex in 0 until 16) {
            val wordTableBase = readU32(tables.finalTableIndex, tableIndex * 4).toInt() * 0x400
            val mapBase = tableIndex * 0x100
            val keyWord = readU32(context, 0xa0 + (tableIndex shr 2) * 4)
            val shift = 24 - 8 * (tableIndex and 3)
            val destination = 0xb0 + tableIndex * 0x100

            for (i in 0 until 256) {
                val mixed = keyWord xor readU32(tables.finalTableWords, wordTableBase + i * 4)
                context[destination + i] = tables.finalTableMap[mapBase + ((mixed shr shift) and 0xFFu).toInt()]
            }
        }
        return context
    }

    /**
     * Turns 16 plain bytes into 16 protected bytes.
     *
     * @param context the working area from [keySetup].
     */
    fun encryptBlock(plaintext: ByteArray, context: ByteArray): ByteArray {
        if (plaintext.size != BLOCK_SIZE) {
            throw Libre3CryptoException("a block is 16 bytes, not ${plaintext.size}")
        }
        if (context.size < CONTEXT_SIZE) throw Libre3CryptoException("the working area is too small")

        val tables = Libre3LibAesTables.load()
        val first = tables.phase5Round1Tables

        // The four starting words: the key material mixed with the plain bytes, one table per byte.
        var w16 = readU32(context, 0x00) xor startWord(first, plaintext, 0)
        var w14 = readU32(context, 0x04) xor startWord(first, plaintext, 4)
        var w13 = readU32(context, 0x08) xor startWord(first, plaintext, 8)
        var w15 = readU32(context, 0x0c) xor startWord(first, plaintext, 12)

        var round = 0
        var w11 = 0u
        while (true) {
            val firstHalf = firstHalf(w16, w14, w13, w15, context, round, tables)
            w11 = firstHalf[0]; w14 = firstHalf[1]; w13 = firstHalf[2]; w15 = firstHalf[3]
            if (round == 0x80) break
            val secondHalf = secondHalf(w11, w14, w13, w15, context, round, tables)
            w16 = secondHalf[0]; w14 = secondHalf[1]; w13 = secondHalf[2]; w15 = secondHalf[3]
            round += 0x20
        }

        // The last step reads one byte per lane out of the working area itself.
        val out = ByteArray(BLOCK_SIZE)
        out[0] = pick(context, 0x0b0, w11 shr 24)
        out[1] = pick(context, 0x1b0, w14 shr 16)
        out[2] = pick(context, 0x2b0, w13 shr 8)
        out[3] = pick(context, 0x3b0, w15)
        out[4] = pick(context, 0x4b0, w14 shr 24)
        out[5] = pick(context, 0x5b0, w13 shr 16)
        out[6] = pick(context, 0x6b0, w15 shr 8)
        out[7] = pick(context, 0x7b0, w11)
        out[8] = pick(context, 0x8b0, w13 shr 24)
        out[9] = pick(context, 0x9b0, w15 shr 16)
        out[10] = pick(context, 0xab0, w11 shr 8)
        out[11] = pick(context, 0xbb0, w14)
        out[12] = pick(context, 0xcb0, w15 shr 24)
        out[13] = pick(context, 0xdb0, w11 shr 16)
        out[14] = pick(context, 0xeb0, w14 shr 8)
        out[15] = pick(context, 0xfb0, w13)
        return out
    }

    /** Four bytes of the plain text, each through its own table, joined into one word. */
    private fun startWord(first: ByteArray, plaintext: ByteArray, lane: Int): UInt {
        val base = lane * 0x100
        return (first[base + 0x000 + (plaintext[lane].toInt() and 0xFF)].toUInt() and 0xFFu shl 24) or
            (first[base + 0x100 + (plaintext[lane + 1].toInt() and 0xFF)].toUInt() and 0xFFu shl 16) or
            (first[base + 0x200 + (plaintext[lane + 2].toInt() and 0xFF)].toUInt() and 0xFFu shl 8) or
            (first[base + 0x300 + (plaintext[lane + 3].toInt() and 0xFF)].toUInt() and 0xFFu)
    }

    private fun firstHalf(
        w16: UInt,
        w14: UInt,
        w13: UInt,
        w15: UInt,
        context: ByteArray,
        round: Int,
        tables: Libre3LibAesTables,
    ): UIntArray {
        val t = tables.phase5RoundTables
        val rk0 = readU32(context, 0x10 + round)
        val rk1 = readU32(context, 0x14 + round)
        val rk2 = readU32(context, 0x18 + round)
        val rk3 = readU32(context, 0x1c + round)

        val out11 = word(t, 0, w16 shr 24) xor rk0 xor word(t, 15, w15) xor
            word(t, 5, bits(w14, 16)) xor word(t, 10, bits(w13, 8))

        val out14 = (rk1 xor word(t, 3, w16) xor word(t, 9, bits(w13, 16))) xor
            (word(t, 4, w14 shr 24) xor word(t, 14, bits(w15, 8)))

        val out13 = word(t, 13, bits(w15, 16)) xor word(t, 7, w14) xor
            word(t, 2, bits(w16, 8)) xor word(t, 8, w13 shr 24) xor rk2

        val out15 = (word(t, 6, bits(w14, 8)) xor rk3) xor
            (word(t, 12, w15 shr 24) xor word(t, 11, w13)) xor word(t, 1, bits(w16, 16))

        return uintArrayOf(out11, out14, out13, out15)
    }

    private fun secondHalf(
        w11: UInt,
        w14: UInt,
        w13: UInt,
        w15: UInt,
        context: ByteArray,
        round: Int,
        tables: Libre3LibAesTables,
    ): UIntArray {
        val t = tables.phase5RoundTables
        val rk0 = readU32(context, 0x20 + round)
        val rk1 = readU32(context, 0x24 + round)
        val rk2 = readU32(context, 0x28 + round)
        val rk3 = readU32(context, 0x2c + round)

        val out16 = word(t, 15, w15) xor word(t, 0, w11 shr 24) xor
            word(t, 10, bits(w13, 8)) xor word(t, 5, bits(w14, 16)) xor rk0

        val out14 = (word(t, 4, w14 shr 24) xor word(t, 3, w11) xor word(t, 14, bits(w15, 8))) xor
            rk1 xor word(t, 9, bits(w13, 16))

        val out13 = (word(t, 13, bits(w15, 16)) xor rk2 xor word(t, 2, bits(w11, 8))) xor
            word(t, 8, w13 shr 24) xor word(t, 7, w14)

        val out15 = (word(t, 11, w13) xor word(t, 1, bits(w11, 16)) xor rk3) xor
            (word(t, 12, w15 shr 24) xor word(t, 6, bits(w14, 8)))

        return uintArrayOf(out16, out14, out13, out15)
    }

    /** Eight bits of a word, starting at [lowestBit]. */
    private fun bits(value: UInt, lowestBit: Int): UInt = (value shr lowestBit) and 0xFFu

    private fun word(table: UIntArray, index: Int, position: UInt): UInt =
        table[index * 256 + (position and 0xFFu).toInt()]

    private fun pick(context: ByteArray, offset: Int, position: UInt): Byte =
        context[offset + (position and 0xFFu).toInt()]

    private fun rotateRight(value: UInt, shift: Int): UInt = (value shr shift) or (value shl (32 - shift))

    /** One word rebuilt from four table lookups, lane by lane. */
    private fun subword(table: ByteArray, value: UInt, group: Int): UInt {
        val base = group * 0x400
        val b0 = (value and 0xFFu).toInt()
        val b1 = ((value shr 8) and 0xFFu).toInt()
        val b2 = ((value shr 16) and 0xFFu).toInt()
        val b3 = ((value shr 24) and 0xFFu).toInt()
        return (table[base + 0x300 + b0].toUInt() and 0xFFu) or
            ((table[base + 0x100 + b2].toUInt() and 0xFFu) shl 16) or
            ((table[base + 0x200 + b1].toUInt() and 0xFFu) shl 8) or
            ((table[base + b3].toUInt() and 0xFFu) shl 24)
    }

    private fun readU32(bytes: ByteArray, offset: Int): UInt =
        (bytes[offset].toUInt() and 0xFFu) or
            ((bytes[offset + 1].toUInt() and 0xFFu) shl 8) or
            ((bytes[offset + 2].toUInt() and 0xFFu) shl 16) or
            ((bytes[offset + 3].toUInt() and 0xFFu) shl 24)

    private fun writeU32(bytes: ByteArray, offset: Int, value: UInt) {
        bytes[offset] = (value and 0xFFu).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFFu).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xFFu).toByte()
        bytes[offset + 3] = ((value shr 24) and 0xFFu).toByte()
    }
}
