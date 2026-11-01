package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException

/*
 * The `633fa8` tail: twenty schedule words in, the seventy byte scalar window out.
 *
 * Ported from LibreCRKit `Crypto/FirstPairSourceSlice.swift` at pin `a86b92f`.
 *
 * The middle step works on a large scratch area and reads and writes it at fixed offsets. Those
 * offsets are kept exactly as they are, because they are the shape of the original routine.
 */

private fun requireTailWords(words: UIntArray, label: String) {
    if (words.size != builder633fa8ScalarWordCount) {
        throw Libre3CryptoException("the 633fa8 $label words must be $builder633fa8ScalarWordCount, not ${words.size}")
    }
}

@Suppress("LongParameterList")
private fun convolutionWorkspace633fa8Tail(
    stack: ByteArray,
    aVectorOffset: Int,
    aPrefixOffset: Int,
    bVectorOffset: Int,
    bPrefixOffset: Int,
    outOffset: Int,
    length: Int,
    outputCount: Int,
    countMul: ULong,
    countAdd: ULong,
    productMul: ULong,
    bPrefixMul: ULong,
    aPrefixMul: ULong,
    finalMul: ULong,
    finalAdd: ULong,
) {
    val aVector = ULongArray(length) { readUInt64LE(stack, aVectorOffset + it * 8) }
    val aPrefix = ULongArray(length) { readUInt64LE(stack, aPrefixOffset + it * 8) }
    val bVector = ULongArray(length) { readUInt64LE(stack, bVectorOffset + it * 8) }
    val bPrefix = ULongArray(length) { readUInt64LE(stack, bPrefixOffset + it * 8) }

    for (index in 0 until outputCount) {
        val low = maxOf(0, index - (length - 1))
        val high = minOf(index, length - 1)
        var productSum = 0uL
        var aSum = 0uL
        var bSum = 0uL
        var count = 0
        if (low <= high) {
            for (bIndex in low..high) {
                productSum += aVector[index - bIndex] * bVector[bIndex]
            }
            aSum = aPrefix[index - low]
            if (index - high - 1 >= 0) aSum -= aPrefix[index - high - 1]
            bSum = bPrefix[high]
            if (low > 0) bSum -= bPrefix[low - 1]
            count = high - low + 1
        }

        var out = count.toULong() * countMul + countAdd
        out += productSum * productMul
        out += bSum * bPrefixMul
        out += aSum * aPrefixMul
        out = out * finalMul + finalAdd
        writeUInt64LE(out, stack, outOffset + index * 8)
    }
}

