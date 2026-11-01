package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException

/*
 * The null branch of `633fa8`: fresh entropy in, the seventy byte scalar window of the phone's own
 * key pair out.
 *
 * Ported from LibreCRKit `Crypto/FirstPairSourceSlice.swift` at pin `a86b92f`.
 *
 * Not every draw of entropy is accepted. The scheme runs its own two checks and refuses a draw
 * that does not pass them, which is why the caller draws again in a loop.
 */

/** Raised when a draw of entropy is refused by the scheme's own checks. */
internal class Libre3RejectedEntropyException : Exception("this draw of entropy was refused by the first pairing scheme")

/** The fixed sources the null branch starts from. */
internal class Builder633fa8NullEntrySources(
    val prologueSource: ByteArray,
    val check1SourceWords: UIntArray,
    val check2SourceWords: UIntArray,
)

/** What the first step of the null branch produced. */
internal class Builder633fa8NullInitialResult(
    val maskedEntropy: ByteArray,
    val cf0: ByteArray,
    val e10: ByteArray,
    val seedInputs: ByteArray,
    val seedBlocks: ByteArray,
)

/** What the first loop of the null branch produced. */
internal class Builder633fa8NullFirstLoopResult(val finalTLane: ByteArray, val scheduleWords: UIntArray)

/** The two checks the scheme runs on a draw of entropy. */
internal class Builder633fa8NullScheduleAcceptance(val firstOK: Boolean, val secondOK: Boolean)

/** The two block runs the accepted draw produced. */
internal class Builder633fa8NullPostAcceptResult(val blocks4080: ByteArray, val blocks3f40: ByteArray)

/** An accepted scalar window, with the entropy that made it and how many draws it took. */
internal class Builder633fa8NullScalarResult(
    val scalarWindow: ByteArray,
    val entropy11A: ByteArray,
    val attempts: Int,
)

private fun builder633fa8NullTableBlock(libOffset: Int, byteCount: Int, tables: Libre3FirstPairTables): ByteArray =
    checkedSlice(
        tables.nullTables633fa8, libOffset - builder633fa8NullTableBase, byteCount,
        Libre3FirstPairTables.NULL_TABLES_633FA8,
    )

private fun builder633fa8NullNibbleByte(libOffset: Int, tables: Libre3FirstPairTables): Int =
    checkedSlice(
        tables.nullNibble633fa8, libOffset - builder633fa8NullNibbleTableBase, 1,
        Libre3FirstPairTables.NULL_NIBBLE_633FA8,
    ).u8(0)

private fun u32TableWord633fa8Null(absoluteOffset: Int, tables: Libre3FirstPairTables): UInt {
    val relative = absoluteOffset - builder633fa8NullTableBase
    if (relative < 0 || relative + 4 > tables.nullTables633fa8.size) {
        throw Libre3CryptoException(
            "the first pairing table ${Libre3FirstPairTables.NULL_TABLES_633FA8} has nothing at offset $absoluteOffset"
        )
    }
    return readUInt32LE(tables.nullTables633fa8, relative)
}

private fun fold633fa8NullCheck32(value: UInt, tableOffset: Int, rounds: Int, tables: Libre3FirstPairTables): UInt {
    var folded = value
    repeat(rounds) {
        folded = u32TableWord633fa8Null(tableOffset + (folded and 0x0fu).toInt() * 4, tables) + (folded shr 4)
    }
    return folded
}

private fun expand3BitPairTableRow633fa8Null(raw9: ByteArray): ByteArray {
    if (raw9.size != 9) {
        throw Libre3CryptoException("a 633fa8 null table row must be 9 bytes, not ${raw9.size}")
    }
    val out = ByteArray(builder633fa8NullLoopLaneBytes)
    var write = 0
    for (value in raw9) {
        out[write++] = (value.toInt() and 7).toByte()
        out[write++] = ((value.toInt() and 0xFF) shr 3 and 7).toByte()
    }
    return out
}

