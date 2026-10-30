package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException

/*
 * The low seed path of the `6388f0` stream: from the fixed entry source to the three preimages of
 * row zero, and to the twenty schedule words that the `633fa8` scalar window starts from.
 *
 * Ported from LibreCRKit `Crypto/FirstPairSourceSlice.swift` at pin `a86b92f`.
 */

/** The three round seeds of the low seed path. */
internal class Builder6388f0LowSeedCF0Seeds(val phase1: ByteArray, val phase2: ByteArray, val phase3: ByteArray)

/** The two blocks the low seed path ends on. */
internal class Builder6388f0LowSeedTailPair(val left: ByteArray, val right: ByteArray)

/** What the low seed loop produced. */
internal class Builder6388f0LowSeedLoopResult(val final6377f0: ByteArray, val scheduleWords: UIntArray)

/** The three preimages of row zero. */
internal class Builder6388f0Row0LowSeedPreimages(val out4: ByteArray, val out3: ByteArray, val out2: ByteArray)

/** Where the `633fa8` tail starts from. */
internal class Builder633fa8TailBoundary(
    val words3ab0: UIntArray,
    val words3120: UIntArray,
    val words2dfc: UIntArray,
    val seed3110: ULong,
    val preludeSource: ByteArray,
)

private class LowSeedPhaseResult(val finalCF0: ByteArray)

private fun requireEntrySource(entrySource: ByteArray) {
    if (entrySource.size < builder6388f0LowSeedEntrySourceBytes) {
        throw Libre3CryptoException(
            "the 6388f0 low seed entry source must be at least $builder6388f0LowSeedEntrySourceBytes bytes, " +
                "not ${entrySource.size}"
        )
    }
}

private fun builder6388f0LowSeedStaticBlock(libOffset: Int, tables: Libre3FirstPairTables): ByteArray =
    checkedSlice(
        tables.lowSeedStatics6388f0, libOffset - builder6388f0LowSeedStaticBase, builder6388f0LowSeedBlockBytes,
        Libre3FirstPairTables.LOW_SEED_STATICS_6388F0,
    )

private fun builder6388f0LowLoopStaticBlock(libOffset: Int, byteCount: Int, tables: Libre3FirstPairTables): ByteArray =
    checkedSlice(
        tables.lowLoopStatics6388f0, libOffset - builder6388f0LowLoopStaticBase, byteCount,
        Libre3FirstPairTables.LOW_LOOP_STATICS_6388F0,
    )

private fun builder6388f0LowLoopStaticByte(libOffset: Int, tables: Libre3FirstPairTables): Int =
    builder6388f0LowLoopStaticBlock(libOffset, 1, tables).u8(0)

private fun builder6388f0LowSeedE10SourceFromAB0(marker: Int, shift: Int, ab0: ByteArray): ByteArray {
    if (marker > 7) {
        throw Libre3CryptoException("a low seed marker must be at most 7, not $marker")
    }
    if (shift < 1 || shift > builder6388f0LowSeedBlockBytes) {
        throw Libre3CryptoException("a low seed shift must be from 1 to $builder6388f0LowSeedBlockBytes, not $shift")
    }
    val copyCount = builder6388f0LowSeedBlockBytes - shift
    requireSize(ab0, copyCount, "6388f0 low-seed ab0 source")

    val out = ByteArray(builder6388f0LowSeedBlockBytes)
    out[shift - 1] = marker.toByte()
    if (copyCount > 0) {
        ab0.copyInto(out, shift, 0, copyCount)
    }
    return out
}