/** The twenty long words the scalar window is squeezed out of. */
@Suppress("LongMethod")
internal fun builder633fa8TailQwordsFromSources(
    words3ab0: UIntArray,
    words3120: UIntArray,
    words2dfc: UIntArray,
    seed3110: ULong,
): ULongArray {
    requireTailWords(words3ab0, "3ab0")
    requireTailWords(words3120, "3120")
    requireTailWords(words2dfc, "2dfc")

    val tables = Libre3FirstPairTables.get()
    val stack = ByteArray(builder633fa8TailStackBytes)

    var word = words3ab0[0] * 0xc365675bu + 0xe8f087b3u
    var value = builder633fa8TailStreamU64(
        word, 0x39629fb00ae1a583uL, 0xc87e38ff2ae3bb2duL, builder633fa8TailAFoldTable,
        0xd0779b0b00000000uL, 0x7168c55d8932925fuL, 0x9f10057a9662ab2duL, tables,
    )
    writeUInt64LE(value, stack, 0x3f40)
    writeUInt64LE(value, stack, 0x3cf0)
    var prefix = value
    for (index in 1 until builder633fa8ScalarWordCount) {
        word = u32Affine633fa8Tail(words3ab0[index], index, 0x11fd08, 0x11fd28, tables)
        word = word * 0x6ebad499u + 0x8b060038u
        value = builder633fa8TailStreamU64(
            word, 0x39629fb00ae1a583uL, 0xc87e38ff2ae3bb2duL, builder633fa8TailAFoldTable,
            0xd0779b0b00000000uL, 0x7168c55d8932925fuL, 0x9f10057a9662ab2duL, tables,
        )
        writeUInt64LE(value, stack, 0x3f40 + index * 8)
        prefix += value
        writeUInt64LE(prefix, stack, 0x3cf0 + index * 8)
    }

    word = words3120[0] * 0x21753b73u + 0x9f972fa4u
    value = builder633fa8TailStreamU64(
        word, 0xc16bd9358bd641f1uL, 0xdbd59c6303e46229uL, builder633fa8TailBFoldTable,
        0xe919ac4d00000000uL, 0x0eb018d832b73e83uL, 0x1f4e35decd254a8buL, tables,
    )
    writeUInt64LE(value, stack, 0x3e10)
    writeUInt64LE(value, stack, 0x3bd0)
    prefix = value
    for (index in 1 until builder633fa8ScalarWordCount) {
        word = u32Affine633fa8Tail(words3120[index], index, 0x112528, 0x112548, tables)
        word = word * 0x740d5673u + 0xf3b4a3bcu
        value = builder633fa8TailStreamU64(
            word, 0xc16bd9358bd641f1uL, 0xdbd59c6303e46229uL, builder633fa8TailBFoldTable,
            0xe919ac4d00000000uL, 0x0eb018d832b73e83uL, 0x1f4e35decd254a8buL, tables,
        )
        writeUInt64LE(value, stack, 0x3e10 + index * 8)
        prefix += value
        writeUInt64LE(prefix, stack, 0x3bd0 + index * 8)
    }

    convolutionWorkspace633fa8Tail(
        stack = stack,
        aVectorOffset = 0x3e10, aPrefixOffset = 0x3bd0,
        bVectorOffset = 0x3f40, bPrefixOffset = 0x3cf0,
        outOffset = 0x4080,
        length = builder633fa8ScalarWordCount, outputCount = 42,
        countMul = 0x88edcb9fcc5a504fuL, countAdd = 0xc50c4cfe6b90cc32uL,
        productMul = 0xb280f1fcde620b25uL, bPrefixMul = 0x24cc8b7736fa66cfuL,
        aPrefixMul = 0x512ce98be108b3a5uL,
        finalMul = 0xe8cb5b6d2f40c331uL, finalAdd = 0xeb47abb56d203e7duL,
    )

    for (index in words2dfc.indices) {
        word = u32Affine633fa8Tail(words2dfc[index], index, 0x11b268, 0x120e48, tables)
        word = word * 0x4890e04fu + 0xc2cec971u
        value = builder633fa8TailStreamU64(
            word, 0xe2d3ea4512d167e7uL, 0xa7c876b324afde01uL, builder633fa8TailCFoldTable,
            0xc4e79ba300000000uL, 0xf5ea48539d50faebuL, 0x37ffe0ce46814927uL, tables,
        )
        writeUInt64LE(value, stack, 0x3f40 + index * 8)
    }

    val q = ULongArray(builder633fa8ScalarWordCount) { readUInt64LE(stack, 0x3f40 + it * 8) }
    var x30 = readUInt64LE(stack, 0x4080)
    val seedA = seed3110 * 0xac33be2f37df9899uL + 0xf586b9c725fc2655uL
    val seedB = seed3110 * 0xc460e481253db509uL + 0x5642aeb8585a52ebuL

    for (byteOffset in 0 until 0xb0 step 8) {
        val position = 0x4080 + byteOffset
        var x10 = x30 * seedA + seedB
        var folded = fold633fa8Tail(
            x10 * 0x09883223fa4660eduL + 0x2d97cba42b4b302fuL, builder633fa8TailDFoldTable, 7, tables,
        )
        x10 = x10 * 0x9c92333d4c3638c9uL + folded * 0x718df58330000000uL + 0xdb9c322f9570a279uL

        val x7 = x10 * 0x06192264fc57feafuL + 0xe244b4f2265375bfuL
        val x21 = x10 * 0x3fd0fde8a99b1d3cuL + 0x7bd7d5be27b1d17cuL
        val x3 = x7 * q[8] + x21
        x30 = x7 * q[0] + x21 + x30
        val x4 = x7 * q[9] + x21

        folded = fold633fa8Tail(
            x30 * 0xa5ba351ba23facf5uL + 0x0720c6fcb580eff2uL, builder633fa8TailEFoldTable, 7, tables,
        )
        var x12 = folded * 0x9549f71510a8f0e7uL + 0x2d3bf7a5dd39f0abuL
        folded = fold633fa8Tail(
            x12 * 0xead4735c0bc5924duL + 0x73beb11d9159837cuL, builder633fa8TailFFoldTable, 9, tables,
        )
        var x8Mix = x12 * 0x7794ebcd6781608duL + folded * 0xafc58bf000000000uL

        val old08 = readUInt64LE(stack, position + 0x08)
        val old10 = readUInt64LE(stack, position + 0x10)
        var x13 = x7 * q[1] + x21 + old08
        val x11 = x7 * q[2] + x21 + old10
        writeUInt64LE(x30, stack, position)
        writeUInt64LE(x13, stack, position + 0x08)

        val acc13 = x7 * q[13] + x21
        val old18 = readUInt64LE(stack, position + 0x18)
        val old20 = readUInt64LE(stack, position + 0x20)
        var x14 = x7 * q[3] + x21 + old18
        val x15 = x7 * q[4] + x21 + old20

        val old28 = readUInt64LE(stack, position + 0x28)
        val old30 = readUInt64LE(stack, position + 0x30)
        val x16 = x7 * q[5] + x21 + old28
        var x17 = x7 * q[6] + x21 + old30
        writeUInt64LE(x14, stack, position + 0x18)
        writeUInt64LE(x15, stack, position + 0x20)

        val old38 = readUInt64LE(stack, position + 0x38)
        val old40 = readUInt64LE(stack, position + 0x40)
        var acc15 = x7 * q[14] + x21
        val x1 = x7 * q[7] + x21 + old38
        val x16b = x3 + old40
        writeUInt64LE(x16, stack, position + 0x28)
        writeUInt64LE(x17, stack, position + 0x30)

        val old48 = readUInt64LE(stack, position + 0x48)
        val old50 = readUInt64LE(stack, position + 0x50)
        val acc0 = x7 * q[15] + x21
        x14 = x4 + old48
        val acc2 = x7 * q[10] + x21
        x17 = acc2 + old50
        writeUInt64LE(x1, stack, position + 0x38)
        writeUInt64LE(x16b, stack, position + 0x40)
        writeUInt64LE(x14, stack, position + 0x48)
        writeUInt64LE(x17, stack, position + 0x50)

        val old58 = readUInt64LE(stack, position + 0x58)
        val old60 = readUInt64LE(stack, position + 0x60)
        val acc12 = x7 * q[11] + x21
        val acc30 = x7 * q[12] + x21
        val acc14 = x7 * q[16] + x21
        x12 = acc12 + old58
        x17 = acc30 + old60
        val acc1 = x7 * q[17] + x21
        writeUInt64LE(x12, stack, position + 0x58)
        writeUInt64LE(x17, stack, position + 0x60)

        val old68 = readUInt64LE(stack, position + 0x68)
        val old70 = readUInt64LE(stack, position + 0x70)
        x13 = acc13 + old68
        x12 = acc15 + old70
        val acc16 = x7 * q[18] + x21
        writeUInt64LE(x13, stack, position + 0x68)
        writeUInt64LE(x12, stack, position + 0x70)

        val old78 = readUInt64LE(stack, position + 0x78)
        val old80 = readUInt64LE(stack, position + 0x80)
        acc15 = x7 * q[19] + x21
        x12 = acc0 + old78
        x13 = acc14 + old80
        writeUInt64LE(x12, stack, position + 0x78)
        writeUInt64LE(x13, stack, position + 0x80)

        x8Mix = x8Mix * 0x56c495ec086d9247uL + readUInt64LE(stack, position + 0x08)

        val old88 = readUInt64LE(stack, position + 0x88)
        val old90 = readUInt64LE(stack, position + 0x90)
        x12 = acc1 + old88
        x14 = acc16 + old90
        writeUInt64LE(x12, stack, position + 0x88)
        writeUInt64LE(x14, stack, position + 0x90)

        val old98 = readUInt64LE(stack, position + 0x98)
        x12 = acc15 + old98
        writeUInt64LE(x12, stack, position + 0x98)

        x30 = x8Mix + 0xc387faf5615fb2e3uL
        writeUInt64LE(x30, stack, position + 0x08)
        writeUInt64LE(x11, stack, position + 0x10)
    }

    return ULongArray(builder633fa8ScalarWordCount) { readUInt64LE(stack, 0x4130 + it * 8) }
}