private fun stitch633fa8NullPrelude11A(firstBlock: ByteArray, restBlocks: ByteArray): ByteArray {
    if (firstBlock.size != builder633fa8NullSeedBlockBytes) {
        throw Libre3CryptoException(
            "the 633fa8 null first stitch block must be $builder633fa8NullSeedBlockBytes bytes, not ${firstBlock.size}"
        )
    }
    val restByteCount = (builder633fa8ScalarWordCount - 1) * builder633fa8NullSeedBlockBytes
    if (restBlocks.size != restByteCount) {
        throw Libre3CryptoException(
            "the 633fa8 null rest stitch blocks must be $restByteCount bytes, not ${restBlocks.size}"
        )
    }

    val out = ByteArray(builder633fa8NullEntropyBytes)
    firstBlock.copyInto(out, 0)
    for (index in 0 until builder633fa8ScalarWordCount - 1) {
        val srcStart = index * builder633fa8NullSeedBlockBytes + 2
        val dstStart = builder633fa8NullSeedBlockBytes + index * builder633fa8NullSeedBlockStride
        restBlocks.copyInto(out, dstStart, srcStart, srcStart + builder633fa8NullSeedBlockStride)
    }
    return out
}

@Suppress("LongParameterList")
private fun builder633fa8NullScheduleCheck(
    scheduleWords: UIntArray,
    sourceWords: UIntArray,
    scheduleMulTable: Int,
    sourceMulTable: Int,
    addTable: Int,
    foldTable: Int,
    target: UInt,
    foldTarget: UInt,
    tables: Libre3FirstPairTables,
): Boolean {
    if (scheduleWords.size != builder633fa8ScalarWordCount || sourceWords.size != builder633fa8ScalarWordCount) {
        throw Libre3CryptoException("a 633fa8 null check needs $builder633fa8ScalarWordCount words")
    }

    for (index in builder633fa8ScalarWordCount - 1 downTo 0) {
        val tableOffset = (index * 4) and 0x1c
        val word = scheduleWords[index] * u32TableWord63c278(scheduleMulTable + tableOffset, tables) +
            sourceWords[index] * u32TableWord63c278(sourceMulTable + tableOffset, tables) +
            u32TableWord63c278(addTable + tableOffset, tables)
        if (word == target) continue
        val folded = fold633fa8NullCheck32(word, foldTable, 7, tables)
        return (folded and 0x0fu) == foldTarget
    }
    return true
}

internal fun builder633fa8NullEntrySourcesFromInvariantEntry(): Builder633fa8NullEntrySources {
    val unpacked = unpack3BitStream5bdd14(builder633fa8NullEntryBitsChecksSource, 0, builder633fa8NullEntropyBytes)
    val cursor = unpacked.nextOffset
    val checkBytes = builder633fa8ScalarWordCount * 4
    if (cursor + 2 * checkBytes > builder633fa8NullEntryBitsChecksSource.size) {
        throw Libre3CryptoException("the 633fa8 null entry source is too short for its two checks")
    }
    val check1 = UIntArray(builder633fa8ScalarWordCount) {
        readUInt32LE(builder633fa8NullEntryBitsChecksSource, cursor + it * 4)
    }
    val check2 = UIntArray(builder633fa8ScalarWordCount) {
        readUInt32LE(builder633fa8NullEntryBitsChecksSource, cursor + checkBytes + it * 4)
    }
    return Builder633fa8NullEntrySources(unpacked.values, check1, check2)
}

