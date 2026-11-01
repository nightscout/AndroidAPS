package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException

/*
 * The `6473d0` builder: three arguments, two preimages and a shared context in, five answers out.
 *
 * Ported from LibreCRKit `Crypto/FirstPairSourceSlice.swift` at pin `a86b92f`.
 *
 * It runs ten rounds of the `64c524` engine. The rounds are numbered as in the original, and every
 * round reads answers of earlier ones, so their order may not be changed.
 */

/** The eight values of one `6473d0` call. */
internal class Builder6473d0Result(
    val in0After: ByteArray,
    val in1After: ByteArray,
    val in2After: ByteArray,
    val out0: ByteArray,
    val out1: ByteArray,
    val out2: ByteArray,
    val out3: ByteArray,
    val out4: ByteArray,
)

/** The five preimages that the caller keeps between rows. */
internal class Builder6473d0OutputPreimages(
    val out4: ByteArray,
    val out3: ByteArray,
    val out2: ByteArray,
    val out1: ByteArray,
    val out0: ByteArray,
)

/** Two word runs of one round, with their running totals. */
internal class Libre3Streams6473d0(
    val aWords: ULongArray,
    val bWords: ULongArray,
    val aPrefix: ULongArray,
    val bPrefix: ULongArray,
)

private fun requireSource(source: ByteArray, label: String, needed: Int = builder63c278VectorBytes) {
    if (source.size < needed) {
        throw Libre3CryptoException("the $label must be at least $needed bytes, not ${source.size}")
    }
}

private fun requireWords(words: UIntArray, expected: Int = builder63c278VectorWords) {
    if (words.size != expected) {
        throw Libre3CryptoException("a 6473d0 stream must be $expected words, not ${words.size}")
    }
}

/** Twenty two words of an answer, each through one table driven affine step. */
private fun affineWords(output: ByteArray, mulTable: Int, addTable: Int, label: String): UIntArray {
    requireSource(output, label)
    val tables = Libre3FirstPairTables.get()
    return UIntArray(builder63c278VectorWords) {
        u32Affine63c278(readUInt32LE(output, it * 4), it, mulTable, addTable, tables)
    }
}

/** The shared shape of the small word reducers of this builder. */
@Suppress("LongParameterList")
internal fun reducerU32Words63c278(
    sourceWords: UIntArray,
    stateInit: UInt,
    stateMul: UInt,
    foldPreMul: UInt,
    foldPreAdd: UInt,
    foldTable: Int,
    sideMul: UInt,
    sideFoldedMul: UInt,
    sideAdd: UInt,
    nextFolded7Mul: UInt,
    nextFolded8Mul: UInt,
    nextAdd: UInt,
    outMulTable: Int,
    outAddTable: Int,
): UIntArray {
    val tables = Libre3FirstPairTables.get()
    var state = stateInit
    val out = UIntArray(sourceWords.size)
    for (index in sourceWords.indices) {
        val tableOffset = (index * 4) and 0x1c
        state = state * stateMul + sourceWords[index]
        var folded7 = state * foldPreMul + foldPreAdd
        folded7 = fold32ByNibbles63c278(folded7, foldTable, 7, tables)
        val side = state * sideMul + folded7 * sideFoldedMul + sideAdd
        val folded8 = fold32ByNibbles63c278(folded7, foldTable, 1, tables)
        out[index] = u32TableAffine63c278(side, outMulTable + tableOffset, outAddTable + tableOffset, tables)
        state = folded7 * nextFolded7Mul + folded8 * nextFolded8Mul + nextAdd
    }
    return out
}

/** The shared shape of the two convolution reducers of the ninth round. */
@Suppress("LongParameterList")
internal fun convolutionReducerU32Words63c278(
    aWords: ULongArray,
    bWords: ULongArray,
    stateInit: ULong,
    countMul: ULong,
    productMul: ULong,
    sumAMul: ULong,
    sumBMul: ULong,
    foldPreMul: ULong,
    foldPreAdd: ULong,
    foldTable: Int,
    sideMul: UInt,
    sideFoldedMul: UInt,
    sideAdd: UInt,
    nextFolded8Mul: ULong,
    nextFolded16Mul: ULong,
    nextAdd: ULong,
    outMulTable: Int,
    outAddTable: Int,
): UIntArray {
    if (aWords.size != builder63c278VectorWords || bWords.size != builder63c278VectorWords) {
        throw Libre3CryptoException("a 6473d0 convolution needs $builder63c278VectorWords long words in each stream")
    }
    val tables = Libre3FirstPairTables.get()
    val aPrefix = prefixSumsU64(aWords)
    val bPrefix = prefixSumsU64(bWords)
    var state = stateInit
    val out = UIntArray(builder64bd0cWorkspaceWords)

    for (index in 0 until builder64bd0cWorkspaceWords) {
        val start = maxOf(0, index - (builder63c278VectorWords - 1))
        val end = minOf(index, builder63c278VectorWords - 1)
        var productSum = 0uL
        if (start <= end) {
            for (position in start..end) {
                productSum += aWords[position] * bWords[index - position]
            }
        }
        val span = if (start <= end) (end - start + 1).toULong() else 0uL
        val sumA = rangeSumFromPrefix(aPrefix, start, end)
        val sumB = rangeSumFromPrefix(bPrefix, index - end, index - start)
        val mixed = state + span * countMul + productSum * productMul + sumA * sumAMul + sumB * sumBMul
        val foldedSeed = mixed * foldPreMul + foldPreAdd
        val folded7 = fold63c278(foldedSeed, foldTable, 7, tables)
        val folded16 = fold63c278(foldedSeed, foldTable, 16, tables)
        val side = mixed.toUInt() * sideMul + folded7.toUInt() * sideFoldedMul + sideAdd
        val tableOffset = (index * 4) and 0x1c
        out[index] = u32TableAffine63c278(side, outMulTable + tableOffset, outAddTable + tableOffset, tables)
        state = folded7 * nextFolded8Mul + folded16 * nextFolded16Mul + nextAdd
    }
    return out
}

