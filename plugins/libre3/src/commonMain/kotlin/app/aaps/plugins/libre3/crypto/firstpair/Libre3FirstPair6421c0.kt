package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException

/*
 * The `6421c0` builder and the high seeds of the `6388f0` stream.
 *
 * Ported from LibreCRKit `Crypto/FirstPairSourceSlice.swift` at pin `a86b92f`.
 *
 * A point of the curve, in two seventy byte windows, becomes twenty two schedule words here.
 */

/** The two high seeds of one row. */
internal class Builder6388f0HighSeedStreamStartSeeds(val out0: ByteArray, val out1: ByteArray)

/** The high seeds of the two rows a first pairing needs. */
internal class Builder6388f0FirstPairHighSeedStreamStartSeeds(
    val row0: Builder6388f0HighSeedStreamStartSeeds,
    val row59: Builder6388f0HighSeedStreamStartSeeds,
)

/** A list of long words with its running totals. */
internal class Libre3Streams6421c0(val raw: ULongArray, val prefix: ULongArray)

/** The long words packed low byte first. */
internal fun packUInt64LE64cd40(words: ULongArray): ByteArray {
    val out = ByteArray(words.size * 8)
    for (index in words.indices) writeUInt64LE(words[index], out, index * 8)
    return out
}

/** The words packed low byte first. */
internal fun packUInt32LE(words: UIntArray): ByteArray {
    val out = ByteArray(words.size * 4)
    for (index in words.indices) writeUInt32LE(words[index], out, index * 4)
    return out
}

