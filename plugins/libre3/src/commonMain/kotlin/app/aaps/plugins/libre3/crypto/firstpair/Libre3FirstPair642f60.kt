package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException

/*
 * The `642f60` builder: three arguments and a shared context in, three answers out.
 *
 * Ported from LibreCRKit `Crypto/FirstPairSourceSlice.swift` at pin `a86b92f`.
 *
 * It runs eight rounds of the `64bd0c` engine, each fed by its own stage. The order of the rounds
 * matters: every stage reads an answer of an earlier one.
 */

/** The three answers of one `642f60` call. */
internal class Builder642f60Result(val out0: ByteArray, val out1: ByteArray, val out2: ByteArray)

/** The two table pieces of the caller loop. */
internal class Builder6388f0CallerLoopTables(val first: ByteArray, val second: ByteArray)

/** The `642f60` arguments of the two rows a first pairing needs. */
internal class Builder6388f0FirstPair642f60Starts(
    val row0: Builder6388f0Next642f60Inputs,
    val row59: Builder6388f0Next642f60Inputs,
)

private fun requireVector(words: UIntArray) {
    if (words.size != builder63c278VectorWords) {
        throw Libre3CryptoException("a 642f60 stream must be $builder63c278VectorWords words, not ${words.size}")
    }
}

private fun requireVector64(words: ULongArray, expected: Int = builder63c278VectorWords) {
    if (words.size != expected) {
        throw Libre3CryptoException("a 642f60 stream must be $expected long words, not ${words.size}")
    }
}

private fun requireSource(source: ByteArray, label: String, needed: Int = builder63c278VectorBytes) {
    if (source.size < needed) {
        throw Libre3CryptoException("the $label must be at least $needed bytes, not ${source.size}")
    }
}

/** The shared shape of the stream words that come from one table driven affine step. */
@Suppress("LongParameterList")
internal fun u64StreamWordFromU32Affine(
    word: UInt,
    index: Int,
    mulTable: Int,
    addTable: Int,
    u32Mul: UInt,
    u32Add: UInt,
    foldMul: ULong,
    foldAdd: ULong,
    foldTable: Int,
    linearMul: ULong,
    foldedMul: ULong,
    linearAdd: ULong,
    tables: Libre3FirstPairTables,
): ULong {
    val affine = u32Affine63c278(word, index, mulTable, addTable, tables)
    val w = affine * u32Mul + u32Add
    val folded = fold63c278(w.toULong() * foldMul + foldAdd, foldTable, 8, tables)
    return w.toULong() * linearMul + folded * foldedMul + linearAdd
}

// ------------------------------------------------------------ the caller context

internal fun builder6388f0FirstPair642f60StreamStarts(
    seeds: Builder6388f0FirstPairStreamSeeds,
    x2Source: ByteArray? = null,
): Builder6388f0FirstPair642f60Starts = Builder6388f0FirstPair642f60Starts(
    builder6388f0StreamStart642f60Inputs(seeds.row0Out0, seeds.row0Out1, x2Source),
    builder6388f0StreamStart642f60Inputs(seeds.row59Out0, seeds.row59Out1, x2Source),
)

internal fun builder6388f0SharedContextFromBundle(): ByteArray = Libre3FirstPairTables.get().sharedContext6388f0

/** Splits the shipped interleaved table into the two tables the caller loop reads. */
internal fun builder6388f0CallerLoopTablesFromBundle(): Builder6388f0CallerLoopTables {
    val interleaved = Libre3FirstPairTables.get().callerLoopInterleaved6388f0
    var first = ByteArray(0)
    var second = ByteArray(0)
    for (row in 0 until builder6388f0CallerLoopTableRows) {
        val rowOffset = row * builder6388f0CallerLoopInterleavedRowBytes
        first += interleaved.copyOfRange(rowOffset, rowOffset + builder6388f0CallerLoopRowBytes)
        second += interleaved.copyOfRange(
            rowOffset + builder6388f0CallerLoopRowBytes, rowOffset + builder6388f0CallerLoopInterleavedRowBytes,
        )
    }
    return Builder6388f0CallerLoopTables(first, second)
}

internal fun builder6388f0CallerContextFromLoopTables(loopTables: Builder6388f0CallerLoopTables): ByteArray {
    requireSource(loopTables.first, "6388f0 caller loop table 1", builder6388f0CallerLoopTableBytes)
    requireSource(loopTables.second, "6388f0 caller loop table 2", builder6388f0CallerLoopTableBytes)

    val context = ByteArray(builder6388f0CallerContextLength)
    replace(context, 0, builder6388f0SharedContextFromBundle())
    replace(context, builder6388f0CallerLoopTable1ContextOffset, loopTables.first.copyOfRange(0, builder6388f0CallerLoopTableBytes))
    replace(context, builder6388f0CallerLoopTable2ContextOffset, loopTables.second.copyOfRange(0, builder6388f0CallerLoopTableBytes))
    return context
}

internal fun builder6388f0CallerContextFromBundle(): ByteArray =
    builder6388f0CallerContextFromLoopTables(builder6388f0CallerLoopTablesFromBundle())

// ------------------------------------------------------------ the eight stages

internal fun builder642f60StageSP2A8WordsFromX1(x1Source: ByteArray): UIntArray {
    requireSource(x1Source, "642f60 x1 source")
    val tables = Libre3FirstPairTables.get()
    var state = 0x373c5287u
    val out = UIntArray(builder63c278VectorWords)

    for (index in 0 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        val word = readUInt32LE(x1Source, index * 4)
        val mixed = u32TableAffine63c278(
            word, builder642f60SP2A8X1MulTable + tableOffset, builder642f60SP2A8X1AddTable + tableOffset, tables,
        ) * 0x3c4be1d6u
        state = state * 0x92c1f72bu + mixed + 0xb77cdf91u

        var folded = state * 0x52c0ee2fu + 0xaec98dccu
        val sideBase = state * 0x58fd5601u
        folded = fold32ByNibbles63c278(folded, builder642f60SP2A8FoldTable, 7, tables)
        val side = sideBase + (folded shl 28) + 0x79c97500u
        val folded8 = fold32ByNibbles63c278(folded, builder642f60SP2A8FoldTable, 1, tables)

        out[index] = u32TableAffine63c278(
            side, builder642f60SP2A8OutMulTable + tableOffset, builder642f60SP2A8OutAddTable + tableOffset, tables,
        )
        state = folded * 0x01d6d2edu + folded8 * 0xe292d130u + 0x57678c8fu
    }
    return out
}