// ------------------------------------------------------------ rounds one to four

internal fun builder6473d0FirstStreamsFromIn2(in2: ByteArray): Libre3Streams6473d0 {
    requireSource(in2, "6473d0 in2 source")
    val tables = Libre3FirstPairTables.get()
    val aWords = ULongArray(builder63c278VectorWords) { builder6473d0FirstAWord(readUInt32LE(in2, it * 4), it, tables) }
    val bWords = ULongArray(builder63c278VectorWords) { builder6473d0FirstBWord(readUInt32LE(in2, it * 4), it, tables) }
    return Libre3Streams6473d0(aWords, bWords, prefixSumsU64(aWords), prefixSumsU64(bWords))
}

internal fun builder6473d0First64c524Workspace(in2: ByteArray): ByteArray {
    val streams = builder6473d0FirstStreamsFromIn2(in2)
    return convolutionWorkspaceU64(
        streams.aWords, streams.bWords,
        builder6473d0FirstConvBaseAdd, builder6473d0FirstConvCountMul, builder6473d0FirstConvProductMul,
        builder6473d0FirstConvSumAMul, builder6473d0FirstConvSumBMul,
        builder6473d0FirstConvFinalMul, builder6473d0FirstConvFinalAdd,
    )
}

internal fun builder6473d0SP488WordsFrom64c524Output(output: ByteArray): UIntArray =
    affineWords(output, builder6473d0SP488MulTable, builder6473d0SP488AddTable, "6473d0 64c524 answer")

internal fun builder6473d0SecondStreams(out0Seed: ByteArray, sp488Words: UIntArray): Libre3Streams6473d0 {
    requireSource(out0Seed, "6473d0 out0 seed")
    requireWords(sp488Words)
    val tables = Libre3FirstPairTables.get()
    val aWords = ULongArray(builder63c278VectorWords) { builder6473d0SecondAWord(readUInt32LE(out0Seed, it * 4), it, tables) }
    val bWords = ULongArray(builder63c278VectorWords) { builder6473d0SecondBWord(sp488Words[it], it, tables) }
    return Libre3Streams6473d0(aWords, bWords, prefixSumsU64(aWords), prefixSumsU64(bWords))
}

internal fun builder6473d0Second64c524Workspace(out0Seed: ByteArray, sp488Words: UIntArray): ByteArray {
    val streams = builder6473d0SecondStreams(out0Seed, sp488Words)
    return convolutionWorkspaceU64(
        streams.aWords, streams.bWords,
        builder6473d0SecondConvBaseAdd, builder6473d0SecondConvCountMul, builder6473d0SecondConvProductMul,
        builder6473d0SecondConvSumAMul, builder6473d0SecondConvSumBMul,
        builder6473d0SecondConvFinalMul, builder6473d0SecondConvFinalAdd,
    )
}

internal fun builder6473d0ThirdSourceWords(secondOutput: ByteArray, contextSource: ByteArray, in0: ByteArray): UIntArray {
    requireSource(secondOutput, "6473d0 second answer")
    requireSource(contextSource, "6473d0 context source", 0x260)
    requireSource(in0, "6473d0 in0 source")
    val tables = Libre3FirstPairTables.get()
    val baseWords = u32WordsFromTableSegments(
        listOf(
            TableSegment(builder6473d0ThirdStaticQ0, 16), TableSegment(builder6473d0ThirdStaticQ1, 16),
            TableSegment(builder6473d0ThirdStaticQ0, 16), TableSegment(builder6473d0ThirdStaticQ1, 16),
            TableSegment(builder6473d0ThirdStaticQ0, 16), TableSegment(builder6473d0ThirdStaticD1, 8),
        ),
        tables,
    )
    return UIntArray(builder63c278VectorWords) { index ->
        val tableOffset = (index * 4) and 0x1c
        baseWords[index] +
            readUInt32LE(secondOutput, index * 4) * u32TableWord63c278(builder6473d0ThirdSecondMulTable + tableOffset, tables) +
            readUInt32LE(contextSource, 0x208 + index * 4) *
            u32TableWord63c278(builder6473d0ThirdContext208MulTable + tableOffset, tables) +
            readUInt32LE(in0, index * 4) * u32TableWord63c278(builder6473d0ThirdIn0MulTable + tableOffset, tables)
    }
}

/** Note the subtraction of the folded value. That is what the original does. */
internal fun builder6473d0ThirdSP430Words(sourceWords: UIntArray): UIntArray {
    requireWords(sourceWords)
    val tables = Libre3FirstPairTables.get()
    var state = builder6473d0ThirdSP430StateInit
    val out = UIntArray(builder63c278VectorWords)
    for (index in sourceWords.indices) {
        val tableOffset = (index * 4) and 0x1c
        state = state * builder6473d0ThirdSP430StateMul + sourceWords[index]
        var folded7 = state * builder6473d0ThirdSP430FoldPreMul + builder6473d0ThirdSP430FoldPreAdd
        folded7 = fold32ByNibbles63c278(folded7, builder6473d0ThirdSP430FoldTable, 7, tables)
        val side = state * builder6473d0ThirdSP430SideMul - (folded7 shl 28) + builder6473d0ThirdSP430SideAdd
        val folded8 = fold32ByNibbles63c278(folded7, builder6473d0ThirdSP430FoldTable, 1, tables)
        out[index] = u32TableAffine63c278(
            side, builder6473d0ThirdSP430OutMulTable + tableOffset, builder6473d0ThirdSP430OutAddTable + tableOffset, tables,
        )
        state = folded7 * builder6473d0ThirdSP430NextFolded7Mul +
            folded8 * builder6473d0ThirdSP430NextFolded8Mul +
            builder6473d0ThirdSP430NextAdd
    }
    return out
}