private fun builder6388f0LowSeedPhaseFromCF0Seed(
    spec: LowSeedPhaseSpec,
    seedCF0: ByteArray,
    tables: Libre3FirstPairTables,
): LowSeedPhaseResult {
    val count = spec.auxMagics.size
    val sizes = listOf(
        "e10 magics" to spec.e10Magics.size,
        "e10 markers" to spec.e10Markers.size,
        "e10 shifts" to builder6388f0LowSeedE10SourceShifts.size,
        "f40 magics" to spec.f40Magics.size,
    )
    for ((label, valueCount) in sizes) {
        if (valueCount != count) {
            throw Libre3CryptoException("the low seed round has $valueCount $label but $count rounds")
        }
    }
    requireSize(seedCF0, builder6388f0LowSeedBlockBytes, "6388f0 low-seed cf0 seed")

    val staticBlock = builder6388f0LowSeedStaticBlock(spec.staticOffset, tables)
    var cf0 = seedCF0
    for (index in 0 until count) {
        val bd0 = vm638840(spec.bd0Magic, cf0, staticBlock, tables)
        val ab0 = vm641fcc(spec.unaryMagic, bd0, tables)
        val e10Source = builder6388f0LowSeedE10SourceFromAB0(
            spec.e10Markers[index], builder6388f0LowSeedE10SourceShifts[index], ab0,
        )
        val e10 = vm638840(spec.e10Magics[index], e10Source, e10Source, tables)
        val f40 = vm6420d8(spec.f40Magics[index], ab0, ab0, tables)
        val aux = vm638840(spec.auxMagics[index], e10, f40, tables)
        cf0 = vm638840(spec.phaseMagic, cf0, aux, tables)
    }
    return LowSeedPhaseResult(cf0)
}

internal fun builder6388f0LowSeedCF0SeedsFromEntrySource(entrySource: ByteArray): Builder6388f0LowSeedCF0Seeds {
    requireEntrySource(entrySource)
    val tables = Libre3FirstPairTables.get()
    val entryHead = entrySource.copyOfRange(0, builder6388f0LowSeedBlockBytes)
    val entryTail = entrySource.copyOfRange(builder6388f0LowSeedBlockBytes, builder6388f0LowSeedEntrySourceBytes)

    val pre2S898 = vm638840(builder6388f0LowSeedEntryS898Magic, entryHead, entryHead, tables)
    val pre2S78E = vm638840(builder6388f0LowSeedEntryS78EMagic, entryTail, entryTail, tables)
    val phase1SeedCF0 = vm641fcc(builder6388f0LowSeedCF0Phase1SeedMagic, pre2S78E, tables)
    val phase1 = builder6388f0LowSeedPhaseFromCF0Seed(builder6388f0LowSeedPhase1Spec, phase1SeedCF0, tables)

    val pre2Static = builder6388f0LowSeedStaticBlock(builder6388f0LowSeedPrev2Static, tables)
    val pre2BD0 = vm638840(builder6388f0LowSeedPrev2BD0Magic, phase1.finalCF0, pre2Static, tables)
    val pre2S684 = vm638840(builder6388f0LowSeedPrev2S684Magic, pre2BD0, pre2BD0, tables)
    val pre2S57A = vm638840(builder6388f0LowSeedMiddleMagic, pre2S898, pre2S684, tables)
    val preS898 = vm638840(builder6388f0LowSeedTailLeftMagic, pre2S78E, pre2S78E, tables)
    val preS78E = vm638840(builder6388f0LowSeedTailRightMagic, pre2S57A, pre2S57A, tables)
    val phase2SeedCF0 = vm641fcc(builder6388f0LowSeedCF0Phase2SeedMagic, preS78E, tables)
    val phase2 = builder6388f0LowSeedPhaseFromCF0Seed(builder6388f0LowSeedPhase2Spec, phase2SeedCF0, tables)

    val preStatic = builder6388f0LowSeedStaticBlock(builder6388f0LowSeedPreStatic, tables)
    val preBD0 = vm638840(builder6388f0LowSeedPreBD0Magic, phase2.finalCF0, preStatic, tables)
    val prevS684 = vm638840(builder6388f0LowSeedPrevS684Magic, preBD0, preBD0, tables)
    val prevS57A = vm638840(builder6388f0LowSeedMiddleMagic, preS898, prevS684, tables)
    val seedS78E = vm638840(builder6388f0LowSeedTailRightMagic, prevS57A, prevS57A, tables)
    val phase3SeedCF0 = vm641fcc(builder6388f0LowSeedCF0Phase3SeedMagic, seedS78E, tables)

    return Builder6388f0LowSeedCF0Seeds(phase1SeedCF0, phase2SeedCF0, phase3SeedCF0)
}

