package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException

/*
 * The `process2(5)` public point: the same draw of entropy that makes the phone's scalar also
 * makes the point that is sent on the wire.
 *
 * Ported from LibreCRKit `Crypto/FirstPairSourceSlice.swift` at pin `a86b92f`.
 *
 * This is why a first pairing may not use an ordinary random key pair: the sensor checks that the
 * point it is given belongs to this scheme.
 */

private fun requireScalarWords(words: UIntArray, label: String) {
    if (words.size != builder633fa8ScalarWordCount) {
        throw Libre3CryptoException("the process2(5) $label needs $builder633fa8ScalarWordCount words, not ${words.size}")
    }
}

private fun requireScalarQwords(words: ULongArray, label: String) {
    if (words.size != builder633fa8ScalarWordCount) {
        throw Libre3CryptoException("the process2(5) $label needs $builder633fa8ScalarWordCount long words, not ${words.size}")
    }
}

/**
 * The twenty words of the A source, from the entry source the accepted entropy makes.
 *
 * The loop below has the same shape as the two other packing loops of this scheme, with its own
 * tables and its own numbers.
 */
@Suppress("LongMethod")
internal fun builderProcess2P5PublicASourceWordsFromEntryArgSource(source11A: ByteArray): UIntArray {
    if (source11A.size != 0x11a) {
        throw Libre3CryptoException("the process2(5) entry source must be 0x11a bytes, not ${source11A.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val prelude = vm6420d8(process2P5PublicASourceInitialMagic, source11A, source11A, tables)
    requireSize(prelude, 0x10a, "process2(5) public prelude")

    var seedInputs = ByteArray(0)
    for (index in 0 until 19) {
        val start = index * 0x0e
        seedInputs += prelude.copyOfRange(start, start + 0x10)
    }
    seedInputs += process2P5PublicTableBlock(process2P5PublicASourceStaticTailBlock, 0x10, tables)

    val seedBlocks = ByteArray(builder633fa8ScalarWordCount * 0x10)
    for (index in 0 until builder633fa8ScalarWordCount) {
        val start = index * 0x10
        val block = seedInputs.copyOfRange(start, start + 0x10)
        vm638840(process2P5PublicASourceBlockMagic, block, block, tables).copyInto(seedBlocks, start)
    }

    val initCLane = process2P5PublicTableBlock(process2P5PublicASourceStaticCTable, 0x10, tables) + bytesOf(0x06, 0x06)
    val staticALane = process2P5PublicTableBlock(process2P5PublicASourceStaticATable, 0x12, tables)

    val out = UIntArray(builder633fa8ScalarWordCount)
    for (outerIndex in 0 until builder633fa8ScalarWordCount) {
        val lane = outerIndex and 7
        val eSource = process2P5PublicTableBlock(process2P5PublicASourceStaticETable + 0x12 * lane, 0x12, tables)
        val dSource = process2P5PublicTableBlock(process2P5PublicASourceStaticDTable + 0x12 * lane, 0x12, tables)
        val blockOffset = outerIndex * 0x10
        val block = seedBlocks.copyOfRange(blockOffset, blockOffset + 0x10)

        var bLane = vm638840(process2P5PublicASourceBInitMagic, eSource, eSource, tables)
        val dLaneInitial = vm6420d8(process2P5PublicASourceDInitMagic, block, block, tables)
        var eLane = vm638840(process2P5PublicASourcePrebridgeMagic, bLane, bLane, tables)
        var cLane = initCLane

        repeat(28) {
            val fLane = vm638840(process2P5PublicASourceFMagic, dLaneInitial, cLane, tables)
            val aLane = vm638840(process2P5PublicASourceAMagic, staticALane, fLane, tables)
            val tLane = vm638840(process2P5PublicASourceTMagic, bLane, aLane, tables)
            eLane = vm638840(process2P5PublicASourceBMixMagic, eLane, tLane, tables)
            bLane = vm638840(process2P5PublicASourceEAdvanceMagic, bLane, bLane, tables)
            cLane = vm638840(process2P5PublicASourceCAdvanceMagic, cLane, cLane, tables)
        }

        val fLane = vm638840(process2P5PublicASourcePostFMagic, eLane, bLane, tables)
        val dLane = vm638840(process2P5PublicASourcePostDMagic, fLane, dSource, tables)
        var packELane = vm641fcc(process2P5PublicASourcePostEMagic, dLane, tables)

        val packedLane = ByteArray(4)
        var shift = 32
        for (packIndex in 0 until 8) {
            val cWord = vm638840(process2P5PublicASourcePackCMagic, packELane, packELane, tables)
            if (shift >= 5) {
                packELane = vm6420d8(process2P5PublicASourcePackEMagic, packELane, packELane, tables)
            }
            val bWord = vm638840(process2P5PublicASourcePackBMagic, cWord, cWord, tables)

            val selected = bWord.u8(2) xor (bWord.u8(3) shl 3)
            val packed = process2P5PublicTableByte(process2P5PublicASourceNibbleTable + selected, tables)
            var nibble = if (packIndex and 1 == 0) packed and 0x0f else packed shr 4
            if (shift < 4) {
                val mask = if (shift == 0) 0 else (1 shl shift) - 1
                nibble = nibble and mask
            }

            val byteIndex = packIndex shr 1
            if (packIndex and 1 == 0) {
                packedLane[byteIndex] = nibble.toByte()
            } else {
                packedLane[byteIndex] = (packedLane[byteIndex].toInt() xor (nibble shl 4)).toByte()
            }
            shift = maxOf(shift - 4, 0)
        }

        out[outerIndex] = readUInt32LE(packedLane, 0)
    }
    return out
}

@Suppress("LongParameterList")
private fun builderProcess2P5PublicPrefixQword(
    word: UInt,
    foldTable: Int,
    qwordMul: ULong,
    qwordAdd: ULong,
    foldMul: ULong,
    finalMul: ULong,
    finalAdd: ULong,
    tables: Libre3FirstPairTables,
): ULong {
    val folded = foldProcess2P5Public(word.toULong() * qwordMul + qwordAdd, foldTable, 8, tables)
    return folded * foldMul + word.toULong() * finalMul + finalAdd
}

/** The running totals of the two source word runs. */
internal class Libre3Process2Prefixes(val aPrefix: ULongArray, val bPrefix: ULongArray)

internal fun builderProcess2P5PublicInitialPrefixesFromSourceWords(
    aSourceWords: UIntArray,
    bSourceWords: UIntArray,
): Libre3Process2Prefixes {
    requireScalarWords(aSourceWords, "A source")
    requireScalarWords(bSourceWords, "B source")
    val tables = Libre3FirstPairTables.get()

    val aValues = ULongArray(builder633fa8ScalarWordCount)
    var word = aSourceWords[0] * process2P5PublicPrefixAInitWordMul + process2P5PublicPrefixAInitWordAdd
    aValues[0] = builderProcess2P5PublicPrefixQword(
        word, process2P5PublicPrefixAFoldTable, process2P5PublicPrefixAQwordMul, process2P5PublicPrefixAQwordAdd,
        process2P5PublicPrefixAFoldMul, process2P5PublicPrefixAFinalMul, process2P5PublicPrefixAFinalAdd, tables,
    )
    for (index in 1 until builder633fa8ScalarWordCount) {
        val tableOffset = (index shl 2) and 0x1c
        word = aSourceWords[index] * u32TableWord63c278(process2P5PublicPrefixAWordMulTable + tableOffset, tables) +
            u32TableWord63c278(process2P5PublicPrefixAWordAddTable + tableOffset, tables)
        word = word * process2P5PublicPrefixAWordMul + process2P5PublicPrefixAWordAdd
        aValues[index] = builderProcess2P5PublicPrefixQword(
            word, process2P5PublicPrefixAFoldTable, process2P5PublicPrefixAQwordMul, process2P5PublicPrefixAQwordAdd,
            process2P5PublicPrefixAFoldMul, process2P5PublicPrefixAFinalMul, process2P5PublicPrefixAFinalAdd, tables,
        )
    }

    val bValues = ULongArray(builder633fa8ScalarWordCount)
    word = bSourceWords[0] * process2P5PublicPrefixBInitWordMul + process2P5PublicPrefixBInitWordAdd
    bValues[0] = builderProcess2P5PublicPrefixQword(
        word, process2P5PublicPrefixBFoldTable, process2P5PublicPrefixBQwordMul, process2P5PublicPrefixBQwordAdd,
        process2P5PublicPrefixBFoldMul, process2P5PublicPrefixBFinalMul, process2P5PublicPrefixBFinalAdd, tables,
    )
    for (index in 1 until builder633fa8ScalarWordCount) {
        val tableOffset = (index and 7) shl 2
        word = bSourceWords[index] * u32TableWord63c278(process2P5PublicPrefixBWordMulTable + tableOffset, tables) +
            u32TableWord63c278(process2P5PublicPrefixBWordAddTable + tableOffset, tables)
        word = word * process2P5PublicPrefixBWordMul + process2P5PublicPrefixBWordAdd
        bValues[index] = builderProcess2P5PublicPrefixQword(
            word, process2P5PublicPrefixBFoldTable, process2P5PublicPrefixBQwordMul, process2P5PublicPrefixBQwordAdd,
            process2P5PublicPrefixBFoldMul, process2P5PublicPrefixBFinalMul, process2P5PublicPrefixBFinalAdd, tables,
        )
    }

    return Libre3Process2Prefixes(cumulativeQwords(aValues), cumulativeQwords(bValues))
}

internal fun builderProcess2P5PublicInitialWorkspaceFromPrefixes(prefixes: Libre3Process2Prefixes): ULongArray {
    requireScalarQwords(prefixes.aPrefix, "A prefix")
    requireScalarQwords(prefixes.bPrefix, "B prefix")

    val aVec = diffCumulativeQwords(prefixes.aPrefix)
    val bVec = diffCumulativeQwords(prefixes.bPrefix)
    val out = ULongArray(42)
    for (index in 0 until 42) {
        val low = maxOf(0, index - 19)
        val high = minOf(index, 19)
        var productSum = 0uL
        var aSum = 0uL
        var bSum = 0uL
        var count = 0
        if (low <= high) {
            for (bIndex in low..high) {
                productSum += aVec[index - bIndex] * bVec[bIndex]
            }
            aSum = prefixes.aPrefix[index - low]
            if (index - high - 1 >= 0) aSum -= prefixes.aPrefix[index - high - 1]
            bSum = prefixes.bPrefix[high]
            if (low != 0) bSum -= prefixes.bPrefix[low - 1]
            count = high - low + 1
        }

        var value = count.toULong() * Process2P5PublicInitWorkspaceConstants.countMul +
            Process2P5PublicInitWorkspaceConstants.countAdd
        value += productSum * Process2P5PublicInitWorkspaceConstants.productMul
        value += bSum * Process2P5PublicInitWorkspaceConstants.bPrefixMul
        value += aSum * Process2P5PublicInitWorkspaceConstants.aPrefixMul
        out[index] = value * Process2P5PublicInitWorkspaceConstants.finalMul +
            Process2P5PublicInitWorkspaceConstants.finalAdd
    }
    return out
}

internal fun builderProcess2P5PublicInitialWorkspaceFromSourceWords(
    aSourceWords: UIntArray,
    bSourceWords: UIntArray,
): ULongArray = builderProcess2P5PublicInitialWorkspaceFromPrefixes(
    builderProcess2P5PublicInitialPrefixesFromSourceWords(aSourceWords, bSourceWords)
)

internal fun builderProcess2P5PublicTable35d8FromSourceWords(sourceWords: UIntArray): ULongArray {
    requireScalarWords(sourceWords, "table35d8 source")
    val tables = Libre3FirstPairTables.get()
    return ULongArray(builder633fa8ScalarWordCount) { index ->
        val tableOffset = (index shl 2) and 0x1c
        var word = sourceWords[index] * u32TableWord63c278(process2P5PublicTableU32MulTable + tableOffset, tables) +
            u32TableWord63c278(process2P5PublicTableU32AddTable + tableOffset, tables)
        word = word * process2P5PublicTableWordMul + process2P5PublicTableWordAdd

        var qword = word.toULong() * process2P5PublicTableQwordMul + process2P5PublicTableQwordAdd
        qword = foldProcess2P5Public(qword, process2P5PublicTableFoldTable, 8, tables)
        word.toULong() * process2P5PublicTableFinalMul + qword * process2P5PublicTableFoldMul +
            process2P5PublicTableFinalAdd
    }
}

internal fun builderProcess2P5PublicLowPrefixFromTable(qwords35d8: ULongArray, seed80: ULong): ByteArray {
    requireScalarQwords(qwords35d8, "low prefix table")
    val out = ByteArray(0xb0)
    writeUInt64LE(seed80 * process2P5PublicLowA8Mul + process2P5PublicLowA8Add, out, 0xa8)
    writeUInt64LE(seed80 * process2P5PublicLow80Mul + process2P5PublicLow80Add, out, 0x80)
    for (copy in process2P5PublicLowCopyOffsets) {
        writeUInt64LE(qwords35d8[copy.tableIndex], out, copy.offset)
    }
    return out
}

/**
 * The twenty two rounds that turn the two work areas into the long words of the scalar.
 *
 * The offsets and the order of the reads and writes are kept exactly as they are in the original.
 */
@Suppress("LongMethod")
internal fun builderProcess2P5PublicQwordWorkspacesFromPreframe(
    lowPrefix: ByteArray,
    highStack: ByteArray,
): List<ULongArray> {
    requireSize(lowPrefix, 0xb0, "process2(5) qword low prefix")
    requireSize(highStack, 0x360 + 42 * 8, "process2(5) qword high stack")
    val tables = Libre3FirstPairTables.get()

    val low28 = readUInt64LE(lowPrefix, 0x28)
    val low30 = readUInt64LE(lowPrefix, 0x30)
    val low38 = readUInt64LE(lowPrefix, 0x38)
    val low40 = readUInt64LE(lowPrefix, 0x40)
    val low48 = readUInt64LE(lowPrefix, 0x48)
    val low50 = readUInt64LE(lowPrefix, 0x50)
    val low58 = readUInt64LE(lowPrefix, 0x58)
    val low60 = readUInt64LE(lowPrefix, 0x60)
    val low68 = readUInt64LE(lowPrefix, 0x68)
    val low70 = readUInt64LE(lowPrefix, 0x70)
    val low78 = readUInt64LE(lowPrefix, 0x78)
    val low80 = readUInt64LE(lowPrefix, 0x80)
    val low88 = readUInt64LE(lowPrefix, 0x88)
    val low90 = readUInt64LE(lowPrefix, 0x90)
    val lowA8 = readUInt64LE(lowPrefix, 0xa8)

    val workspace = ULongArray(42) { readUInt64LE(highStack, 0x360 + it * 8) }
    var x22 = workspace[0]
    val x6 = readUInt64LE(highStack, 0x140)
    val x19 = readUInt64LE(highStack, 0x148)
    val x21 = readUInt64LE(highStack, 0x150)
    val x23 = readUInt64LE(highStack, 0x158)
    val x24 = readUInt64LE(highStack, 0x160)
    val x26 = readUInt64LE(highStack, 0x168)
    val x28 = readUInt64LE(highStack, 0x170)

    val workspaces = ArrayList<ULongArray>(23)
    workspaces.add(workspace.copyOf())
    for (index in 0 until 22) {
        val reg15 = low88
        val reg2 = low50
        val reg1 = low58
        var reg4 = low40
        var reg3 = low48

        var state = x22 * lowA8 + low80
        var folded = foldProcess2P5Public(
            state * 0x87d6a191657cf88buL + 0x55ab3c8b3f81c5eauL, process2P5PublicQwordFoldTableA, 7, tables,
        )
        state = state * 0x5513e20130c294ffuL + folded * 0x097f450230000000uL + 0x65416d6b1d6e1cbcuL
        val x27 = state * 0xde9a0217389253bbuL + 0x7368784697fb3dc5uL
        val x20 = state * 0x421be0fdc09a97cfuL + 0x492946def7da33b1uL

        reg3 = x27 * reg3 + x20
        val x22Head = x27 * low90 + x20 + x22
        reg4 = x27 * reg4 + x20

        folded = foldProcess2P5Public(
            x22Head * 0xe991db2a5d2a7faduL + 0xddaca38024dd36cduL, process2P5PublicQwordFoldTableB, 7, tables,
        )
        val x12 = folded * 0xcf053a359e1d9b81uL + 0xcfa9a29b5752d274uL
        folded = foldProcess2P5Public(
            x12 * 0x71795e15d000819buL + 0xf0c1332200ddc903uL, process2P5PublicQwordFoldTableC, 9, tables,
        )

        val old = ULongArray(20) { workspace[index + it] }
        val out = ULongArray(20)
        out[0] = x22Head
        out[1] = x27 * reg15 + x20 + old[1]
        out[2] = x27 * low78 + x20 + old[2]
        val acc13 = x27 * x6 + x20
        out[3] = x27 * low70 + x20 + old[3]
        out[4] = x27 * low68 + x20 + old[4]
        out[5] = x27 * low60 + x20 + old[5]
        out[6] = x27 * reg1 + x20 + old[6]
        val acc15 = x27 * x19 + x20
        out[7] = x27 * reg2 + x20 + old[7]
        out[8] = reg3 + old[8]
        val acc0 = x27 * x21 + x20
        out[9] = reg4 + old[9]
        val acc2 = x27 * low38 + x20
        out[10] = acc2 + old[10]
        val acc12 = x27 * low30 + x20
        out[11] = acc12 + old[11]
        val acc22 = x27 * low28 + x20
        out[12] = acc22 + old[12]
        val acc14 = x27 * x23 + x20
        val acc1 = x27 * x24 + x20
        out[13] = acc13 + old[13]
        out[14] = acc15 + old[14]
        val acc16 = x27 * x26 + x20
        val acc15Tail = x27 * x28 + x20
        out[15] = acc0 + old[15]
        out[16] = acc14 + old[16]

        var finalAcc = folded * 0x7a1cf7b000000000uL + x12 * 0x85a6c1a6777a6587uL
        finalAcc = finalAcc * 0xbf59bd30f12b2173uL + out[1]
        out[17] = acc1 + old[17]
        out[18] = acc16 + old[18]
        out[19] = acc15Tail + old[19]
        x22 = finalAcc + 0x0ba9328bc380f3f5uL
        out[1] = x22

        for (outIndex in out.indices) {
            workspace[index + outIndex] = out[outIndex]
        }
        workspaces.add(workspace.copyOf())
    }
    return workspaces
}

internal fun builderProcess2P5PublicQwordsFromPreframe(lowPrefix: ByteArray, highStack: ByteArray): ULongArray {
    val workspaces = builderProcess2P5PublicQwordWorkspacesFromPreframe(lowPrefix, highStack)
    val last = workspaces[workspaces.size - 1]
    return ULongArray(20) { last[22 + it] }
}

internal fun builderProcess2P5PublicScalarQwordsFromEntropy(entropy11A: ByteArray): ULongArray {
    val x1Source = builder633fa8NullPublicEntrySourceFromEntropy(entropy11A)
    val aSourceWords = builderProcess2P5PublicASourceWordsFromEntryArgSource(x1Source)
    val initialWorkspace = builderProcess2P5PublicInitialWorkspaceFromSourceWords(
        aSourceWords, process2P5PublicBSourceStaticWords,
    )
    val table35d8 = builderProcess2P5PublicTable35d8FromSourceWords(builder633fa8InvariantWords2dfc)
    val lowPrefix = builderProcess2P5PublicLowPrefixFromTable(table35d8, builder633fa8InvariantSeed3110)

    val highStack = ByteArray(0x360 + 42 * 8)
    val repeated = table35d8[7]
    for (offset in intArrayOf(0x140, 0x148, 0x150, 0x158, 0x160, 0x168, 0x170)) {
        writeUInt64LE(repeated, highStack, offset)
    }
    for (index in initialWorkspace.indices) {
        writeUInt64LE(initialWorkspace[index], highStack, 0x360 + index * 8)
    }

    return builderProcess2P5PublicQwordsFromPreframe(lowPrefix, highStack)
}

internal fun builderProcess2P5PublicScalarWordsFromQwords(qwords: ULongArray): UIntArray {
    requireScalarQwords(qwords, "scalar words")
    val tables = Libre3FirstPairTables.get()
    var state = 0x8e047df005b7774buL
    val out = UIntArray(builder633fa8ScalarWordCount)
    for (index in qwords.indices) {
        state = state * 0x4f9b1e335b5175b1uL + qwords[index] * 0xddc0126ec4f0da8buL + 0x807a205bcf09b957uL
        val foldedSeed = state * 0x0cc6d1cb7a71ea27uL + 0x75f17a53af690cbcuL
        val folded7 = foldProcess2P5Public(foldedSeed, process2P5PublicScalarQwordFoldTable, 7, tables)
        var word = state.toUInt() * 0xb904cc8bu + folded7.toUInt() * 0x30000000u + 0x7733dbc5u
        val folded16 = foldProcess2P5Public(folded7, process2P5PublicScalarQwordFoldTable, 9, tables)
        val tableOffset = (index * 4) and 0x1c
        word = word * u32TableWord63c278(process2P5PublicScalarQwordMulTable + tableOffset, tables) +
            u32TableWord63c278(process2P5PublicScalarQwordAddTable + tableOffset, tables)
        out[index] = word

        state = folded7 * 0xf5b69300c49039c7uL + folded16 * 0xb6fc639000000000uL + 0x5c589cf77e794af2uL
    }
    return out
}

internal fun builderProcess2P5PublicScalarWindowFromWords(words: UIntArray): ByteArray {
    requireScalarWords(words, "scalar pack")
    val tables = Libre3FirstPairTables.get()
    val out = ByteArray(builder633fa8ScalarWindowBytes)
    var acc = 0uL
    var bits = 0
    var outIndex = 0
    for (index in words.indices) {
        val tableOffset = (index * 4) and 0x1c
        var value = words[index] * u32TableWord63c278(process2P5PublicScalarPackMulTable + tableOffset, tables) +
            u32TableWord63c278(process2P5PublicScalarPackAddTable + tableOffset, tables)
        value = value * 0x0b6afc2fu + 0x4608a396u

        acc = acc xor (value.toULong() shl bits)
        bits += 28
        while (bits > 16 && outIndex < 69) {
            out[outIndex] = (acc and 0xffuL).toByte()
            acc = acc shr 8
            outIndex += 1
            bits -= 8
        }
    }
    if (bits >= 1 && outIndex < 69) {
        out[outIndex] = (acc and 0xffuL).toByte()
    }
    return out
}

internal fun builderProcess2P5PublicScalarWindowFromEntropy(entropy11A: ByteArray): ByteArray {
    val qwords = builderProcess2P5PublicScalarQwordsFromEntropy(entropy11A)
    return builderProcess2P5PublicScalarWindowFromWords(builderProcess2P5PublicScalarWordsFromQwords(qwords))
}

/**
 * The sixty five byte public point of this phone, `0x04` then X then Y.
 *
 * @throws Libre3RejectedEntropyException when this draw of entropy is refused by the scheme.
 */
internal fun builderProcess2P5PublicKey65FromEntropy(entropy11A: ByteArray): ByteArray {
    val scalarWindow = builderProcess2P5PublicScalarWindowFromEntropy(entropy11A)
    val fixedPoint = process2P5PublicFixedPointBE.copyOfRange(1, process2P5PublicFixedPointBE.size)
    val outputs = builder5bcf98P256Outputs(scalarWindow, fixedPoint)
    val xBE = outputs.xOutput70.copyOfRange(0, 32).reversedArray()
    val yBE = outputs.yOutput70.copyOfRange(0, 32).reversedArray()
    return bytesOf(0x04) + xBE + yBE
}