internal fun builder6473d0ThirdStreams(in2: ByteArray, sp488Words: UIntArray): Libre3Streams6473d0 {
    requireSource(in2, "6473d0 in2 source")
    requireWords(sp488Words)
    val tables = Libre3FirstPairTables.get()
    val aWords = ULongArray(builder63c278VectorWords) { builder6473d0ThirdAWord(readUInt32LE(in2, it * 4), it, tables) }
    val bWords = ULongArray(builder63c278VectorWords) { builder6473d0ThirdBWord(sp488Words[it], it, tables) }
    return Libre3Streams6473d0(aWords, bWords, prefixSumsU64(aWords), prefixSumsU64(bWords))
}

internal fun builder6473d0Third64c524Workspace(in2: ByteArray, sp488Words: UIntArray): ByteArray {
    val streams = builder6473d0ThirdStreams(in2, sp488Words)
    return convolutionWorkspaceU64(
        streams.aWords, streams.bWords,
        builder6473d0ThirdConvBaseAdd, builder6473d0ThirdConvCountMul, builder6473d0ThirdConvProductMul,
        builder6473d0ThirdConvSumAMul, builder6473d0ThirdConvSumBMul,
        builder6473d0ThirdConvFinalMul, builder6473d0ThirdConvFinalAdd,
    )
}

internal fun builder6473d0FourthStreams(out1Seed: ByteArray, thirdOutput: ByteArray): Libre3Streams6473d0 {
    requireSource(out1Seed, "6473d0 out1 seed")
    requireSource(thirdOutput, "6473d0 third answer")
    val tables = Libre3FirstPairTables.get()
    val aWords = ULongArray(builder63c278VectorWords) { builder6473d0FourthAWord(readUInt32LE(out1Seed, it * 4), it, tables) }
    val bWords = ULongArray(builder63c278VectorWords) { builder6473d0FourthBWord(readUInt32LE(thirdOutput, it * 4), it, tables) }
    return Libre3Streams6473d0(aWords, bWords, prefixSumsU64(aWords), prefixSumsU64(bWords))
}

internal fun builder6473d0Fourth64c524Workspace(out1Seed: ByteArray, thirdOutput: ByteArray): ByteArray {
    val streams = builder6473d0FourthStreams(out1Seed, thirdOutput)
    return convolutionWorkspaceU64(
        streams.aWords, streams.bWords,
        builder6473d0FourthConvBaseAdd, builder6473d0FourthConvCountMul, builder6473d0FourthConvProductMul,
        builder6473d0FourthConvSumAMul, builder6473d0FourthConvSumBMul,
        builder6473d0FourthConvFinalMul, builder6473d0FourthConvFinalAdd,
    )
}

// ------------------------------------------------------------ rounds five to eight

internal fun builder6473d0FifthSourceWords(fourthOutput: ByteArray, contextSource: ByteArray, in1: ByteArray): UIntArray {
    requireSource(fourthOutput, "6473d0 fourth answer")
    requireSource(contextSource, "6473d0 context source", 0x1b0)
    requireSource(in1, "6473d0 in1 source")
    val tables = Libre3FirstPairTables.get()
    val baseWords = u32WordsFromTableSegments(
        listOf(
            TableSegment(builder6473d0FifthStaticQ0, 16), TableSegment(builder6473d0FifthStaticQ1, 16),
            TableSegment(builder6473d0FifthStaticQ0, 16), TableSegment(builder6473d0FifthStaticQ1, 16),
            TableSegment(builder6473d0FifthStaticQ0, 16), TableSegment(builder6473d0FifthStaticD1, 8),
        ),
        tables,
    )
    return UIntArray(builder63c278VectorWords) { index ->
        val tableOffset = (index * 4) and 0x1c
        baseWords[index] +
            readUInt32LE(fourthOutput, index * 4) * u32TableWord63c278(builder6473d0FifthFourthMulTable + tableOffset, tables) +
            readUInt32LE(contextSource, 0x158 + index * 4) *
            u32TableWord63c278(builder6473d0FifthContext158MulTable + tableOffset, tables) +
            readUInt32LE(in1, index * 4) * u32TableWord63c278(builder6473d0FifthIn1MulTable + tableOffset, tables)
    }
}

internal fun builder6473d0FifthSP3D8Words(sourceWords: UIntArray): UIntArray {
    requireWords(sourceWords)
    return reducerU32Words63c278(
        sourceWords,
        builder6473d0FifthSP3D8StateInit, builder6473d0FifthSP3D8StateMul,
        builder6473d0FifthSP3D8FoldPreMul, builder6473d0FifthSP3D8FoldPreAdd, builder6473d0FifthSP3D8FoldTable,
        builder6473d0FifthSP3D8SideMul, builder6473d0FifthSP3D8SideFoldedMul, builder6473d0FifthSP3D8SideAdd,
        builder6473d0FifthSP3D8NextFolded7Mul, builder6473d0FifthSP3D8NextFolded8Mul, builder6473d0FifthSP3D8NextAdd,
        builder6473d0FifthSP3D8OutMulTable, builder6473d0FifthSP3D8OutAddTable,
    )
}