internal fun builder633fa8NullInitialFromEntropy(entropy11A: ByteArray, prologueSource: ByteArray): Builder633fa8NullInitialResult {
    if (entropy11A.size != builder633fa8NullEntropyBytes) {
        throw Libre3CryptoException(
            "the first pairing entropy must be $builder633fa8NullEntropyBytes bytes, not ${entropy11A.size}"
        )
    }
    if (prologueSource.size < builder633fa8NullEntropyBytes) {
        throw Libre3CryptoException(
            "the 633fa8 null prologue must be at least $builder633fa8NullEntropyBytes bytes, not ${prologueSource.size}"
        )
    }
    val tables = Libre3FirstPairTables.get()
    val maskedEntropy = ByteArray(entropy11A.size) { (entropy11A[it].toInt() and 7).toByte() }
    val prologue = prologueSource.copyOfRange(0, builder633fa8NullEntropyBytes)
    val cf0 = vm638840(builder633fa8NullInitialAMagic, maskedEntropy, prologue, tables)
    val e10 = vm638840(builder633fa8NullInitialBMagic, cf0, cf0, tables)

    val lastSeedInputEnd = (builder633fa8ScalarWordCount - 1) * builder633fa8NullSeedBlockStride +
        builder633fa8NullSeedBlockBytes
    if (e10.size < lastSeedInputEnd) {
        throw Libre3CryptoException("the 633fa8 null first step came back as ${e10.size} bytes")
    }

    val seedInputs = ByteArray(builder633fa8ScalarWordCount * builder633fa8NullSeedBlockBytes)
    for (index in 0 until builder633fa8ScalarWordCount) {
        val start = index * builder633fa8NullSeedBlockStride
        e10.copyInto(seedInputs, index * builder633fa8NullSeedBlockBytes, start, start + builder633fa8NullSeedBlockBytes)
    }

    val seedBlocks = ByteArray(builder633fa8ScalarWordCount * builder633fa8NullSeedBlockBytes)
    for (index in 0 until builder633fa8ScalarWordCount) {
        val start = index * builder633fa8NullSeedBlockBytes
        val block = seedInputs.copyOfRange(start, start + builder633fa8NullSeedBlockBytes)
        vm638840(builder633fa8NullSeedBlockMagic, block, block, tables).copyInto(seedBlocks, start)
    }

    return Builder633fa8NullInitialResult(maskedEntropy, cf0, e10, seedInputs, seedBlocks)
}

/**
 * The first loop of the null branch.
 *
 * It has the same shape as the low seed loop, with its own tables and its own numbers. The two are
 * kept apart on purpose: joining them would hide a wrong table behind a right looking answer.
 */