internal fun builder642f60StageSP300WordsFrom64bd0cOutput(output: ByteArray): UIntArray =
    builder642f60AffineWordsFrom64bd0cOutput(output, builder642f60SP300MulTable, builder642f60SP300AddTable, "642f60 first 64bd0c answer")

internal fun builder642f60StageSP250WordsFrom64bd0cOutput(output: ByteArray): UIntArray =
    builder642f60AffineWordsFrom64bd0cOutput(output, builder642f60SP250MulTable, builder642f60SP250AddTable, "642f60 second 64bd0c answer")

internal fun builder642f60StageSP148WordsFrom64bd0cOutput(output: ByteArray): UIntArray =
    builder642f60AffineWordsFrom64bd0cOutput(output, builder642f60SP148MulTable, builder642f60SP148AddTable, "642f60 third 64bd0c answer")

internal fun builder642f60StageSPF0WordsFrom64bd0cOutput(output: ByteArray): UIntArray =
    builder642f60AffineWordsFrom64bd0cOutput(output, builder642f60SPF0MulTable, builder642f60SPF0AddTable, "642f60 fourth 64bd0c answer")

internal fun builder642f60StageSP1A0WordsFrom64bd0cOutput(output: ByteArray): UIntArray =
    builder642f60AffineWordsFrom64bd0cOutput(output, builder642f60SP1A0MulTable, builder642f60SP1A0AddTable, "642f60 fifth 64bd0c answer")

internal fun builder642f60StageSP1F8WordsFromX0(x0Source: ByteArray): UIntArray {
    requireSource(x0Source, "642f60 x0 source")
    val tables = Libre3FirstPairTables.get()
    var state = 0x27b40eb7u
    val out = UIntArray(builder63c278VectorWords)

    for (index in 0 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        val word = readUInt32LE(x0Source, index * 4)
        val mixed = u32TableAffine63c278(
            word, builder642f60SP1F8X0MulTable + tableOffset, builder642f60SP1F8X0AddTable + tableOffset, tables,
        ) * 0x8c0bfb6eu
        state = state * 0xcfb36435u + mixed + 0x11d2681du

        var folded = state * 0xc337e20fu + 0x69960635u
        folded = fold32ByNibbles63c278(folded, builder642f60SP1F8FoldTable, 7, tables)
        val side = state * 0x37e76a4du + folded * 0xd0000000u + 0x0a2a2ce9u
        val folded8 = fold32ByNibbles63c278(folded, builder642f60SP1F8FoldTable, 1, tables)

        out[index] = u32TableAffine63c278(
            side, builder642f60SP1F8OutMulTable + tableOffset, builder642f60SP1F8OutAddTable + tableOffset, tables,
        )
        state = folded * 0x01e08913u + folded8 * 0xe1f76ed0u + 0xe153bedeu
    }
    return out
}

internal fun builder642f60First64bd0cWorkspaceFromX1(x1Source: ByteArray, sp2a8Words: UIntArray): ByteArray {
    requireSource(x1Source, "642f60 x1 source")
    requireVector(sp2a8Words)
    val tables = Libre3FirstPairTables.get()
    val aWords = ULongArray(builder63c278VectorWords) { builder642f60FirstAWord(readUInt32LE(x1Source, it * 4), it, tables) }
    val bWords = ULongArray(builder63c278VectorWords) { builder642f60FirstBWord(sp2a8Words[it], it, tables) }
    return convolutionWorkspaceU64(
        aWords, bWords,
        baseAdd = 0xc69bed71f29f125auL, countMul = 0x90f419f6ac783668uL, productMul = 0x4cb8f06bf0049b7duL,
        sumAMul = 0x0f7eac37b6812618uL, sumBMul = 0xd1388a4d4ecb84f3uL,
        finalMul = 0xdacc0c3ac7084aaduL, finalAdd = 0x094d3bfe92d4e136uL,
    )
}

internal fun builder642f60Second64bd0cWorkspace(sp1f8Words: UIntArray, sp300Words: UIntArray): ByteArray {
    requireVector(sp1f8Words)
    requireVector(sp300Words)
    val tables = Libre3FirstPairTables.get()
    val aWords = ULongArray(builder63c278VectorWords) { builder642f60SecondAWord(sp1f8Words[it], it, tables) }
    val bWords = ULongArray(builder63c278VectorWords) { builder642f60SecondBWord(sp300Words[it], it, tables) }
    return convolutionWorkspaceU64(
        aWords, bWords,
        baseAdd = 0x65de471500bb3121uL, countMul = 0x5b751607ca3bf450uL, productMul = 0xf90d1f20daf847f7uL,
        sumAMul = 0x3bd2bf8830ac06c7uL, sumBMul = 0x8510f1581a89dd50uL,
        finalMul = 0x95f22cc42a8e1323uL, finalAdd = 0x54b290ac63e72185uL,
    )
}

internal fun builder642f60Third64bd0cWorkspaceFromX2(x2Source: ByteArray): ByteArray {
    requireSource(x2Source, "642f60 x2 source")
    val tables = Libre3FirstPairTables.get()
    val sourceWords = UIntArray(builder63c278VectorWords) { readUInt32LE(x2Source, it * 4) }
    val aWords = ULongArray(builder63c278VectorWords) { builder642f60ThirdAWord(sourceWords[it], it, tables) }
    val bWords = ULongArray(builder63c278VectorWords) { builder642f60ThirdBWord(sourceWords[it], it, tables) }
    return convolutionWorkspaceU64(
        aWords, bWords,
        baseAdd = 0x5b1e432b74fd20f9uL, countMul = 0xd6a9de8138afb1c4uL, productMul = 0x491764cf27f996a7uL,
        sumAMul = 0x5f259b9e6d3d894fuL, sumBMul = 0x0634d81d5a7a1464uL,
        finalMul = 0x81b9bc3ed86899dbuL, finalAdd = 0x7f3bdcb4320a4605uL,
    )
}