internal fun builder6473d0FifthStreams(sp430Words: UIntArray): Libre3Streams6473d0 {
    requireWords(sp430Words)
    val tables = Libre3FirstPairTables.get()
    val aWords = ULongArray(builder63c278VectorWords) { builder6473d0FifthAWord(sp430Words[it], it, tables) }
    val bWords = ULongArray(builder63c278VectorWords) { builder6473d0FifthBWord(sp430Words[it], it, tables) }
    return Libre3Streams6473d0(aWords, bWords, prefixSumsU64(aWords), prefixSumsU64(bWords))
}

internal fun builder6473d0Fifth64c524Workspace(sp430Words: UIntArray): ByteArray {
    val streams = builder6473d0FifthStreams(sp430Words)
    return convolutionWorkspaceU64(
        streams.aWords, streams.bWords,
        builder6473d0FifthConvBaseAdd, builder6473d0FifthConvCountMul, builder6473d0FifthConvProductMul,
        builder6473d0FifthConvSumAMul, builder6473d0FifthConvSumBMul,
        builder6473d0FifthConvFinalMul, builder6473d0FifthConvFinalAdd,
    )
}

internal fun builder6473d0SixthSP380Words(fifthOutput: ByteArray): UIntArray =
    affineWords(fifthOutput, builder6473d0SixthSP380MulTable, builder6473d0SixthSP380AddTable, "6473d0 fifth answer")

internal fun builder6473d0SixthStreams(sp430Words: UIntArray, sp380Words: UIntArray): Libre3Streams6473d0 {
    requireWords(sp430Words)
    requireWords(sp380Words)
    val tables = Libre3FirstPairTables.get()
    val aWords = ULongArray(builder63c278VectorWords) { builder6473d0SixthSP750Word(sp380Words[it], it, tables) }
    val bWords = ULongArray(builder63c278VectorWords) { builder6473d0SixthSP698Word(sp430Words[it], it, tables) }
    return Libre3Streams6473d0(aWords, bWords, prefixSumsU64(aWords), prefixSumsU64(bWords))
}

internal fun builder6473d0Sixth64c524Workspace(sp430Words: UIntArray, sp380Words: UIntArray): ByteArray {
    val streams = builder6473d0SixthStreams(sp430Words, sp380Words)
    return convolutionWorkspaceU64(
        streams.aWords, streams.bWords,
        builder6473d0SixthConvBaseAdd, builder6473d0SixthConvCountMul, builder6473d0SixthConvProductMul,
        builder6473d0SixthConvSumAMul, builder6473d0SixthConvSumBMul,
        builder6473d0SixthConvFinalMul, builder6473d0SixthConvFinalAdd,
    )
}

internal fun builder6473d0SeventhSP328Words(sixthOutput: ByteArray): UIntArray =
    affineWords(sixthOutput, builder6473d0SeventhSP328MulTable, builder6473d0SeventhSP328AddTable, "6473d0 sixth answer")

internal fun builder6473d0SeventhStreams(in0: ByteArray, sp380Words: UIntArray): Libre3Streams6473d0 {
    requireSource(in0, "6473d0 in0")
    requireWords(sp380Words)
    val tables = Libre3FirstPairTables.get()
    val aWords = ULongArray(builder63c278VectorWords) { builder6473d0SeventhSP750Word(readUInt32LE(in0, it * 4), it, tables) }
    val bWords = ULongArray(builder63c278VectorWords) { builder6473d0SeventhSP698Word(sp380Words[it], it, tables) }
    return Libre3Streams6473d0(aWords, bWords, prefixSumsU64(aWords), prefixSumsU64(bWords))
}

internal fun builder6473d0Seventh64c524Workspace(in0: ByteArray, sp380Words: UIntArray): ByteArray {
    val streams = builder6473d0SeventhStreams(in0, sp380Words)
    return convolutionWorkspaceU64(
        streams.aWords, streams.bWords,
        builder6473d0SeventhConvBaseAdd, builder6473d0SeventhConvCountMul, builder6473d0SeventhConvProductMul,
        builder6473d0SeventhConvSumAMul, builder6473d0SeventhConvSumBMul,
        builder6473d0SeventhConvFinalMul, builder6473d0SeventhConvFinalAdd,
    )
}

internal fun builder6473d0EighthSP2D0Words(seventhOutput: ByteArray): UIntArray =
    affineWords(seventhOutput, builder6473d0EighthSP2D0MulTable, builder6473d0EighthSP2D0AddTable, "6473d0 seventh answer")

internal fun builder6473d0EighthStreams(sp3d8Words: UIntArray): Libre3Streams6473d0 {
    requireWords(sp3d8Words)
    val tables = Libre3FirstPairTables.get()
    val aWords = ULongArray(builder63c278VectorWords) { builder6473d0EighthAWord(sp3d8Words[it], it, tables) }
    val bWords = ULongArray(builder63c278VectorWords) { builder6473d0EighthBWord(sp3d8Words[it], it, tables) }
    return Libre3Streams6473d0(aWords, bWords, prefixSumsU64(aWords), prefixSumsU64(bWords))
}

internal fun builder6473d0Eighth64c524Workspace(sp3d8Words: UIntArray): ByteArray {
    val streams = builder6473d0EighthStreams(sp3d8Words)
    return convolutionWorkspaceU64(
        streams.aWords, streams.bWords,
        builder6473d0EighthConvBaseAdd, builder6473d0EighthConvCountMul, builder6473d0EighthConvProductMul,
        builder6473d0EighthConvSumAMul, builder6473d0EighthConvSumBMul,
        builder6473d0EighthConvFinalMul, builder6473d0EighthConvFinalAdd,
    )
}