@Suppress("LongMethod")
internal fun builder633fa8NullFirstLoopFromBlocks(seedBlocks: ByteArray): Builder633fa8NullFirstLoopResult {
    if (seedBlocks.size != builder633fa8NullSeedBlocksBytes) {
        throw Libre3CryptoException(
            "the 633fa8 null seed blocks must be $builder633fa8NullSeedBlocksBytes bytes, not ${seedBlocks.size}"
        )
    }
    val tables = Libre3FirstPairTables.get()
    val scheduleWords = UIntArray(builder633fa8ScalarWordCount)
    var finalTLane = ByteArray(0)

    for (outerIndex in 0 until builder633fa8ScalarWordCount) {
        val lane = outerIndex and 7
        var cLane = builder633fa8NullTableBlock(builder633fa8NullLoopStaticCTable, 0x10, tables) + bytesOf(0x05, 0x05)

        val eSource = builder633fa8NullTableBlock(
            builder633fa8NullLoopStaticETable + builder633fa8NullLoopLaneBytes * lane,
            builder633fa8NullLoopLaneBytes, tables,
        )
        var eLane = vm638840(builder633fa8NullLoopEInitMagic, eSource, eSource, tables)
        val blockOffset = outerIndex * builder633fa8NullSeedBlockBytes
        val block = seedBlocks.copyOfRange(blockOffset, blockOffset + builder633fa8NullSeedBlockBytes)
        val dLane = vm6420d8(builder633fa8NullLoopDInitMagic, block, block, tables)
        var bLane = vm638840(builder633fa8NullLoopBInitMagic, eLane, eLane, tables)

        var aLane = ByteArray(builder633fa8NullLoopLaneBytes)
        var tLane = ByteArray(builder633fa8NullLoopLaneBytes)
        repeat(28) {
            val fLane = vm638840(builder633fa8NullLoopFMagic, dLane, cLane, tables)
            val aSource = builder633fa8NullTableBlock(
                builder633fa8NullLoopStaticATable, builder633fa8NullLoopLaneBytes, tables,
            )
            aLane = vm638840(builder633fa8NullLoopAMagic, aSource, fLane, tables)
            tLane = vm638840(builder633fa8NullLoopTMagic, eLane, aLane, tables)
            bLane = vm638840(builder633fa8NullLoopBMixMagic, bLane, tLane, tables)
            eLane = vm638840(builder633fa8NullLoopEAdvanceMagic, eLane, eLane, tables)
            cLane = vm638840(builder633fa8NullLoopCAdvanceMagic, cLane, cLane, tables)
        }

        finalTLane = tLane
        val fLane = vm638840(builder633fa8NullLoopPostFMagic, bLane, eLane, tables)
        val dSource = builder633fa8NullTableBlock(
            builder633fa8NullLoopStaticDTable + builder633fa8NullLoopLaneBytes * lane,
            builder633fa8NullLoopLaneBytes, tables,
        )
        val postDLane = vm638840(builder633fa8NullLoopPostDMagic, fLane, dSource, tables)
        var packELane = vm641fcc(builder633fa8NullLoopPostEMagic, postDLane, tables)

        val packedLane = aLane.copyOf()
        for (index in 0 until 4) packedLane[index] = 0
        var shift = 32
        for (packIndex in 0 until 8) {
            val cWord = vm638840(builder633fa8NullLoopPackCMagic, packELane, packELane, tables)
            if (shift >= 5) {
                packELane = vm6420d8(builder633fa8NullLoopPackEMagic, packELane, packELane, tables)
            }
            val bWord = vm638840(builder633fa8NullLoopPackBMagic, cWord, cWord, tables)
            val selected = bWord.u8(2) xor (bWord.u8(3) shl 3)
            val packed = builder633fa8NullNibbleByte(builder633fa8NullLoopNibbleTable + selected, tables)
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

        scheduleWords[outerIndex] = readUInt32LE(packedLane, 0)
    }

    return Builder633fa8NullFirstLoopResult(finalTLane, scheduleWords)
}

internal fun builder633fa8NullScheduleAcceptance(
    scheduleWords: UIntArray,
    check1SourceWords: UIntArray,
    check2SourceWords: UIntArray,
): Builder633fa8NullScheduleAcceptance {
    val tables = Libre3FirstPairTables.get()
    val firstOK = builder633fa8NullScheduleCheck(
        scheduleWords, check1SourceWords,
        builder633fa8NullCheck1ScheduleMulTable, builder633fa8NullCheck1SourceMulTable,
        builder633fa8NullCheck1AddTable, builder633fa8NullCheck1FoldTable,
        builder633fa8NullCheck1Target, builder633fa8NullCheck1FoldTarget, tables,
    )
    val secondOK = builder633fa8NullScheduleCheck(
        scheduleWords, check2SourceWords,
        builder633fa8NullCheck2ScheduleMulTable, builder633fa8NullCheck2SourceMulTable,
        builder633fa8NullCheck2AddTable, builder633fa8NullCheck2FoldTable,
        builder633fa8NullCheck2Target, builder633fa8NullCheck2FoldTarget, tables,
    )
    return Builder633fa8NullScheduleAcceptance(firstOK, secondOK)
}

@Suppress("LongMethod")
internal fun builder633fa8NullPostAcceptBlocks(scheduleWords: UIntArray): Builder633fa8NullPostAcceptResult {
    if (scheduleWords.size != builder633fa8ScalarWordCount) {
        throw Libre3CryptoException("the 633fa8 null schedule must be $builder633fa8ScalarWordCount words, not ${scheduleWords.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val initCF0 = builder633fa8NullTableBlock(builder633fa8NullPostInitCF0Static, builder633fa8NullSeedBlockBytes, tables)
    val initBD0 = builder633fa8NullTableBlock(builder633fa8NullPostInitBD0Static, builder633fa8NullSeedBlockBytes, tables)
    val finalCF0Static = builder633fa8NullTableBlock(builder633fa8NullPostFinalCF0Static, builder633fa8NullLoopLaneBytes, tables)
    val finalBD0Static = builder633fa8NullTableBlock(builder633fa8NullPostFinalBD0Static, builder633fa8NullLoopLaneBytes, tables)

    var blocks4080 = ByteArray(0)
    var blocks3f40 = ByteArray(0)

    for (index in scheduleWords.indices) {
        val tableOffset = (index * 4) and 0x1c
        val selector = scheduleWords[index] * u32TableWord63c278(builder633fa8NullPostKeyMulTable + tableOffset, tables) +
            u32TableWord63c278(builder633fa8NullPostKeyAddTable + tableOffset, tables)
        var cf0State = initCF0
        var bd0State = initBD0

        for (shift in intArrayOf(24, 16, 8, 0)) {
            val cf0Source = ByteArray(3) + bytesOf(0x05) +
                cf0State.copyOfRange(0, 8) + cf0State.copyOfRange(8, 12) + cf0State.copyOfRange(12, 14)
            val bd0Source = ByteArray(3) + bytesOf(0x03) +
                bd0State.copyOfRange(0, 8) + bd0State.copyOfRange(8, 12) + bd0State.copyOfRange(12, 14)
            val cf0 = vm638840(builder633fa8NullPostInitCF0Magic, cf0Source, cf0Source, tables)
            val bd0 = vm638840(builder633fa8NullPostInitBD0Magic, bd0Source, bd0Source, tables)
            val byteValue = ((selector shr shift) and 0xffu).toInt()
            val cf0Row = expand3BitPairTableRow633fa8Null(
                builder633fa8NullTableBlock(builder633fa8NullPostTableCF0 + byteValue * 9, 9, tables)
            )
            val bd0Row = expand3BitPairTableRow633fa8Null(
                builder633fa8NullTableBlock(builder633fa8NullPostTableBD0 + byteValue * 9, 9, tables)
            )
            cf0State = vm638840(builder633fa8NullPostMixCF0Magic, cf0, cf0Row, tables)
            bd0State = vm638840(builder633fa8NullPostMixBD0Magic, bd0, bd0Row, tables)
        }

        val e10 = vm638840(builder633fa8NullPostFinalCF0Magic, cf0State, finalCF0Static, tables)
        val ab0 = vm638840(builder633fa8NullPostFinalBD0Magic, bd0State, finalBD0Static, tables)
        blocks4080 += vm638840(builder633fa8NullPostBlock4080Magic, e10, e10, tables)
        blocks3f40 += vm638840(builder633fa8NullPostBlock3f40Magic, ab0, ab0, tables)
    }

    return Builder633fa8NullPostAcceptResult(blocks4080, blocks3f40)
}

internal fun builder633fa8NullPreludeSourceFromPostAccept(blocks4080: ByteArray, blocks3f40: ByteArray): ByteArray {
    if (blocks4080.size != builder633fa8NullSeedBlocksBytes) {
        throw Libre3CryptoException("the 633fa8 null 4080 blocks must be $builder633fa8NullSeedBlocksBytes bytes, not ${blocks4080.size}")
    }
    if (blocks3f40.size != builder633fa8NullSeedBlocksBytes) {
        throw Libre3CryptoException("the 633fa8 null 3f40 blocks must be $builder633fa8NullSeedBlocksBytes bytes, not ${blocks3f40.size}")
    }

    val tables = Libre3FirstPairTables.get()
    val first4080Block = blocks4080.copyOfRange(0, builder633fa8NullSeedBlockBytes)
    val first4080 = vm638840(builder633fa8NullPreludeFirst4080Magic, first4080Block, first4080Block, tables)
    var rest4080 = ByteArray(0)
    for (index in 1 until builder633fa8ScalarWordCount) {
        val start = index * builder633fa8NullSeedBlockBytes
        val block = blocks4080.copyOfRange(start, start + builder633fa8NullSeedBlockBytes)
        rest4080 += vm638840(builder633fa8NullPreludeRest4080Magic, block, block, tables)
    }
    val stitched4080 = stitch633fa8NullPrelude11A(first4080, rest4080)
    val bd0 = vm638840(builder633fa8NullPreludeBD0Magic, stitched4080, stitched4080, tables)

    val first3f40Block = blocks3f40.copyOfRange(0, builder633fa8NullSeedBlockBytes)
    val first3f40 = vm638840(builder633fa8NullPreludeFirst3f40Magic, first3f40Block, first3f40Block, tables)
    var rest3f40 = ByteArray(0)
    for (index in 1 until builder633fa8ScalarWordCount) {
        val start = index * builder633fa8NullSeedBlockBytes
        val block = blocks3f40.copyOfRange(start, start + builder633fa8NullSeedBlockBytes)
        rest3f40 += vm638840(builder633fa8NullPreludeRest3f40Magic, block, block, tables)
    }
    val stitched3f40 = stitch633fa8NullPrelude11A(first3f40, rest3f40)
    val ab0 = vm638840(builder633fa8NullPreludeAB0Magic, stitched3f40, stitched3f40, tables)

    val stage4080 = vm638840(builder633fa8NullPreludeStage4080Magic, bd0, ab0, tables)
    val f40 = vm638840(builder633fa8NullPreludeF40Magic, stage4080, ab0, tables)
    return vm638840(builder633fa8NullPreludeSourceMagic, f40, f40, tables)
}

/**
 * @throws Libre3RejectedEntropyException when this draw of entropy fails the scheme's own checks.
 */
internal fun builder633fa8NullPreludeSourceFromEntropy(entropy11A: ByteArray): ByteArray {
    val sources = builder633fa8NullEntrySourcesFromInvariantEntry()
    val initial = builder633fa8NullInitialFromEntropy(entropy11A, sources.prologueSource)
    val loop = builder633fa8NullFirstLoopFromBlocks(initial.seedBlocks)
    val acceptance = builder633fa8NullScheduleAcceptance(
        loop.scheduleWords, sources.check1SourceWords, sources.check2SourceWords,
    )
    if (!acceptance.firstOK || !acceptance.secondOK) {
        throw Libre3RejectedEntropyException()
    }

    val postAccept = builder633fa8NullPostAcceptBlocks(loop.scheduleWords)
    return builder633fa8NullPreludeSourceFromPostAccept(postAccept.blocks4080, postAccept.blocks3f40)
}

/**
 * The seventy byte scalar window of one accepted draw of entropy.
 *
 * @throws Libre3RejectedEntropyException when this draw is refused.
 */
internal fun builder633fa8NullScalarWindowFromEntropy(entropy11A: ByteArray): ByteArray =
    builder633fa8ScalarWindowFromPreludeSource(builder633fa8NullPreludeSourceFromEntropy(entropy11A))

/** The public point that the same entropy leads to, before the scalar window is even needed. */
internal fun builder633fa8NullPublicEntrySourceFromEntropy(entropy11A: ByteArray): ByteArray {
    val preludeSource = builder633fa8NullPreludeSourceFromEntropy(entropy11A)
    val scalar = builder633fa8ScalarWindowFromPreludeSource(preludeSource)
    return preludeSource + scalar.copyOfRange(0, 0x10)
}

/**
 * Draws entropy until the scheme accepts it.
 *
 * @param maxAttempts how many draws to try before giving up.
 * @param entropySource asked for [builder633fa8NullEntropyBytes] fresh bytes each time.
 */
internal fun builder633fa8NullScalarWindowFromEntropySource(
    maxAttempts: Int = 64,
    entropySource: (Int) -> ByteArray,
): Builder633fa8NullScalarResult {
    if (maxAttempts <= 0) {
        throw Libre3CryptoException("the first pairing needs at least one draw of entropy, not $maxAttempts")
    }

    for (attempt in 1..maxAttempts) {
        val entropy = entropySource(builder633fa8NullEntropyBytes)
        try {
            return Builder633fa8NullScalarResult(
                builder633fa8NullScalarWindowFromEntropy(entropy), entropy, attempt,
            )
        } catch (_: Libre3RejectedEntropyException) {
            continue
        }
    }
    throw Libre3CryptoException("the first pairing scheme refused every one of the $maxAttempts draws of entropy")
}