private fun builder6388f0LowSeedTailPairFromSlotState(
    preS898: ByteArray,
    preS78E: ByteArray,
    preBD0: ByteArray,
    tailBD0: ByteArray,
    tables: Libre3FirstPairTables,
): Builder6388f0LowSeedTailPair {
    requireSize(preS898, builder6388f0LowSeedBlockBytes, "6388f0 low-seed pre s898")
    requireSize(preS78E, builder6388f0LowSeedBlockBytes, "6388f0 low-seed pre s78e")
    requireSize(preBD0, builder6388f0LowSeedBlockBytes, "6388f0 low-seed pre bd0")
    requireSize(tailBD0, builder6388f0LowSeedBlockBytes, "6388f0 low-seed tail bd0")

    val prevS684 = vm638840(builder6388f0LowSeedPrevS684Magic, preBD0, preBD0, tables)
    val prevS57A = vm638840(builder6388f0LowSeedMiddleMagic, preS898, prevS684, tables)
    val seedS898 = vm638840(builder6388f0LowSeedTailLeftMagic, preS78E, preS78E, tables)
    val seedS78E = vm638840(builder6388f0LowSeedTailRightMagic, prevS57A, prevS57A, tables)
    val tailS684 = vm638840(builder6388f0LowSeedTailS684Magic, tailBD0, tailBD0, tables)
    val tailS57A = vm638840(builder6388f0LowSeedMiddleMagic, seedS898, tailS684, tables)
    val left = vm638840(builder6388f0LowSeedTailLeftMagic, seedS78E, seedS78E, tables)
    val right = vm638840(builder6388f0LowSeedTailRightMagic, tailS57A, tailS57A, tables)
    return Builder6388f0LowSeedTailPair(left, right)
}

private fun builder6388f0LowSeedTailPairFromEntryAndCF0(
    entrySource: ByteArray,
    pre2CF0: ByteArray,
    preCF0: ByteArray,
    tailCF0: ByteArray,
    tables: Libre3FirstPairTables,
): Builder6388f0LowSeedTailPair {
    requireEntrySource(entrySource)
    requireSize(pre2CF0, builder6388f0LowSeedBlockBytes, "6388f0 low-seed pre2 cf0")
    requireSize(preCF0, builder6388f0LowSeedBlockBytes, "6388f0 low-seed pre cf0")
    requireSize(tailCF0, builder6388f0LowSeedBlockBytes, "6388f0 low-seed tail cf0")

    val entryHead = entrySource.copyOfRange(0, builder6388f0LowSeedBlockBytes)
    val entryTail = entrySource.copyOfRange(builder6388f0LowSeedBlockBytes, builder6388f0LowSeedEntrySourceBytes)
    val pre2S898 = vm638840(builder6388f0LowSeedEntryS898Magic, entryHead, entryHead, tables)
    val pre2S78E = vm638840(builder6388f0LowSeedEntryS78EMagic, entryTail, entryTail, tables)

    val pre2Static = builder6388f0LowSeedStaticBlock(builder6388f0LowSeedPrev2Static, tables)
    val preStatic = builder6388f0LowSeedStaticBlock(builder6388f0LowSeedPreStatic, tables)
    val tailStatic = builder6388f0LowSeedStaticBlock(builder6388f0LowSeedTailStatic, tables)
    val pre2BD0 = vm638840(builder6388f0LowSeedPrev2BD0Magic, pre2CF0, pre2Static, tables)
    val pre2S684 = vm638840(builder6388f0LowSeedPrev2S684Magic, pre2BD0, pre2BD0, tables)
    val pre2S57A = vm638840(builder6388f0LowSeedMiddleMagic, pre2S898, pre2S684, tables)
    val preS898 = vm638840(builder6388f0LowSeedTailLeftMagic, pre2S78E, pre2S78E, tables)
    val preS78E = vm638840(builder6388f0LowSeedTailRightMagic, pre2S57A, pre2S57A, tables)
    val preBD0 = vm638840(builder6388f0LowSeedPreBD0Magic, preCF0, preStatic, tables)
    val tailBD0 = vm638840(builder6388f0LowSeedTailBD0Magic, tailCF0, tailStatic, tables)
    return builder6388f0LowSeedTailPairFromSlotState(preS898, preS78E, preBD0, tailBD0, tables)
}