// ------------------------------------------------------------ the ninth and tenth rounds

internal fun builder6473d0NinthFirstSourceWords(
    eighthOutput: ByteArray,
    contextSource: ByteArray,
    sp328Words: UIntArray,
    sp2d0Words: UIntArray,
): UIntArray {
    requireSource(eighthOutput, "6473d0 eighth answer")
    requireSource(contextSource, "6473d0 context source", 0x260)
    requireWords(sp328Words)
    requireWords(sp2d0Words)
    val tables = Libre3FirstPairTables.get()
    val baseWords = u32WordsFromTableSegments(
        listOf(
            TableSegment(builder6473d0Ninth1StaticQ0, 16), TableSegment(builder6473d0Ninth1StaticQ1, 16),
            TableSegment(builder6473d0Ninth1StaticQ0, 16), TableSegment(builder6473d0Ninth1StaticQ1, 16),
            TableSegment(builder6473d0Ninth1StaticQ0, 16), TableSegment(builder6473d0Ninth1StaticD1, 8),
        ),
        tables,
    )
    return UIntArray(builder63c278VectorWords) { index ->
        val tableOffset = (index * 4) and 0x1c
        val sp2d0Delta = sp2d0Words[index] * u32TableWord63c278(builder6473d0Ninth1SP2D0MulTable + tableOffset, tables)
        baseWords[index] +
            readUInt32LE(eighthOutput, index * 4) * u32TableWord63c278(builder6473d0Ninth1EighthMulTable + tableOffset, tables) +
            readUInt32LE(contextSource, 0x208 + index * 4) *
            u32TableWord63c278(builder6473d0Ninth1Context208MulTable + tableOffset, tables) +
            sp328Words[index] * u32TableWord63c278(builder6473d0Ninth1SP328MulTable + tableOffset, tables) +
            sp2d0Delta + sp2d0Delta
    }
}

internal fun builder6473d0NinthOut2Words(sourceWords: UIntArray): UIntArray {
    requireWords(sourceWords)
    return reducerU32Words63c278(
        sourceWords,
        builder6473d0Ninth1Out3StateInit, builder6473d0Ninth1Out3StateMul,
        builder6473d0Ninth1Out3FoldPreMul, builder6473d0Ninth1Out3FoldPreAdd, builder6473d0Ninth1Out3FoldTable,
        builder6473d0Ninth1Out3SideMul, builder6473d0Ninth1Out3SideFoldedMul, builder6473d0Ninth1Out3SideAdd,
        builder6473d0Ninth1Out3NextFolded7Mul, builder6473d0Ninth1Out3NextFolded8Mul, builder6473d0Ninth1Out3NextAdd,
        builder6473d0Ninth1Out3OutMulTable, builder6473d0Ninth1Out3OutAddTable,
    )
}

internal fun builder6473d0NinthSecondSourceWords(
    sp2d0Words: UIntArray,
    contextSource: ByteArray,
    out2Words: UIntArray,
): UIntArray {
    requireWords(sp2d0Words)
    requireSource(contextSource, "6473d0 context source", 0x2b8)
    requireWords(out2Words)
    val tables = Libre3FirstPairTables.get()
    val baseWords = u32WordsFromTableSegments(
        listOf(
            TableSegment(builder6473d0Ninth2StaticQ0, 16), TableSegment(builder6473d0Ninth2StaticQ1, 16),
            TableSegment(builder6473d0Ninth2StaticQ0, 16), TableSegment(builder6473d0Ninth2StaticQ1, 16),
            TableSegment(builder6473d0Ninth2StaticQ0, 16), TableSegment(builder6473d0Ninth2StaticD1, 8),
        ),
        tables,
    )
    return UIntArray(builder63c278VectorWords) { index ->
        val tableOffset = (index * 4) and 0x1c
        baseWords[index] +
            sp2d0Words[index] * u32TableWord63c278(builder6473d0Ninth2SP2D0MulTable + tableOffset, tables) +
            readUInt32LE(contextSource, 0x260 + index * 4) *
            u32TableWord63c278(builder6473d0Ninth2Context260MulTable + tableOffset, tables) +
            out2Words[index] * u32TableWord63c278(builder6473d0Ninth2Out2MulTable + tableOffset, tables)
    }
}

internal fun builder6473d0NinthSP278Words(sourceWords: UIntArray): UIntArray {
    requireWords(sourceWords)
    return reducerU32Words63c278(
        sourceWords,
        builder6473d0Ninth2SP278StateInit, builder6473d0Ninth2SP278StateMul,
        builder6473d0Ninth2SP278FoldPreMul, builder6473d0Ninth2SP278FoldPreAdd, builder6473d0Ninth2SP278FoldTable,
        builder6473d0Ninth2SP278SideMul, builder6473d0Ninth2SP278SideFoldedMul, builder6473d0Ninth2SP278SideAdd,
        builder6473d0Ninth2SP278NextFolded7Mul, builder6473d0Ninth2SP278NextFolded8Mul, builder6473d0Ninth2SP278NextAdd,
        builder6473d0Ninth2SP278OutMulTable, builder6473d0Ninth2SP278OutAddTable,
    )
}