internal fun builder642f60Fourth64bd0cWorkspace(sp148Words: UIntArray): ByteArray {
    requireVector(sp148Words)
    val tables = Libre3FirstPairTables.get()
    val aWords = ULongArray(builder63c278VectorWords) { builder642f60FourthAWord(sp148Words[it], it, tables) }
    val bWords = ULongArray(builder63c278VectorWords) { builder642f60FourthBWord(sp148Words[it], it, tables) }
    return convolutionWorkspaceU64(
        aWords, bWords,
        baseAdd = 0xca87452057c62cf5uL, countMul = 0x33f7ea217636a2b0uL, productMul = 0x25c9902b9655a323uL,
        sumAMul = 0x5486edf9ebf09668uL, sumBMul = 0x9ae2908cd350c4cauL,
        finalMul = 0xc84690dc7332d8bfuL, finalAdd = 0x2381c41e82ce093duL,
    )
}

// ------------------------------------------------------------ the middle stage

internal fun builder642f60MidStageSPA90WordsFromX0(x0Source: ByteArray): ULongArray {
    requireSource(x0Source, "642f60 x0 source")
    val tables = Libre3FirstPairTables.get()
    return ULongArray(builder63c278VectorWords) { index ->
        val affine = u32Affine63c278(
            readUInt32LE(x0Source, index * 4), index, builder642f60MidAX0MulTable, builder642f60MidAX0AddTable, tables,
        )
        val word = affine * 0x3c1bc237u + 0xd6718b75u
        val folded = fold63c278(
            word.toULong() * 0x4570116131d5875buL + 0x4ca880cd5cde550euL, builder642f60MidAFoldTable, 8, tables,
        )
        word.toULong() * 0xc7860ccbc266aa3duL + folded * 0xbffb9fb900000000uL + 0x0bfdc66a47f4cadfuL
    }
}

/**
 * The middle stage that walks a vector from both ends at once.
 *
 * The `while (high > low)` walk and the odd centre step are what the original does.
 */
internal fun builder642f60MidStageSP40WordsFromSPA90(spa90Words: ULongArray): UIntArray {
    requireVector64(spa90Words)
    val tables = Libre3FirstPairTables.get()
    var carry = 0xd2263697af87081fuL
    val out = UIntArray(builder64bd0cWorkspaceWords)

    for (index in 0 until builder64bd0cWorkspaceWords) {
        var low = maxOf(0, index - 21)
        var high = minOf(index, 21)
        var accum = 0x4ca9f4732c4678dauL
        while (high > low) {
            val highWord = spa90Words[high]
            val lowWord = spa90Words[low]
            val left = highWord * 0xb2358691225cfc35uL + 0xdf9a7386fc929cb6uL
            accum = left * lowWord + highWord * 0xdf9a7386fc929cb6uL + accum + 0xe1240ffc79c75054uL
            high -= 1
            low += 1
        }

        val mixed = accum * 0x7047539999fd499euL
        carry *= 0x7d2900791bc15f17uL
        val state = if (high == low) {
            val center = spa90Words[high]
            (center * 0xe6b5f1d6d357e2dbuL + 0x7888b2a9570a9e54uL) * center + mixed + carry + 0xe771da0c03bd224cuL
        } else {
            mixed + carry + 0xb928102d38c55e60uL
        }

        val folded7 = fold63c278(
            state * 0x93dfdd33afa41fcbuL + 0xb5028820475851e2uL, builder642f60MidSP40FoldTable, 7, tables,
        )
        val folded16 = fold63c278(folded7, builder642f60MidSP40FoldTable, 9, tables)
        val side = state.toUInt() * 0x6d4b301fu + folded7.toUInt() * 0x30000000u + 0xb22b53c3u
        carry = folded7 * 0x33b21893aa33e715uL + folded16 * 0x5cc18eb000000000uL + 0xd1af5299bbb3ce82uL

        val tableOffset = (index * 4) and 0x1c
        out[index] = u32TableAffine63c278(
            side, builder642f60MidSP40OutMulTable + tableOffset, builder642f60MidSP40OutAddTable + tableOffset, tables,
        )
    }
    return out
}

/** Four streams of the middle stage: two word runs and their running totals. */
internal class Libre3MidStageStreams(
    val spa90Words: ULongArray,
    val sp510Prefix: ULongArray,
    val sp880Words: ULongArray,
    val sp9e0Prefix: ULongArray,
)

internal fun builder642f60MidStageStreamsFromContextSPF0(contextSource: ByteArray, spf0Words: UIntArray): Libre3MidStageStreams {
    requireSource(contextSource, "642f60 context source", 0x100)
    requireVector(spf0Words)
    val tables = Libre3FirstPairTables.get()
    val contextWords = UIntArray(builder63c278VectorWords) { readUInt32LE(contextSource, 0xa8 + it * 4) }
    val spa90Words = ULongArray(builder63c278VectorWords) { builder642f60MidContextStreamWord(contextWords[it], it, tables) }
    val sp880Words = ULongArray(builder63c278VectorWords) { builder642f60MidSPF0StreamWord(spf0Words[it], it, tables) }
    return Libre3MidStageStreams(spa90Words, prefixSumsU64(spa90Words), sp880Words, prefixSumsU64(sp880Words))
}

internal fun builder642f60MidStageSP670Words(streams: Libre3MidStageStreams): ULongArray {
    requireVector64(streams.spa90Words)
    requireVector64(streams.sp510Prefix)
    requireVector64(streams.sp880Words)
    requireVector64(streams.sp9e0Prefix)

    val out = ULongArray(builder64bd0cWorkspaceWords)
    for (index in 0 until builder64bd0cWorkspaceWords) {
        val start = maxOf(0, index - 21)
        val end = minOf(index, 21)
        var productSum = 0uL
        if (start <= end) {
            for (position in start..end) {
                productSum += streams.spa90Words[position] * streams.sp880Words[index - position]
            }
        }
        val span = if (start <= end) (end - start + 1).toULong() else 0uL
        val sumA = rangeSumFromPrefix(streams.sp510Prefix, start, end)
        val sumB = rangeSumFromPrefix(streams.sp9e0Prefix, index - end, index - start)
        val mixed = span * 0x268d985caf171be0uL +
            0x91891dd268ac7a45uL +
            productSum * 0xbc643695604233c9uL +
            sumA * 0x55d047a51fd1fdd0uL +
            sumB * 0x268318c9a7c7fd06uL
        out[index] = mixed * 0xc36e55bcdc7360d9uL + 0x20aeeecb67e4d8eeuL
    }
    return out
}