internal fun builder633fa8E10WordsFromTailQwords(tailQwords: ULongArray): UIntArray {
    if (tailQwords.size != builder633fa8ScalarWordCount) {
        throw Libre3CryptoException("the 633fa8 tail must be $builder633fa8ScalarWordCount long words, not ${tailQwords.size}")
    }
    val tables = Libre3FirstPairTables.get()
    var carry = builder633fa8E10InitialCarry
    val out = UIntArray(builder633fa8ScalarWordCount)
    for (index in tailQwords.indices) {
        val source = tailQwords[index] * builder633fa8E10QwordMul
        carry = carry * builder633fa8E10CarryMul + source
        carry += builder633fa8E10CarryAdd

        val foldedSeed = carry * builder633fa8E10FoldSeedMul + builder633fa8E10FoldSeedAdd
        var word = carry.toUInt() * builder633fa8E10WordMul
        val folded7 = fold633fa8Tail(foldedSeed, builder633fa8E10TailFoldTable, 7, tables)
        word += ((folded7 and 0x0fuL).toUInt() shl 28) + builder633fa8E10WordAdd
        val folded16 = fold633fa8Tail(folded7, builder633fa8E10TailFoldTable, 9, tables)

        val tableOffset = (index * 4) and 0x1c
        word = word * u32TableWord63c278(builder633fa8E10TailMulTable + tableOffset, tables) +
            u32TableWord63c278(builder633fa8E10TailAddTable + tableOffset, tables)
        out[index] = word

        carry = folded7 * builder633fa8E10NextCarryFolded7Mul +
            folded16 * builder633fa8E10NextCarryFolded16Mul +
            builder633fa8E10NextCarryAdd
    }
    return out
}