internal fun builder6473d0NinthFirstStreams(sp3d8Words: UIntArray, sp278Words: UIntArray): Libre3Streams6473d0 {
    requireWords(sp3d8Words)
    requireWords(sp278Words)
    val tables = Libre3FirstPairTables.get()
    val aWords = ULongArray(builder63c278VectorWords) { index ->
        u64StreamWordFromU32Affine(
            sp3d8Words[index], index, builder6473d0NinthSP3D8AMulTable, builder6473d0NinthSP3D8AAddTable,
            builder6473d0NinthSP3D8AU32Mul, builder6473d0NinthSP3D8AU32Add,
            builder6473d0NinthSP3D8AFoldMul, builder6473d0NinthSP3D8AFoldAdd, builder6473d0NinthSP3D8AFoldTable,
            builder6473d0NinthSP3D8ALinearMul, builder6473d0NinthSP3D8AFoldedMul, builder6473d0NinthSP3D8ALinearAdd, tables,
        )
    }
    val bWords = ULongArray(builder63c278VectorWords) { index ->
        u64StreamWordFromU32Affine(
            sp278Words[index], index, builder6473d0NinthSP278BMulTable, builder6473d0NinthSP278BAddTable,
            builder6473d0NinthSP278BU32Mul, builder6473d0NinthSP278BU32Add,
            builder6473d0NinthSP278BFoldMul, builder6473d0NinthSP278BFoldAdd, builder6473d0NinthSP278BFoldTable,
            builder6473d0NinthSP278BLinearMul, builder6473d0NinthSP278BFoldedMul, builder6473d0NinthSP278BLinearAdd, tables,
        )
    }
    return Libre3Streams6473d0(aWords, bWords, shiftedPrefixSumsU64(aWords), shiftedPrefixSumsU64(bWords))
}

internal fun builder6473d0NinthSP1C8Words(aWords: ULongArray, bWords: ULongArray): UIntArray =
    convolutionReducerU32Words63c278(
        aWords, bWords,
        builder6473d0NinthConv1StateInit, builder6473d0NinthConv1CountMul, builder6473d0NinthConv1ProductMul,
        builder6473d0NinthConv1SumAMul, builder6473d0NinthConv1SumBMul,
        builder6473d0NinthConv1FoldPreMul, builder6473d0NinthConv1FoldPreAdd, builder6473d0NinthConv1FoldTable,
        builder6473d0NinthConv1SideMul, builder6473d0NinthConv1SideFoldedMul, builder6473d0NinthConv1SideAdd,
        builder6473d0NinthConv1NextFolded8Mul, builder6473d0NinthConv1NextFolded16Mul, builder6473d0NinthConv1NextAdd,
        builder6473d0NinthConv1OutMulTable, builder6473d0NinthConv1OutAddTable,
    )

internal fun builder6473d0NinthSecondStreams(in1: ByteArray, sp328Words: UIntArray): Libre3Streams6473d0 {
    requireSource(in1, "6473d0 in1 source")
    requireWords(sp328Words)
    val tables = Libre3FirstPairTables.get()
    val aWords = ULongArray(builder63c278VectorWords) { index ->
        u64StreamWordFromU32Affine(
            readUInt32LE(in1, index * 4), index, builder6473d0NinthIn1AMulTable, builder6473d0NinthIn1AAddTable,
            builder6473d0NinthIn1AU32Mul, builder6473d0NinthIn1AU32Add,
            builder6473d0NinthIn1AFoldMul, builder6473d0NinthIn1AFoldAdd, builder6473d0NinthIn1AFoldTable,
            builder6473d0NinthIn1ALinearMul, builder6473d0NinthIn1AFoldedMul, builder6473d0NinthIn1ALinearAdd, tables,
        )
    }
    val bWords = ULongArray(builder63c278VectorWords) { index ->
        u64StreamWordFromU32Affine(
            sp328Words[index], index, builder6473d0NinthSP328BMulTable, builder6473d0NinthSP328BAddTable,
            builder6473d0NinthSP328BU32Mul, builder6473d0NinthSP328BU32Add,
            builder6473d0NinthSP328BFoldMul, builder6473d0NinthSP328BFoldAdd, builder6473d0NinthSP328BFoldTable,
            builder6473d0NinthSP328BLinearMul, builder6473d0NinthSP328BFoldedMul, builder6473d0NinthSP328BLinearAdd, tables,
        )
    }
    return Libre3Streams6473d0(aWords, bWords, shiftedPrefixSumsU64(aWords), shiftedPrefixSumsU64(bWords))
}

internal fun builder6473d0NinthSP118Words(aWords: ULongArray, bWords: ULongArray): UIntArray =
    convolutionReducerU32Words63c278(
        aWords, bWords,
        builder6473d0NinthConv2StateInit, builder6473d0NinthConv2CountMul, builder6473d0NinthConv2ProductMul,
        builder6473d0NinthConv2SumAMul, builder6473d0NinthConv2SumBMul,
        builder6473d0NinthConv2FoldPreMul, builder6473d0NinthConv2FoldPreAdd, builder6473d0NinthConv2FoldTable,
        builder6473d0NinthConv2SideMul, builder6473d0NinthConv2SideFoldedMul, builder6473d0NinthConv2SideAdd,
        builder6473d0NinthConv2NextFolded8Mul, builder6473d0NinthConv2NextFolded16Mul, builder6473d0NinthConv2NextAdd,
        builder6473d0NinthConv2OutMulTable, builder6473d0NinthConv2OutAddTable,
    )

internal fun builder6473d0NinthThirdSourceWords(
    sp1c8Words: UIntArray,
    contextSource: ByteArray,
    sp118Words: UIntArray,
): UIntArray {
    requireWords(sp1c8Words, builder64bd0cWorkspaceWords)
    requireSource(contextSource, "6473d0 context source", 0x368)
    requireWords(sp118Words, builder64bd0cWorkspaceWords)
    val tables = Libre3FirstPairTables.get()
    return UIntArray(builder64bd0cWorkspaceWords) { index ->
        val tableOffset = (index * 4) and 0x1c
        u32TableWord63c278(builder6473d0Ninth3StaticTable + ((index and 7) * 4), tables) +
            sp1c8Words[index] * u32TableWord63c278(builder6473d0Ninth3SP1C8MulTable + tableOffset, tables) +
            readUInt32LE(contextSource, 0x2b8 + index * 4) *
            u32TableWord63c278(builder6473d0Ninth3Context2B8MulTable + tableOffset, tables) +
            sp118Words[index] * u32TableWord63c278(builder6473d0Ninth3SP118MulTable + tableOffset, tables)
    }
}