/** The word run of the middle stage that comes out of `sp40`, with its running totals. */
internal class Libre3MidSP40Streams(val spa90Words: ULongArray, val sp880Prefix: ULongArray, val sideInit: ULong)

internal fun builder642f60MidStageSPA90SP880FromSP40(sp40Words: UIntArray): Libre3MidSP40Streams {
    if (sp40Words.size != builder64bd0cWorkspaceWords) {
        throw Libre3CryptoException("the 642f60 sp40 words must be $builder64bd0cWorkspaceWords, not ${sp40Words.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val spa90Words = ULongArray(builder64bd0cWorkspaceWords) { builder642f60MidSP40BWord(sp40Words[it], it, tables) }
    return Libre3MidSP40Streams(spa90Words, prefixSumsU64(spa90Words), 0x9b3fe2a5f2a431c6uL)
}

/** The fixed word run of the middle stage, with its running totals. */
internal class Libre3MidStaticStreams(val sp9e0Words: ULongArray, val sp7d0Prefix: ULongArray)

internal fun builder642f60MidStageStaticSP9E0SP7D0(sideInit: ULong): Libre3MidStaticStreams {
    val tables = Libre3FirstPairTables.get()
    val sp9e0Words = ULongArray(builder63c278VectorWords)
    sp9e0Words[0] = sideInit
    for (index in 1 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        val source = foldTableU32Word63c278(builder642f60MidStaticSrcTable + index * 4, tables)
        val multiplier = u32TableWord63c278(builder642f60MidStaticMulTable + tableOffset, tables)
        val addend = u32TableWord63c278(builder642f60MidStaticAddTable + tableOffset, tables)
        val word = (source * multiplier + addend) * 0x8e3923f3u + 0xdcf87258u
        val folded = fold63c278(
            word.toULong() * 0x76e0c10d644166b9uL + 0x9f48a8b2fd92040duL, builder642f60MidStaticFoldTable, 8, tables,
        )
        sp9e0Words[index] = word.toULong() * 0x5a02cb2433277ab9uL + folded * 0xedf34bff00000000uL + 0x6f59c0117d1d1775uL
    }
    return Libre3MidStaticStreams(sp9e0Words, prefixSumsU64(sp9e0Words))
}

internal fun builder642f60MidStageSP510Words(sp40: Libre3MidSP40Streams, static: Libre3MidStaticStreams): ULongArray {
    requireVector64(sp40.spa90Words, builder64bd0cWorkspaceWords)
    requireVector64(sp40.sp880Prefix, builder64bd0cWorkspaceWords)
    requireVector64(static.sp9e0Words)
    requireVector64(static.sp7d0Prefix)

    val out = ULongArray(builder64bd0cWorkspaceWords)
    for (index in 0 until builder64bd0cWorkspaceWords) {
        val start = maxOf(0, index - 21)
        val end = index
        var productSum = 0uL
        for (position in start..end) {
            productSum += sp40.spa90Words[position] * static.sp9e0Words[index - position]
        }
        val span = end - start + 1
        val sumA = rangeSumFromPrefix(sp40.sp880Prefix, start, end)
        val sumB = static.sp7d0Prefix[span - 1]
        val mixed = productSum * 0x29f4a886cd96e34duL +
            span.toULong() * 0xfb27869a34fe306euL +
            sumA * 0x7228cc7a696bf425uL +
            sumB * 0x4005e2eb6883e7deuL
        out[index] = mixed * 0x10af80ba2ba8ff03uL + 0x603f2c10b20e1521uL
    }
    return out
}

internal fun builder642f60MidFifth64bd0cWorkspace(sp670Words: ULongArray, sp510Words: ULongArray): ByteArray {
    requireVector64(sp670Words, builder64bd0cWorkspaceWords)
    requireVector64(sp510Words, builder64bd0cWorkspaceWords)
    val out = ByteArray(builder64bd0cWorkspaceBytes)
    for (index in 0 until builder64bd0cWorkspaceWords) {
        writeUInt64LE(
            sp670Words[index] * 0x311e50313531405duL + sp510Words[index] * 0xc817dbca0a20eafduL + 0xc254ca1fa792908cuL,
            out, index * 8,
        )
    }
    return out
}

// ------------------------------------------------------------ the sixth round

/** Two word runs and their running totals. */
internal class Libre3PairedStreams(
    val spa90Words: ULongArray,
    val sp670Prefix: ULongArray,
    val sp880Words: ULongArray,
    val sp510Prefix: ULongArray,
)

internal fun builder642f60SixthStreamsFromSP1A0(sp1a0Words: UIntArray): Libre3PairedStreams {
    requireVector(sp1a0Words)
    val tables = Libre3FirstPairTables.get()
    val spa90Words = ULongArray(builder63c278VectorWords) { builder642f60SixthAWord(sp1a0Words[it], it, tables) }
    val sp880Words = ULongArray(builder63c278VectorWords) { builder642f60SixthBWord(sp1a0Words[it], it, tables) }
    return Libre3PairedStreams(spa90Words, prefixSumsU64(spa90Words), sp880Words, prefixSumsU64(sp880Words))
}

internal fun builder642f60Sixth64bd0cWorkspace(streams: Libre3PairedStreams): ByteArray {
    requireVector64(streams.spa90Words)
    requireVector64(streams.sp670Prefix)
    requireVector64(streams.sp880Words)
    requireVector64(streams.sp510Prefix)

    val out = ByteArray(builder64bd0cWorkspaceBytes)
    for (index in 0 until builder64bd0cWorkspaceWords) {
        val start = maxOf(0, index - 21)
        val end = minOf(index, 21)
        var productSum = 0uL
        if (start <= end) {
            for (position in start..end) {
                productSum += streams.spa90Words[position] * streams.sp880Words[index - position]
            }
        }
        val span = if (start <= end) (end - start + 1).toULong() else 0uL
        val sumA = rangeSumFromPrefix(streams.sp670Prefix, start, end)
        val sumB = rangeSumFromPrefix(streams.sp510Prefix, index - end, index - start)
        val mixed = span * 0xc9579b83c731c3c0uL +
            0x5c81c51b07a75dd5uL +
            productSum * 0x5af5ce9c3c24da93uL +
            sumA * 0x22758d71fea188c0uL +
            sumB * 0xf1d0ed7a635c3b3fuL
        writeUInt64LE(mixed * 0xa731aa4721be8565uL + 0x25f1b6bafa949dffuL, out, index * 8)
    }
    return out
}

// ------------------------------------------------------------ the three answers

internal fun builder642f60Out0SourceWords(sixthOutput: ByteArray, contextSource: ByteArray, sp250Words: UIntArray): UIntArray {
    requireSource(sixthOutput, "642f60 sixth answer")
    requireSource(contextSource, "642f60 context source", 0x208)
    requireVector(sp250Words)
    val tables = Libre3FirstPairTables.get()
    val baseWords = u32WordsFromTableSegments(
        listOf(
            TableSegment(builder642f60Out0StaticQ0, 16), TableSegment(builder642f60Out0StaticQ1, 16),
            TableSegment(builder642f60Out0StaticQ0, 16), TableSegment(builder642f60Out0StaticQ1, 16),
            TableSegment(builder642f60Out0StaticQ0, 16), TableSegment(builder642f60Out0StaticD1, 8),
        ),
        tables,
    )
    return UIntArray(builder63c278VectorWords) { index ->
        val tableOffset = (index * 4) and 0x1c
        val sixthWord = readUInt32LE(sixthOutput, index * 4)
        val contextWord = readUInt32LE(contextSource, 0x1b0 + index * 4)
        var word = baseWords[index] + sixthWord * u32TableWord63c278(builder642f60Out0SixthMulTable + tableOffset, tables)
        word += contextWord * u32TableWord63c278(builder642f60Out0ContextMulTable + tableOffset, tables)
        val sp250Delta = sp250Words[index] * u32TableWord63c278(builder642f60Out0SP250MulTable + tableOffset, tables)
        word + sp250Delta + sp250Delta
    }
}

internal fun builder642f60Out0WordsFromSource(sourceWords: UIntArray): UIntArray {
    requireVector(sourceWords)
    val tables = Libre3FirstPairTables.get()
    var state = 0xb326b224u
    val out = UIntArray(builder63c278VectorWords)
    for (index in sourceWords.indices) {
        val tableOffset = (index * 4) and 0x1c
        state = state * 0x3d98bc67u + sourceWords[index]
        var folded7 = state * 0xe98a6e39u + 0xa9ce435cu
        folded7 = fold32ByNibbles63c278(folded7, builder642f60Out0FoldTable, 7, tables)
        val side = state * 0x88625dcfu + folded7 * 0x90000000u + 0x647eea94u
        val folded8 = fold32ByNibbles63c278(folded7, builder642f60Out0FoldTable, 1, tables)
        out[index] = u32TableAffine63c278(
            side, builder642f60Out0OutMulTable + tableOffset, builder642f60Out0OutAddTable + tableOffset, tables,
        )
        state = folded7 * 0x50717a0fu + folded8 * 0xf8e85f10u + 0x119b9786u
    }
    return out
}

internal fun builder642f60SeventhSourceWords(sp250Words: UIntArray, contextSource: ByteArray, out0Words: UIntArray): UIntArray {
    requireVector(sp250Words)
    requireSource(contextSource, "642f60 context source", 0x260)
    requireVector(out0Words)
    val tables = Libre3FirstPairTables.get()
    val baseWords = u32WordsFromTableSegments(
        listOf(
            TableSegment(builder642f60SeventhStaticQ0, 16), TableSegment(builder642f60SeventhStaticQ1, 16),
            TableSegment(builder642f60SeventhStaticQ0, 16), TableSegment(builder642f60SeventhStaticQ1, 16),
            TableSegment(builder642f60SeventhStaticQ0, 16), TableSegment(builder642f60SeventhStaticD1, 8),
        ),
        tables,
    )
    return UIntArray(builder63c278VectorWords) { index ->
        val tableOffset = (index * 4) and 0x1c
        val contextWord = readUInt32LE(contextSource, 0x208 + index * 4)
        baseWords[index] +
            sp250Words[index] * u32TableWord63c278(builder642f60SeventhSP250MulTable + tableOffset, tables) +
            contextWord * u32TableWord63c278(builder642f60SeventhContextMulTable + tableOffset, tables) +
            out0Words[index] * u32TableWord63c278(builder642f60SeventhOut0MulTable + tableOffset, tables)
    }
}

internal fun builder642f60SeventhStageSP148WordsFromSource(sourceWords: UIntArray): UIntArray {
    requireVector(sourceWords)
    val tables = Libre3FirstPairTables.get()
    var state = 0xf92a7de1u
    val out = UIntArray(builder63c278VectorWords)
    for (index in sourceWords.indices) {
        val tableOffset = (index * 4) and 0x1c
        state = state * 0x2bd72421u + sourceWords[index]
        var folded7 = state * 0x79766d05u + 0x22dc5eefu
        folded7 = fold32ByNibbles63c278(folded7, builder642f60SeventhSP148FoldTable, 7, tables)
        val side = state * 0x072b272du + folded7 * 0x70000000u + 0x63742f4bu
        val folded8 = fold32ByNibbles63c278(folded7, builder642f60SeventhSP148FoldTable, 1, tables)
        out[index] = u32TableAffine63c278(
            side, builder642f60SeventhSP148OutMulTable + tableOffset, builder642f60SeventhSP148OutAddTable + tableOffset, tables,
        )
        state = folded7 * 0x04d53e2du + folded8 * 0xb2ac1d30u + 0x1cf006ebu
    }
    return out
}

/** Two word runs of the seventh round, with running totals that carry a leading zero. */
internal class Libre3SeventhStreams(
    val sp670Words: ULongArray,
    val spa90Prefix: ULongArray,
    val sp510Words: ULongArray,
    val sp880Prefix: ULongArray,
)

internal fun builder642f60SeventhStreams(sp1a0Words: UIntArray, sp148Words: UIntArray): Libre3SeventhStreams {
    requireVector(sp1a0Words)
    requireVector(sp148Words)
    val tables = Libre3FirstPairTables.get()
    val sp670Words = ULongArray(builder63c278VectorWords) { index ->
        u64StreamWordFromU32Affine(
            sp1a0Words[index], index, builder642f60SeventhAMulTable, builder642f60SeventhAAddTable,
            0xf36a661du, 0x55308919u, 0xce5ac3ad5b5dac97uL, 0x48dc073b21398a79uL, builder642f60SeventhAFoldTable,
            0xfa62b370c3eadc41uL, 0xf45f1f1900000000uL, 0x6fc778fe52193dd5uL, tables,
        )
    }
    val sp510Words = ULongArray(builder63c278VectorWords) { index ->
        u64StreamWordFromU32Affine(
            sp148Words[index], index, builder642f60SeventhBMulTable, builder642f60SeventhBAddTable,
            0x84c35e4fu, 0xea2fdd20u, 0x1f41e4ec093ed9f7uL, 0x27d1733855de4d16uL, builder642f60SeventhBFoldTable,
            0x9fac6b22392e3497uL, 0x09482d9f00000000uL, 0xf6042c7612dc729euL, tables,
        )
    }
    return Libre3SeventhStreams(
        sp670Words, ulongArrayOf(0uL) + prefixSumsU64(sp670Words),
        sp510Words, ulongArrayOf(0uL) + prefixSumsU64(sp510Words),
    )
}

internal fun builder642f60SeventhSP9E0Words(streams: Libre3SeventhStreams): UIntArray {
    requireVector64(streams.sp670Words)
    requireVector64(streams.spa90Prefix, builder63c278VectorWords + 1)
    requireVector64(streams.sp510Words)
    requireVector64(streams.sp880Prefix, builder63c278VectorWords + 1)
    val tables = Libre3FirstPairTables.get()
    var state = 0x8360a2c993f75737uL
    val out = UIntArray(builder64bd0cWorkspaceWords)

    for (index in 0 until builder64bd0cWorkspaceWords) {
        val start = maxOf(0, index - (builder63c278VectorWords - 1))
        val end = minOf(index, builder63c278VectorWords - 1)
        val mixed: ULong = if (start <= end) {
            var productSum = 0uL
            for (position in start..end) {
                productSum += streams.sp670Words[position] * streams.sp510Words[index - position]
            }
            val span = (end - start + 1).toULong()
            val sumA = streams.spa90Prefix[end + 1] - streams.spa90Prefix[start]
            val sumB = streams.sp880Prefix[index - start + 1] - streams.sp880Prefix[index - end]
            state + span * 0x005dbd39bbb74611uL + productSum * 0x376b7bf8523b310fuL +
                sumA * 0x05844a4f0ab6c52buL + sumB * 0xbcb254e552fa427duL
        } else {
            state
        }

        val folded7 = fold63c278(
            mixed * 0x8db6469e177ed14buL + 0x0980afdda9144775uL, builder642f60SeventhSP9E0FoldTable, 7, tables,
        )
        val folded8 = fold63c278(folded7, builder642f60SeventhSP9E0FoldTable, 1, tables)
        val folded16 = fold63c278(folded8, builder642f60SeventhSP9E0FoldTable, 8, tables)
        val side = mixed.toUInt() * 0xe15d12adu + folded7.toUInt() * 0x90000000u + 0x07600fb6u
        val tableOffset = (index * 4) and 0x1c
        out[index] = u32TableAffine63c278(
            side, builder642f60SeventhSP9E0OutMulTable + tableOffset, builder642f60SeventhSP9E0OutAddTable + tableOffset, tables,
        )
        state = folded7 * 0xfac2e2a1bcc53063uL + folded16 * 0x33acf9d000000000uL + 0x42e2c949e6b96dc1uL
    }
    return out
}

internal fun builder642f60SeventhSPA90WordsFromSP300(sp300Words: UIntArray): ULongArray {
    requireVector(sp300Words)
    val tables = Libre3FirstPairTables.get()
    return ULongArray(builder63c278VectorWords) { index ->
        u64StreamWordFromU32Affine(
            sp300Words[index], index, builder642f60SeventhSP300MulTable, builder642f60SeventhSP300AddTable,
            0x923b2603u, 0x0d7c3c6du, 0x461236e7241ea4afuL, 0xc0bb06ebd489d8f1uL, builder642f60SeventhSP300FoldTable,
            0x1925dd7dc803ae75uL, 0x6a8f4fe500000000uL, 0x52f0304276b65fdeuL, tables,
        )
    }
}

/** The seventh round walks its vector from both ends, like the middle stage does. */
internal fun builder642f60SeventhSP7D0WordsFromSPA90(spa90Words: ULongArray): UIntArray {
    requireVector64(spa90Words)
    val tables = Libre3FirstPairTables.get()
    var state = 0xd71b81e668a07680uL
    val out = UIntArray(builder64bd0cWorkspaceWords)

    for (index in 0 until builder64bd0cWorkspaceWords) {
        val start = maxOf(0, index - (builder63c278VectorWords - 1))
        val end = minOf(index, builder63c278VectorWords - 1)
        var pairAccumulator = 0xb616243568409e12uL
        var left = start
        var right = end

        if (end > start) {
            while (true) {
                val endWord = spa90Words[right]
                right -= 1
                val product = endWord * 0x3a825182ec92a9efuL + 0x975bbf5d33a0b7f4uL
                var mixedPair = endWord * 0x975bbf5d33a0b7f4uL + pairAccumulator
                val startWord = spa90Words[left]
                left += 1
                mixedPair = product * startWord + mixedPair
                pairAccumulator = mixedPair + 0x4657dd9b924a1870uL
                if (right <= left) break
            }
        }

        pairAccumulator *= 0xc3c1f54f3c2cd4a6uL
        val scaledState = state * 0x7f0a8f747ca98163uL
        val mixed: ULong = if (right == left) {
            val center = spa90Words[right]
            (center * 0x8b03bdcc8a740e7duL + 0x25af1839607d5838uL) * center +
                pairAccumulator + scaledState + 0xe804eb7226c5f391uL
        } else {
            pairAccumulator + scaledState + 0x0bea08ebd101a741uL
        }

        val product = mixed * 0x416e14010d9d6b21uL
        val firstFold = foldTableU64Word63c278(
            builder642f60SeventhSP7D0FoldTable + (product and 0x0fuL).toInt() * 8, tables,
        ) + ((product + 0xe4602986bf1f9a80uL) shr 4)
        val folded7 = fold63c278(firstFold, builder642f60SeventhSP7D0FoldTable, 6, tables)
        val folded8 = fold63c278(folded7, builder642f60SeventhSP7D0FoldTable, 1, tables)
        val side = mixed.toUInt() * 0x3e3dcae5u + folded7.toUInt() * 0xb0000000u + 0x8a63e3dcu
        val folded16 = fold63c278(folded8, builder642f60SeventhSP7D0FoldTable, 8, tables)
        val tableOffset = (index * 4) and 0x1c
        out[index] = u32TableAffine63c278(
            side, builder642f60SeventhSP7D0OutMulTable + tableOffset, builder642f60SeventhSP7D0OutAddTable + tableOffset, tables,
        )
        state = folded7 * 0x38c35e2d317591ebuL + folded16 * 0xe8a6e15000000000uL + 0x7cfc7c8b77dde511uL
    }
    return out
}

internal fun builder642f60SeventhSource44Words(
    sp9e0Words: UIntArray,
    contextSource: ByteArray,
    sp7d0Words: UIntArray,
): UIntArray {
    if (sp9e0Words.size != builder64bd0cWorkspaceWords || sp7d0Words.size != builder64bd0cWorkspaceWords) {
        throw Libre3CryptoException("the 642f60 seventh source needs $builder64bd0cWorkspaceWords words in each stream")
    }
    requireSource(contextSource, "642f60 context source", 0x418)
    val tables = Libre3FirstPairTables.get()
    return UIntArray(builder64bd0cWorkspaceWords) { index ->
        val tableOffset = (index * 4) and 0x1c
        val contextWord = readUInt32LE(contextSource, 0x368 + index * 4)
        val sp7d0Delta = sp7d0Words[index] * u32TableWord63c278(builder642f60SeventhSourceSP7D0MulTable + tableOffset, tables)
        u32TableWord63c278(builder642f60SeventhSourceStaticTable + tableOffset, tables) +
            sp9e0Words[index] * u32TableWord63c278(builder642f60SeventhSourceSP9E0MulTable + tableOffset, tables) +
            contextWord * u32TableWord63c278(builder642f60SeventhSourceContext368MulTable + tableOffset, tables) +
            sp7d0Delta + sp7d0Delta
    }
}

internal fun builder642f60SeventhSP40WordsFromSource44(sourceWords: UIntArray): UIntArray {
    if (sourceWords.size != builder64bd0cWorkspaceWords) {
        throw Libre3CryptoException("the 642f60 seventh source must be $builder64bd0cWorkspaceWords words, not ${sourceWords.size}")
    }
    val tables = Libre3FirstPairTables.get()
    var state = 0xcfda05bau
    val out = UIntArray(builder64bd0cWorkspaceWords)
    for (index in sourceWords.indices) {
        val tableOffset = (index * 4) and 0x1c
        state = state * 0x0862c569u + sourceWords[index]
        var folded7 = state * 0x5e8a87f3u + 0x54d7c56fu
        folded7 = fold32ByNibbles63c278(folded7, builder642f60SeventhSP40FoldTable, 7, tables)
        val side = state * 0x12f83eedu + (folded7 shl 28) + 0x51f93a0au
        val folded8 = foldTableU32Word63c278(
            builder642f60SeventhSP40FoldTable + (folded7 and 0x0fu).toInt() * 4, tables,
        ) + (folded7 shr 4)
        out[index] = u32TableAffine63c278(
            side, builder642f60SeventhSP40OutMulTable + tableOffset, builder642f60SeventhSP40OutAddTable + tableOffset, tables,
        )
        state = folded7 * 0x36a73103u + folded8 * 0x958cefd0u + 0x9e56fff6u
    }
    return out
}

internal fun builder642f60Seventh64bd0cWorkspace(sp40Words: UIntArray): ByteArray {
    if (sp40Words.size != builder64bd0cWorkspaceWords) {
        throw Libre3CryptoException("the 642f60 sp40 words must be $builder64bd0cWorkspaceWords, not ${sp40Words.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val out = ByteArray(builder64bd0cWorkspaceBytes)
    for (index in 0 until builder64bd0cWorkspaceWords) {
        val word = u64StreamWordFromU32Affine(
            sp40Words[index], index, builder642f60SeventhWorkspaceMulTable, builder642f60SeventhWorkspaceAddTable,
            0x2e6bbea3u, 0xe3db739au, 0x40c95ec2845e4b0buL, 0xb5edeaa67030b38duL, builder642f60SeventhWorkspaceFoldTable,
            0xa2d77df3e3f51135uL, 0x7122434100000000uL, 0xb3aefd596d371f14uL, tables,
        )
        writeUInt64LE(word, out, index * 8)
    }
    return out
}

internal fun builder642f60Out1WordsFrom64bd0cOutput(output: ByteArray): UIntArray =
    builder642f60AffineWordsFrom64bd0cOutput(output, builder642f60Out1MulTable, builder642f60Out1AddTable, "642f60 answer one")

internal fun builder642f60EighthStreams(sp2a8Words: UIntArray, x2Source: ByteArray): Libre3PairedStreams {
    requireVector(sp2a8Words)
    requireSource(x2Source, "642f60 x2 source")
    val tables = Libre3FirstPairTables.get()
    val x2Words = UIntArray(builder63c278VectorWords) { readUInt32LE(x2Source, it * 4) }
    val spa90Words = ULongArray(builder63c278VectorWords) { builder642f60EighthAWord(sp2a8Words[it], it, tables) }
    val sp880Words = ULongArray(builder63c278VectorWords) { builder642f60EighthBWord(x2Words[it], it, tables) }
    return Libre3PairedStreams(spa90Words, prefixSumsU64(spa90Words), sp880Words, prefixSumsU64(sp880Words))
}

internal fun builder642f60Eighth64bd0cWorkspace(streams: Libre3PairedStreams): ByteArray {
    requireVector64(streams.spa90Words)
    requireVector64(streams.sp670Prefix)
    requireVector64(streams.sp880Words)
    requireVector64(streams.sp510Prefix)

    val out = ByteArray(builder64bd0cWorkspaceBytes)
    for (index in 0 until builder64bd0cWorkspaceWords) {
        val start = maxOf(0, index - (builder63c278VectorWords - 1))
        val end = minOf(index, builder63c278VectorWords - 1)
        val mixed: ULong = if (start <= end) {
            var productSum = 0uL
            for (position in start..end) {
                productSum += streams.spa90Words[position] * streams.sp880Words[index - position]
            }
            val span = (end - start + 1).toULong()
            val sumA = rangeSumFromPrefix(streams.sp670Prefix, start, end)
            val sumB = rangeSumFromPrefix(streams.sp510Prefix, index - end, index - start)
            span * 0x05f89c998f88e9a2uL + 0xe3449c12b03ff8d9uL + productSum * 0xeab93afc6984b71duL +
                sumA * 0xc2592d51a5992a23uL + sumB * 0xf8e4c71d4c7a89deuL
        } else {
            0xe3449c12b03ff8d9uL
        }
        writeUInt64LE(mixed * 0xca274927c26656e9uL + 0x89706c698c29e887uL, out, index * 8)
    }
    return out
}

internal fun builder642f60Out2WordsFrom64bd0cOutput(output: ByteArray): UIntArray =
    builder642f60AffineWordsFrom64bd0cOutput(output, builder642f60Out2MulTable, builder642f60Out2AddTable, "642f60 answer two")

/** The whole `642f60` builder. */
@Suppress("LongMethod")
internal fun builder642f60Outputs(in0: ByteArray, in1: ByteArray, in2: ByteArray, contextSource: ByteArray): Builder642f60Result {
    requireSource(in0, "642f60 in0")
    requireSource(in1, "642f60 in1")
    requireSource(in2, "642f60 in2")
    requireSource(contextSource, "642f60 context source", 0x420)

    val arg0 = contextSource.copyOfRange(0x100, 0x158)
    val scalar = readUInt64LE(contextSource, 0x418)

    val sp2a8Words = builder642f60StageSP2A8WordsFromX1(in1)
    val firstOutput = packUInt32LE(
        builder64bd0cOutputWords(arg0, scalar, builder642f60First64bd0cWorkspaceFromX1(in1, sp2a8Words))
    )
    val sp300Words = builder642f60StageSP300WordsFrom64bd0cOutput(firstOutput)

    val sp1f8Words = builder642f60StageSP1F8WordsFromX0(in0)
    val secondOutput = packUInt32LE(
        builder64bd0cOutputWords(arg0, scalar, builder642f60Second64bd0cWorkspace(sp1f8Words, sp300Words))
    )
    val sp250Words = builder642f60StageSP250WordsFrom64bd0cOutput(secondOutput)

    val thirdOutput = packUInt32LE(
        builder64bd0cOutputWords(arg0, scalar, builder642f60Third64bd0cWorkspaceFromX2(in2))
    )
    val sp148Words = builder642f60StageSP148WordsFrom64bd0cOutput(thirdOutput)

    val fourthOutput = packUInt32LE(
        builder64bd0cOutputWords(arg0, scalar, builder642f60Fourth64bd0cWorkspace(sp148Words))
    )
    val spf0Words = builder642f60StageSPF0WordsFrom64bd0cOutput(fourthOutput)

    val midSPA90Words = builder642f60MidStageSPA90WordsFromX0(in0)
    val midSP40Words = builder642f60MidStageSP40WordsFromSPA90(midSPA90Words)
    val midStreams = builder642f60MidStageStreamsFromContextSPF0(contextSource, spf0Words)
    val midSP670Words = builder642f60MidStageSP670Words(midStreams)
    val midSP40B = builder642f60MidStageSPA90SP880FromSP40(midSP40Words)
    val midStatic = builder642f60MidStageStaticSP9E0SP7D0(midSP40B.sideInit)
    val midSP510Words = builder642f60MidStageSP510Words(midSP40B, midStatic)

    val fifthOutput = packUInt32LE(
        builder64bd0cOutputWords(arg0, scalar, builder642f60MidFifth64bd0cWorkspace(midSP670Words, midSP510Words))
    )
    val sp1a0Words = builder642f60StageSP1A0WordsFrom64bd0cOutput(fifthOutput)

    val sixthStreams = builder642f60SixthStreamsFromSP1A0(sp1a0Words)
    val sixthOutput = packUInt32LE(
        builder64bd0cOutputWords(arg0, scalar, builder642f60Sixth64bd0cWorkspace(sixthStreams))
    )

    val out0SourceWords = builder642f60Out0SourceWords(sixthOutput, contextSource, sp250Words)
    val out0Words = builder642f60Out0WordsFromSource(out0SourceWords)

    val seventhSourceWords = builder642f60SeventhSourceWords(sp250Words, contextSource, out0Words)
    val seventhSP148Words = builder642f60SeventhStageSP148WordsFromSource(seventhSourceWords)
    val seventhStreams = builder642f60SeventhStreams(sp1a0Words, seventhSP148Words)
    val seventhSP9E0Words = builder642f60SeventhSP9E0Words(seventhStreams)
    val seventhSPA90Words = builder642f60SeventhSPA90WordsFromSP300(sp300Words)
    val seventhSP7D0Words = builder642f60SeventhSP7D0WordsFromSPA90(seventhSPA90Words)
    val seventhSource44Words = builder642f60SeventhSource44Words(seventhSP9E0Words, contextSource, seventhSP7D0Words)
    val seventhSP40Words = builder642f60SeventhSP40WordsFromSource44(seventhSource44Words)
    val seventhOutput = packUInt32LE(
        builder64bd0cOutputWords(arg0, scalar, builder642f60Seventh64bd0cWorkspace(seventhSP40Words))
    )
    val out1Words = builder642f60Out1WordsFrom64bd0cOutput(seventhOutput)

    val eighthStreams = builder642f60EighthStreams(sp2a8Words, in2)
    val eighthOutput = packUInt32LE(
        builder64bd0cOutputWords(arg0, scalar, builder642f60Eighth64bd0cWorkspace(eighthStreams))
    )
    val out2Words = builder642f60Out2WordsFrom64bd0cOutput(eighthOutput)

    return Builder642f60Result(packUInt32LE(out0Words), packUInt32LE(out1Words), packUInt32LE(out2Words))
}

internal fun builder642f60OutputsFromBundledContext(in0: ByteArray, in1: ByteArray, in2: ByteArray): Builder642f60Result =
    builder642f60Outputs(in0, in1, in2, builder6388f0SharedContextFromBundle())