internal fun builder6388f0LowSeedTailPairFromEntrySource(entrySource: ByteArray): Builder6388f0LowSeedTailPair {
    val tables = Libre3FirstPairTables.get()
    val seeds = builder6388f0LowSeedCF0SeedsFromEntrySource(entrySource)
    val phase1 = builder6388f0LowSeedPhaseFromCF0Seed(builder6388f0LowSeedPhase1Spec, seeds.phase1, tables)
    val phase2 = builder6388f0LowSeedPhaseFromCF0Seed(builder6388f0LowSeedPhase2Spec, seeds.phase2, tables)
    val phase3 = builder6388f0LowSeedPhaseFromCF0Seed(builder6388f0LowSeedPhase3Spec, seeds.phase3, tables)
    return builder6388f0LowSeedTailPairFromEntryAndCF0(
        entrySource, phase1.finalCF0, phase2.finalCF0, phase3.finalCF0, tables,
    )
}

internal fun builder6388f0LowSeedTailStageFromPair(pair: Builder6388f0LowSeedTailPair): ByteArray {
    val tables = Libre3FirstPairTables.get()
    requireSize(pair.left, builder6388f0LowSeedBlockBytes, "6388f0 low-seed tail left")
    requireSize(pair.right, builder6388f0LowSeedBlockBytes, "6388f0 low-seed tail right")
    return vm638840(builder6388f0LowSeedTailStageMagic, pair.left, pair.right, tables)
}

internal fun builder6388f0LowSeedPreludeSourceFromTailStage(tailStage: ByteArray): ByteArray {
    val tables = Libre3FirstPairTables.get()
    requireSize(tailStage, builder6388f0LowSeedBlockBytes, "6388f0 low-seed tail stage")
    val stage = vm638840(builder6388f0LowSeedPreludeStageMagic, tailStage, tailStage, tables)
    return vm638840(builder6388f0LowSeedPreludeSourceMagic, stage, stage, tables)
}

internal fun builder6388f0LowSeedBlocksFromPreludeSource(preludeSource: ByteArray): ByteArray {
    val tables = Libre3FirstPairTables.get()
    requireSize(preludeSource, builder6388f0LowSeedBlockBytes, "6388f0 low-seed prelude source")
    val expanded = vm6420d8(builder6388f0LowSeedPreludeMagic, preludeSource, preludeSource, tables)
    if (expanded.size != builder6388f0LowSeedExpandedPreludeBytes) {
        throw Libre3CryptoException("the low seed prelude came back as ${expanded.size} bytes")
    }

    var out = ByteArray(0)
    for (index in 0 until 19) {
        val start = index * 0x0e
        val block = expanded.copyOfRange(start, start + 0x10)
        out += vm638840(builder6388f0Row0SeedBlockMagic, block, block, tables)
    }
    val staticBlock = builder6388f0LowLoopStaticBlock(builder6388f0LowSeedStaticBlockOffset, 0x10, tables)
    out += vm638840(builder6388f0Row0SeedBlockMagic, staticBlock, staticBlock, tables)
    return out
}