internal fun builder6473d0NinthSP68Words(sourceWords: UIntArray): UIntArray {
    requireWords(sourceWords, builder64bd0cWorkspaceWords)
    return reducerU32Words63c278(
        sourceWords,
        builder6473d0Ninth3SP68StateInit, builder6473d0Ninth3SP68StateMul,
        builder6473d0Ninth3SP68FoldPreMul, builder6473d0Ninth3SP68FoldPreAdd, builder6473d0Ninth3SP68FoldTable,
        builder6473d0Ninth3SP68SideMul, builder6473d0Ninth3SP68SideFoldedMul, builder6473d0Ninth3SP68SideAdd,
        builder6473d0Ninth3SP68NextFolded7Mul, builder6473d0Ninth3SP68NextFolded8Mul, builder6473d0Ninth3SP68NextAdd,
        builder6473d0Ninth3SP68OutMulTable, builder6473d0Ninth3SP68OutAddTable,
    )
}

internal fun builder6473d0Ninth64c524Workspace(sp68Words: UIntArray): ByteArray {
    requireWords(sp68Words, builder64bd0cWorkspaceWords)
    val tables = Libre3FirstPairTables.get()
    val words = ULongArray(builder64bd0cWorkspaceWords) { index ->
        u64StreamWordFromU32Affine(
            sp68Words[index], index, builder6473d0NinthWorkspaceMulTable, builder6473d0NinthWorkspaceAddTable,
            builder6473d0NinthWorkspaceU32Mul, builder6473d0NinthWorkspaceU32Add,
            builder6473d0NinthWorkspaceFoldMul, builder6473d0NinthWorkspaceFoldAdd, builder6473d0NinthWorkspaceFoldTable,
            builder6473d0NinthWorkspaceLinearMul, builder6473d0NinthWorkspaceFoldedMul, builder6473d0NinthWorkspaceLinearAdd,
            tables,
        )
    }
    return packUInt64LE64cd40(words)
}

internal fun builder6473d0TenthOut3Words(ninthOutput: ByteArray): UIntArray =
    affineWords(ninthOutput, builder6473d0TenthOut3MulTable, builder6473d0TenthOut3AddTable, "6473d0 ninth answer")

internal fun builder6473d0TenthStreams(in2: ByteArray, sp430Words: UIntArray): Libre3Streams6473d0 {
    requireSource(in2, "6473d0 in2 source")
    requireWords(sp430Words)
    val tables = Libre3FirstPairTables.get()
    val aWords = ULongArray(builder63c278VectorWords) { builder6473d0TenthAWord(readUInt32LE(in2, it * 4), it, tables) }
    val bWords = ULongArray(builder63c278VectorWords) { builder6473d0TenthBWord(sp430Words[it], it, tables) }
    return Libre3Streams6473d0(aWords, bWords, prefixSumsU64(aWords), prefixSumsU64(bWords))
}

internal fun builder6473d0Tenth64c524Workspace(in2: ByteArray, sp430Words: UIntArray): ByteArray {
    val streams = builder6473d0TenthStreams(in2, sp430Words)
    return convolutionWorkspaceU64(
        streams.aWords, streams.bWords,
        builder6473d0TenthConvBaseAdd, builder6473d0TenthConvCountMul, builder6473d0TenthConvProductMul,
        builder6473d0TenthConvSumAMul, builder6473d0TenthConvSumBMul,
        builder6473d0TenthConvFinalMul, builder6473d0TenthConvFinalAdd,
    )
}

internal fun builder6473d0FinalOut4Words(tenthOutput: ByteArray): UIntArray =
    affineWords(tenthOutput, builder6473d0FinalOut4MulTable, builder6473d0FinalOut4AddTable, "6473d0 tenth answer")