/** Packs the twenty words into the seventy byte scalar window, twenty eight bits at a time. */
internal fun builder633fa8ScalarWindowFromE10Words(words: UIntArray): ByteArray {
    if (words.size != builder633fa8ScalarWordCount) {
        throw Libre3CryptoException("the 633fa8 scalar needs $builder633fa8ScalarWordCount words, not ${words.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val out = ByteArray(builder633fa8ScalarWindowBytes)
    var accumulator = 0uL
    var bitCount = 0
    var outIndex = 0
    for (index in words.indices) {
        val tableOffset = (index * 4) and 0x1c
        var value = words[index] * u32TableWord63c278(builder633fa8ScalarPackMulTable + tableOffset, tables) +
            u32TableWord63c278(builder633fa8ScalarPackAddTable + tableOffset, tables)
        value = value * builder633fa8ScalarPackMul + builder633fa8ScalarPackAdd
        accumulator = accumulator xor (value.toULong() shl bitCount)
        bitCount += 28
        while (bitCount > 16 && outIndex < builder633fa8ScalarWindowBytes - 1) {
            out[outIndex] = (accumulator and 0xffuL).toByte()
            accumulator = accumulator shr 8
            outIndex += 1
            bitCount -= 8
        }
    }
    if (bitCount >= 1 && outIndex < builder633fa8ScalarWindowBytes - 1) {
        out[outIndex] = (accumulator and 0xffuL).toByte()
    }
    return out
}