/**
 * The twenty round loop of the low seed path.
 *
 * Each round runs twenty eight small steps, then packs eight nibbles into one schedule word. The
 * running `shift` is a bit budget: once it runs out, the last nibbles are masked away.
 */
internal fun builder6388f0LowSeedLoopFromBlocks(seedBlocks: ByteArray): Builder6388f0LowSeedLoopResult {
    if (seedBlocks.size != builder6388f0LowSeedSeedBlocksBytes) {
        throw Libre3CryptoException(
            "the low seed blocks must be $builder6388f0LowSeedSeedBlocksBytes bytes, not ${seedBlocks.size}"
        )
    }
    val tables = Libre3FirstPairTables.get()
    val scheduleWords = UIntArray(20)
    var final6377f0 = ByteArray(0)

    for (outerIndex in 0 until 20) {
        val lane = outerIndex and 7
        var cLane = builder6388f0LowLoopStaticBlock(builder6388f0LowLoopStaticCTable, 0x10, tables) + bytesOf(0x05, 0x04)

        val eSource = builder6388f0LowLoopStaticBlock(
            builder6388f0LowLoopStaticETable + builder6388f0LowLoopLaneBytes * lane,
            builder6388f0LowLoopLaneBytes, tables,
        )
        var eLane = vm638840(builder6388f0LowLoopEInitMagic, eSource, eSource, tables)
        val blockOffset = outerIndex * 0x10
        val block = seedBlocks.copyOfRange(blockOffset, blockOffset + 0x10)
        val dLane = vm6420d8(builder6388f0LowLoopDInitMagic, block, block, tables)
        var bLane = vm638840(builder6388f0LowLoopBInitMagic, eLane, eLane, tables)

        var aLane = ByteArray(builder6388f0LowLoopLaneBytes)
        var tLane = ByteArray(builder6388f0LowLoopLaneBytes)
        repeat(28) {
            val fLane = vm638840(builder6388f0LowLoopFMagic, dLane, cLane, tables)
            val aSource = builder6388f0LowLoopStaticBlock(
                builder6388f0LowLoopStaticATable, builder6388f0LowLoopLaneBytes, tables,
            )
            aLane = vm638840(builder6388f0LowLoopAMagic, aSource, fLane, tables)
            tLane = vm638840(builder6388f0LowLoopTMagic, eLane, aLane, tables)
            bLane = vm638840(builder6388f0LowLoopBMixMagic, bLane, tLane, tables)
            eLane = vm638840(builder6388f0LowLoopEAdvanceMagic, eLane, eLane, tables)
            cLane = vm638840(builder6388f0LowLoopCAdvanceMagic, cLane, cLane, tables)
        }

        final6377f0 = tLane
        val fLane = vm638840(builder6388f0LowLoopPostFMagic, bLane, eLane, tables)
        val dSource = builder6388f0LowLoopStaticBlock(
            builder6388f0LowLoopStaticDTable + builder6388f0LowLoopLaneBytes * lane,
            builder6388f0LowLoopLaneBytes, tables,
        )
        val postDLane = vm638840(builder6388f0LowLoopPostDMagic, fLane, dSource, tables)
        var packELane = vm641fcc(builder6388f0LowLoopPostEMagic, postDLane, tables)

        val packedLane = aLane.copyOf()
        for (index in 0 until 4) packedLane[index] = 0
        var shift = 32
        for (packIndex in 0 until 8) {
            val cWord = vm638840(builder6388f0LowLoopPackCMagic, packELane, packELane, tables)
            if (shift >= 5) {
                packELane = vm6420d8(builder6388f0LowLoopPackEMagic, packELane, packELane, tables)
            }
            val bWord = vm638840(builder6388f0LowLoopPackBMagic, cWord, cWord, tables)
            val selected = bWord.u8(2) xor (bWord.u8(3) shl 3)
            val packed = builder6388f0LowLoopStaticByte(builder6388f0LowLoopNibbleTable + selected, tables)
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

    return Builder6388f0LowSeedLoopResult(final6377f0, scheduleWords)
}

internal fun builder6388f0Row0LowSeedPreimagesFromEntrySource(entrySource: ByteArray): Builder6388f0Row0LowSeedPreimages {
    val tables = Libre3FirstPairTables.get()
    val seeds = builder6388f0LowSeedCF0SeedsFromEntrySource(entrySource)
    val phase3 = builder6388f0LowSeedPhaseFromCF0Seed(builder6388f0LowSeedPhase3Spec, seeds.phase3, tables)
    val tailStatic = builder6388f0LowSeedStaticBlock(builder6388f0LowSeedTailStatic, tables)
    val tailBD0 = vm638840(builder6388f0LowSeedTailBD0Magic, phase3.finalCF0, tailStatic, tables)
    val tailS684 = vm638840(builder6388f0LowSeedTailS684Magic, tailBD0, tailBD0, tables)
    val pair = builder6388f0LowSeedTailPairFromEntrySource(entrySource)
    val tailStage = builder6388f0LowSeedTailStageFromPair(pair)
    val preludeSource = builder6388f0LowSeedPreludeSourceFromTailStage(tailStage)
    val seedBlocks = builder6388f0LowSeedBlocksFromPreludeSource(preludeSource)
    val loop = builder6388f0LowSeedLoopFromBlocks(seedBlocks)

    val baseOut3 = tailS684.copyOfRange(204, 266) + pair.right.copyOfRange(0, 26)
    val out3 = baseOut3.copyOfRange(0, 62) + loop.final6377f0 + baseOut3.copyOfRange(baseOut3.size - 8, baseOut3.size)
    return Builder6388f0Row0LowSeedPreimages(
        tailS684.copyOfRange(116, 204),
        out3,
        pair.right.copyOfRange(26, 114),
    )
}

internal fun builder633fa8StaticPreludeSourceFromEntrySource(entrySource: ByteArray): ByteArray {
    val pair = builder6388f0LowSeedTailPairFromEntrySource(entrySource)
    return builder6388f0LowSeedPreludeSourceFromTailStage(builder6388f0LowSeedTailStageFromPair(pair))
}

internal fun builder633fa8StaticTailBoundaryFromEntrySource(entrySource: ByteArray): Builder633fa8TailBoundary =
    builder633fa8TailBoundaryFromPreludeSource(builder633fa8StaticPreludeSourceFromEntrySource(entrySource))

internal fun builder633fa8TailBoundaryFromPreludeSource(preludeSource: ByteArray): Builder633fa8TailBoundary {
    val seedBlocks = builder6388f0LowSeedBlocksFromPreludeSource(preludeSource)
    val loop = builder6388f0LowSeedLoopFromBlocks(seedBlocks)
    return Builder633fa8TailBoundary(
        loop.scheduleWords,
        builder633fa8InvariantWords3120,
        builder633fa8InvariantWords2dfc,
        builder633fa8InvariantSeed3110,
        preludeSource,
    )
}

internal fun builder633fa8ScalarWindowFromPreludeSource(preludeSource: ByteArray): ByteArray {
    val boundary = builder633fa8TailBoundaryFromPreludeSource(preludeSource)
    val qwords = builder633fa8TailQwordsFromSources(
        boundary.words3ab0, boundary.words3120, boundary.words2dfc, boundary.seed3110,
    )
    return builder633fa8ScalarWindowFromE10Words(builder633fa8E10WordsFromTailQwords(qwords))
}

/** The seventy byte scalar window of the static branch, from the fixed entry source. */
internal fun builder633fa8StaticScalarWindowFromEntrySource(entrySource: ByteArray): ByteArray =
    builder633fa8ScalarWindowFromPreludeSource(builder633fa8StaticPreludeSourceFromEntrySource(entrySource))