/** The whole `6473d0` builder. */
@Suppress("LongMethod")
internal fun builder6473d0Outputs(
    in0: ByteArray,
    in1: ByteArray,
    in2: ByteArray,
    contextSource: ByteArray,
    out0Preimage: ByteArray? = null,
    out1Preimage: ByteArray? = null,
): Builder6473d0Result {
    requireSource(in0, "6473d0 in0")
    requireSource(in1, "6473d0 in1")
    requireSource(in2, "6473d0 in2")
    requireSource(contextSource, "6473d0 context source", 0x420)

    fun resolvedPreimage(value: ByteArray?, label: String): ByteArray {
        if (value == null) return ByteArray(builder63c278VectorBytes)
        requireSource(value, label)
        return value.copyOfRange(0, builder63c278VectorBytes)
    }

    val arg0 = contextSource.copyOfRange(0x100, 0x158)
    val scalar = readUInt64LE(contextSource, 0x418)
    val out0Seed = resolvedPreimage(out0Preimage, "6473d0 out0 preimage")
    val out1Seed = resolvedPreimage(out1Preimage, "6473d0 out1 preimage")

    val firstOutput = packUInt32LE(builder64c524OutputWords(arg0, scalar, builder6473d0First64c524Workspace(in2)))
    val sp488Words = builder6473d0SP488WordsFrom64c524Output(firstOutput)

    val secondOutput = packUInt32LE(
        builder64c524OutputWords(arg0, scalar, builder6473d0Second64c524Workspace(out0Seed, sp488Words))
    )
    val thirdSourceWords = builder6473d0ThirdSourceWords(secondOutput, contextSource, in0)
    val thirdSP430Words = builder6473d0ThirdSP430Words(thirdSourceWords)

    val thirdOutput = packUInt32LE(
        builder64c524OutputWords(arg0, scalar, builder6473d0Third64c524Workspace(in2, sp488Words))
    )
    val fourthOutput = packUInt32LE(
        builder64c524OutputWords(arg0, scalar, builder6473d0Fourth64c524Workspace(out1Seed, thirdOutput))
    )
    val fifthSourceWords = builder6473d0FifthSourceWords(fourthOutput, contextSource, in1)
    val fifthSP3D8Words = builder6473d0FifthSP3D8Words(fifthSourceWords)

    val fifthOutput = packUInt32LE(
        builder64c524OutputWords(arg0, scalar, builder6473d0Fifth64c524Workspace(thirdSP430Words))
    )
    val sixthSP380Words = builder6473d0SixthSP380Words(fifthOutput)
    val sixthOutput = packUInt32LE(
        builder64c524OutputWords(arg0, scalar, builder6473d0Sixth64c524Workspace(thirdSP430Words, sixthSP380Words))
    )
    val seventhSP328Words = builder6473d0SeventhSP328Words(sixthOutput)
    val seventhOutput = packUInt32LE(
        builder64c524OutputWords(arg0, scalar, builder6473d0Seventh64c524Workspace(in0, sixthSP380Words))
    )
    val eighthSP2D0Words = builder6473d0EighthSP2D0Words(seventhOutput)
    val eighthOutput = packUInt32LE(
        builder64c524OutputWords(arg0, scalar, builder6473d0Eighth64c524Workspace(fifthSP3D8Words))
    )

    val out2Words = builder6473d0NinthOut2Words(
        builder6473d0NinthFirstSourceWords(eighthOutput, contextSource, seventhSP328Words, eighthSP2D0Words)
    )
    val ninthSP278Words = builder6473d0NinthSP278Words(
        builder6473d0NinthSecondSourceWords(eighthSP2D0Words, contextSource, out2Words)
    )
    val ninthFirstStreams = builder6473d0NinthFirstStreams(fifthSP3D8Words, ninthSP278Words)
    val ninthSP1C8Words = builder6473d0NinthSP1C8Words(ninthFirstStreams.aWords, ninthFirstStreams.bWords)
    val ninthSecondStreams = builder6473d0NinthSecondStreams(in1, seventhSP328Words)
    val ninthSP118Words = builder6473d0NinthSP118Words(ninthSecondStreams.aWords, ninthSecondStreams.bWords)
    val ninthSP68Words = builder6473d0NinthSP68Words(
        builder6473d0NinthThirdSourceWords(ninthSP1C8Words, contextSource, ninthSP118Words)
    )
    val ninthOutput = packUInt32LE(
        builder64c524OutputWords(arg0, scalar, builder6473d0Ninth64c524Workspace(ninthSP68Words))
    )
    val out3Words = builder6473d0TenthOut3Words(ninthOutput)
    val tenthOutput = packUInt32LE(
        builder64c524OutputWords(arg0, scalar, builder6473d0Tenth64c524Workspace(in2, thirdSP430Words))
    )
    val out4Words = builder6473d0FinalOut4Words(tenthOutput)

    return Builder6473d0Result(
        in0After = in0.copyOfRange(0, builder63c278VectorBytes),
        in1After = in1.copyOfRange(0, builder63c278VectorBytes),
        in2After = in2.copyOfRange(0, builder63c278VectorBytes),
        out0 = out0Seed,
        out1 = out1Seed,
        out2 = packUInt32LE(out2Words),
        out3 = packUInt32LE(out3Words),
        out4 = packUInt32LE(out4Words),
    )
}

internal fun builder6473d0OutputsFromBundledContext(
    in0: ByteArray,
    in1: ByteArray,
    in2: ByteArray,
    out0Preimage: ByteArray? = null,
    out1Preimage: ByteArray? = null,
): Builder6473d0Result = builder6473d0Outputs(
    in0, in1, in2, builder6388f0SharedContextFromBundle(), out0Preimage, out1Preimage,
)

/** The small stack the caller keeps between rows, built from the five preimages. */
internal fun builder6473d0MinimalStack20FromPreimages(preimages: Builder6473d0OutputPreimages): ByteArray {
    val stack20 = ByteArray(builder6473d0CallerStackPreimageBytes)
    val vectors = listOf(
        Triple(preimages.out4, 0x000, "out4"),
        Triple(preimages.out3, 0x058, "out3"),
        Triple(preimages.out2, 0x0b0, "out2"),
        Triple(preimages.out1, 0x210, "out1"),
        Triple(preimages.out0, 0x268, "out0"),
    )
    for ((raw, offset, name) in vectors) {
        requireSource(raw, "6473d0 $name preimage")
        replace(stack20, offset, raw.copyOfRange(0, builder63c278VectorBytes))
    }
    return stack20
}

/** Where each answer of one row is written in the caller's own stack. */
internal fun builder6473d0PostVectors(result: Builder6473d0Result): Map<Int, ByteArray> = mapOf(
    0x3708 to result.out4,
    0x3760 to result.out3,
    0x37b8 to result.out2,
    0x3810 to result.in2After,
    0x3868 to result.in1After,
    0x38c0 to result.in0After,
    0x3918 to result.out1,
    0x3970 to result.out0,
)