internal fun builder6421c0X0Streams(x0Source: ByteArray): Libre3Streams6421c0 {
    if (x0Source.size < 20 * 4) {
        throw Libre3CryptoException("the 6421c0 x0 source must be at least ${20 * 4} bytes, not ${x0Source.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val raw = ULongArray(20)
    for (index in 0 until 20) {
        val word = readUInt32LE(x0Source, index * 4)
        val mixed = if (index == 0) {
            word * 0x3239bd21u + 0x5c47f2f0u
        } else {
            u32Affine63c278(word, index, builder6421c0X0MulTable, builder6421c0X0AddTable, tables) *
                0x5da2e52fu + 0x6605175eu
        }
        val folded = fold63c278(
            mixed.toULong() * 0x430e55e51aa99355uL + 0x15551dd776f38e14uL,
            builder6421c0X0FoldTable, 8, tables,
        )
        raw[index] = mixed.toULong() * 0xc788d39836400f55uL + folded * 0xd50b73ff00000000uL + 0xce6055b08c097bf0uL
    }
    return Libre3Streams6421c0(raw, prefixSumsU64(raw))
}

internal fun builder6421c0X1Streams(x1Source: ByteArray): Libre3Streams6421c0 {
    if (x1Source.size < builder63c278VectorBytes) {
        throw Libre3CryptoException("the 6421c0 x1 source must be at least $builder63c278VectorBytes bytes, not ${x1Source.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val raw = ULongArray(builder63c278VectorWords)
    for (index in 0 until builder63c278VectorWords) {
        val word = readUInt32LE(x1Source, index * 4)
        val mixed = if (index == 0) {
            word * 0x105085d7u + 0x841874d8u
        } else {
            u32Affine63c278(word, index, builder6421c0X1MulTable, builder6421c0X1AddTable, tables) *
                0x97c9fb77u + 0x6b1b1a39u
        }
        val folded = fold63c278(
            mixed.toULong() * 0x8f1272d1ced32651uL + 0x7eda487fd3a46989uL,
            builder6421c0X1FoldTable, 8, tables,
        )
        raw[index] = mixed.toULong() * 0x9dcd2446c70edca3uL + folded * 0xe9148d4d00000000uL + 0x2df1f5e9fb0ab4f8uL
    }
    return Libre3Streams6421c0(raw, prefixSumsU64(raw))
}

internal fun builder6421c0X2Words(x2Source: ByteArray): ULongArray {
    if (x2Source.size < builder63c278VectorBytes) {
        throw Libre3CryptoException("the 6421c0 x2 source must be at least $builder63c278VectorBytes bytes, not ${x2Source.size}")
    }
    val tables = Libre3FirstPairTables.get()
    return ULongArray(builder63c278VectorWords) { index ->
        val affine = u32Affine63c278(
            readUInt32LE(x2Source, index * 4), index, builder6421c0X2MulTable, builder6421c0X2AddTable, tables,
        )
        val mixed = affine * 0x6819ef77u + 0x57cf46ceu
        val folded = fold63c278(
            mixed.toULong() * 0xc4e90084bd222fd1uL + 0xf9e4937efa15a0b7uL,
            builder6421c0X2FoldTable, 8, tables,
        )
        mixed.toULong() * 0x06447e0a39c79467uL + folded * 0xcfaf794900000000uL + 0xe882bfc48de82700uL
    }
}

internal fun builder6421c0ConvolutionWorkspace(x0: Libre3Streams6421c0, x1: Libre3Streams6421c0): ByteArray {
    if (x0.raw.size != 20 || x0.prefix.size != 20) {
        throw Libre3CryptoException("the 6421c0 x0 stream must be 20 words, not ${x0.raw.size}")
    }
    if (x1.raw.size != builder63c278VectorWords || x1.prefix.size != builder63c278VectorWords) {
        throw Libre3CryptoException("the 6421c0 x1 stream must be $builder63c278VectorWords words, not ${x1.raw.size}")
    }
    val out = ByteArray(builder64cd40WorkspaceBytes)
    for (index in 0 until builder64cd40WorkspaceWords) {
        val low = maxOf(0, index - (x1.raw.size - 1))
        val high = minOf(index, x0.raw.size - 1)
        var productSum = 0uL
        var x0Sum = 0uL
        var x1Sum = 0uL
        var count = 0
        if (high >= low) {
            for (x0Index in low..high) {
                productSum += x0.raw[x0Index] * x1.raw[index - x0Index]
            }
            x0Sum = rangeSumFromPrefix(x0.prefix, low, high)
            x1Sum = rangeSumFromPrefix(x1.prefix, index - high, index - low)
            count = high - low + 1
        }
        val mixed = count.toULong() * 0xdd9e6926c32c9984uL +
            0x7bf33cd7983bce3cuL +
            x0Sum * 0xe703af65ab19ca84uL +
            productSum * 0xe6337be2ad0561b9uL +
            x1Sum * 0x1cd6868a83aeef79uL
        writeUInt64LE(mixed * 0x2e60fd6d05fe470buL + 0xf48a714d4ddd3ee7uL, out, index * 8)
    }
    return out
}

internal fun builder6421c0WorkspaceAfterUpdate(workspace: ByteArray, x2Words: ULongArray, scalar: ULong): ByteArray {
    if (workspace.size < builder64cd40WorkspaceBytes) {
        throw Libre3CryptoException("the 6421c0 work area must be at least $builder64cd40WorkspaceBytes bytes, not ${workspace.size}")
    }
    if (x2Words.size != builder63c278VectorWords) {
        throw Libre3CryptoException("the 6421c0 x2 words must be $builder63c278VectorWords words, not ${x2Words.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val words = ULongArray(builder64cd40WorkspaceWords) { readUInt64LE(workspace, it * 8) }

    for (base in 0 until builder63c278VectorWords) {
        val params = builder6421c0WorkspaceParams(scalar, words[base], tables)
        for (offset in x2Words.indices) {
            val pos = base + offset
            words[pos] = words[pos] + params.broadcast + x2Words[offset] * params.multiplier
        }
        words[base + 1] = builder6421c0RewriteSecondWord(words[base], words[base + 1], tables)
    }
    return packUInt64LE64cd40(words)
}

internal fun builder6421c0FinalU32Words(workspace: ByteArray): UIntArray {
    if (workspace.size < builder64cd40WorkspaceBytes) {
        throw Libre3CryptoException("the 6421c0 work area must be at least $builder64cd40WorkspaceBytes bytes, not ${workspace.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val words = ULongArray(builder64cd40WorkspaceWords) { readUInt64LE(workspace, it * 8) }
    var carry = 0x14ee1c03e369d629uL
    val out = UIntArray(builder63c278VectorWords)

    for (index in 0 until builder63c278VectorWords) {
        val tailWord = words[builder63c278VectorWords + index]
        val mixed = carry * 0x0338c0e89dc8ee71uL + tailWord * 0x32afeb8e00ff3e85uL + 0xfc9f014fa6b572f5uL
        val folded7 = fold63c278(
            mixed * 0xea4b89dcd43400c5uL + 0x3ea3d75ac0581688uL, builder6421c0FinalFoldTable, 7, tables,
        )
        val side = mixed.toUInt() * 0x279eaf81u + folded7.toUInt() * 0x30000000u + 0xac5f152cu
        val folded = fold63c278(folded7, builder6421c0FinalFoldTable, 9, tables)
        carry = folded7 * 0x571b49fe43ec4f5duL + folded * 0xc13b0a3000000000uL + 0x04e301c0d1003cfcuL
        val tableOffset = (index * 4) and 0x1c
        out[index] = u32TableAffine63c278(
            side, builder6421c0FinalOutMulTable + tableOffset, builder6421c0FinalOutAddTable + tableOffset, tables,
        )
    }
    return out
}

internal fun builder6421c0OutputWords(x0Source: ByteArray, x1Source: ByteArray, x2Source: ByteArray, scalar: ULong): UIntArray {
    val x0Streams = builder6421c0X0Streams(x0Source)
    val x1Streams = builder6421c0X1Streams(x1Source)
    val x2Words = builder6421c0X2Words(x2Source)
    val workspace = builder6421c0ConvolutionWorkspace(x0Streams, x1Streams)
    val updated = builder6421c0WorkspaceAfterUpdate(workspace, x2Words, scalar)
    return builder6421c0FinalU32Words(updated)
}

/** Turns a seventy byte coordinate window into twenty packed words of twenty eight bits. */
internal fun builder6388f0HighSeedX0SourceFrom5bcf98Output(source70: ByteArray): ByteArray {
    if (source70.size < 70) {
        throw Libre3CryptoException("the 6388f0 high x0 source must be at least 70 bytes, not ${source70.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val packedWords = UIntArray(18)
    for (index in 0 until 70) {
        val wordIndex = index / 4
        val shift = (index * 8) and 0x18
        packedWords[wordIndex] = packedWords[wordIndex] or (source70.u8(index).toUInt() shl shift)
    }

    val out = UIntArray(20)
    for (index in 0 until 20) {
        val bitOffset = index * 28
        val wordIndex = bitOffset shr 5
        val shift = bitOffset and 0x1c
        var value = packedWords[wordIndex] shr shift
        if (shift != 0) {
            value = value or (packedWords[wordIndex + 1] shl (32 - shift))
        }
        value = value and 0x0fffffffu
        value = value * 0x83dcb233u + 0x774e86a1u
        out[index] = u32Affine63c278(
            value, index, builder6388f0HighSeedX0SourceMulTable, builder6388f0HighSeedX0SourceAddTable, tables,
        )
    }
    return packUInt32LE(out)
}

internal fun builder6388f0HighSeedStreamStartSeedsFrom5bcf98Outputs(
    firstOutput70: ByteArray,
    secondOutput70: ByteArray,
    x1Source: ByteArray? = null,
    x2Source: ByteArray? = null,
    scalar: ULong? = null,
): Builder6388f0HighSeedStreamStartSeeds {
    val resolvedX1 = x1Source ?: highSeed6421c0X1Source
    val resolvedX2 = x2Source ?: highSeed6421c0X2Source
    val resolvedScalar = scalar ?: highSeed6421c0Scalar
    val out0 = builder6421c0OutputWords(
        builder6388f0HighSeedX0SourceFrom5bcf98Output(firstOutput70), resolvedX1, resolvedX2, resolvedScalar,
    )
    val out1 = builder6421c0OutputWords(
        builder6388f0HighSeedX0SourceFrom5bcf98Output(secondOutput70), resolvedX1, resolvedX2, resolvedScalar,
    )
    return Builder6388f0HighSeedStreamStartSeeds(packUInt32LE(out0), packUInt32LE(out1))
}

/** The numbers one round of the `6421c0` work area update uses. */
internal class Libre3WorkspaceParams(val multiplier: ULong, val broadcast: ULong)

internal fun builder6421c0WorkspaceParams(scalar: ULong, firstWord: ULong, tables: Libre3FirstPairTables): Libre3WorkspaceParams {
    val seedA = scalar * 0x5509a203390f347fuL + 0x32f1fb0a9d874bf4uL
    val seedB = scalar * 0x4c2221c00f3005fbuL + 0x0ff14ba0b2a5c7bauL
    var mixed = firstWord * seedA + seedB
    val folded = fold63c278(
        mixed * 0x473c6a74e974ae65uL + 0xadebeda263d28433uL, builder6421c0WorkspaceFoldTable, 7, tables,
    )
    mixed = mixed * 0xef65aceeafea45e9uL + folded * 0x5fe62b0cb0000000uL + 0xd7d1a2ac976837c3uL
    return Libre3WorkspaceParams(
        mixed * 0x9c52396943c088f7uL + 0x8983840ba934a2f1uL,
        mixed * 0x0fd36815b245b0f2uL + 0x3aa2f36c3a09d43euL,
    )
}

internal fun builder6421c0RewriteSecondWord(first: ULong, second: ULong, tables: Libre3FirstPairTables): ULong {
    val folded = fold63c278(
        first * 0x12c340b4b411bb8duL + 0xab10f2a46110bcebuL, builder6421c0RewriteFold1Table, 7, tables,
    )
    var mixed = folded * 0xcfdc2f8d3b1f41e3uL + 0x317484327c6f968auL
    val folded2 = fold63c278(
        mixed * 0xeefa3d8f20f54f35uL + 0x483345b5f608f667uL, builder6421c0RewriteFold2Table, 9, tables,
    )
    mixed = mixed * 0x6b6283330fe2b923uL + folded2 * 0x6214609000000000uL
    return mixed * 0x8d48d385aeebeb5duL + second + 0x71783af05ec8119fuL
}
