package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException

/*
 * The word makers of the `642f60` builder.
 *
 * Ported from LibreCRKit `Crypto/FirstPairSourceSlice.swift` at pin `a86b92f`.
 *
 * Fifteen of the sixteen have exactly the same shape and differ only in their numbers, so they
 * share [builder642f60StreamWord]. The sixteenth, `builder642f60ThirdAWord`, folds in a different
 * order and is written out on its own; that is not a slip, it is what the original does.
 */

@Suppress("LongParameterList")
internal fun builder642f60StreamWord(
    word: UInt,
    index: Int,
    firstMul: UInt,
    firstAdd: UInt,
    mulTable: Int,
    addTable: Int,
    laterMul: UInt,
    laterAdd: UInt,
    qwordMul: ULong,
    qwordAdd: ULong,
    foldTable: Int,
    finalMul: ULong,
    foldMul: ULong,
    finalAdd: ULong,
    tables: Libre3FirstPairTables,
): ULong {
    val w = if (index == 0) {
        word * firstMul + firstAdd
    } else {
        u32Affine63c278(word, index, mulTable, addTable, tables) * laterMul + laterAdd
    }
    val folded = fold63c278(w.toULong() * qwordMul + qwordAdd, foldTable, 8, tables)
    return w.toULong() * finalMul + folded * foldMul + finalAdd
}

/** The one word maker that folds the product before the addend joins in. */
internal fun builder642f60ThirdAWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong {
    val w = if (index == 0) {
        word * 0x92a36947u + 0xab2632fcu
    } else {
        u32Affine63c278(word, index, builder642f60ThirdAMulTable, builder642f60ThirdAAddTable, tables) *
            0xe77cb783u + 0x000f1f23u
    }
    val folded = fold63c278FirstNibbleBeforeAdd(
        w.toULong() * 0xaf7f459e89cfb7e5uL, 0x5c6139b5f80c5a20uL, builder642f60ThirdAFoldTable, tables,
    )
    return w.toULong() * 0x796b8710218eb0c5uL + folded * 0x7224389f00000000uL + 0x086af43c0726c3a9uL
}

/** Twenty two words of a `64bd0c` answer, each through one table driven affine step. */
internal fun builder642f60AffineWordsFrom64bd0cOutput(
    output: ByteArray,
    mulTable: Int,
    addTable: Int,
    label: String,
): UIntArray {
    if (output.size < builder63c278VectorBytes) {
        throw Libre3CryptoException("the $label must be at least $builder63c278VectorBytes bytes, not ${output.size}")
    }
    val tables = Libre3FirstPairTables.get()
    return UIntArray(builder63c278VectorWords) {
        u32Affine63c278(readUInt32LE(output, it * 4), it, mulTable, addTable, tables)
    }
}

internal fun builder642f60FirstAWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, 0x7a6bdb55u, 0x6f457678u, builder642f60FirstAMulTable, builder642f60FirstAAddTable, 0x9935dc8fu, 0x8faec549u,
        0x62170eaa882a1aaduL, 0xfcded5c74336bb62uL, builder642f60FirstAFoldTable, 0x1ae027ac75efae5duL, 0x6a59778f00000000uL, 0x272a0fcbb9692010uL, tables,
    )

internal fun builder642f60FirstBWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, 0x8a0c43a1u, 0xe1069988u, builder642f60FirstBMulTable, builder642f60FirstBAddTable, 0x8fce17f9u, 0x9aa95d9cu,
        0x91c0e3def121255duL, 0x50be110705349aeauL, builder642f60FirstBFoldTable, 0x861960b1d03ace7fuL, 0x7f5bb67500000000uL, 0x4a2faf413913b4a2uL, tables,
    )

internal fun builder642f60SecondAWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, 0xcce32bdbu, 0x79dae932u, builder642f60SecondAMulTable, builder642f60SecondAAddTable, 0x7bd77a89u, 0x0d07fa2au,
        0x0a0b1df06a7b196duL, 0xfd9f62e38b4829f7uL, builder642f60SecondAFoldTable, 0xb1bf8eba2d4b2a69uL, 0xb6b0ac9300000000uL, 0x381faa6c090fdcd8uL, tables,
    )

internal fun builder642f60SecondBWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, 0x9affbc41u, 0x96a579e0u, builder642f60SecondBMulTable, builder642f60SecondBAddTable, 0x3749a60du, 0xb803d34cu,
        0xa5ffca145f08d59buL, 0x8ce0b7edd5a16ba2uL, builder642f60SecondBFoldTable, 0xabe6b5e1333dcc8fuL, 0xfbd091e300000000uL, 0x1df38d76faeb4eaduL, tables,
    )

internal fun builder642f60ThirdBWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, 0x032a79c5u, 0x8da26e96u, builder642f60ThirdBMulTable, builder642f60ThirdBAddTable, 0xb13f4189u, 0x2b7b0e41u,
        0x3144f0ff41a1df83uL, 0x3d414cbf18310011uL, builder642f60ThirdBFoldTable, 0x861a1f875b2dc69buL, 0xf0b786f700000000uL, 0x63b83ae085557472uL, tables,
    )

internal fun builder642f60FourthAWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, 0x9a4392dbu, 0x0d1015eau, builder642f60FourthAMulTable, builder642f60FourthAAddTable, 0x97151be3u, 0x70bf5e2bu,
        0xcf0e32fa8d969f65uL, 0x61afec1284e66a8cuL, builder642f60FourthAFoldTable, 0x610ca66bd199f2b5uL, 0xde6166ef00000000uL, 0x50d31e15d8b1af56uL, tables,
    )

internal fun builder642f60FourthBWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, 0x2ac5e1c1u, 0x957be66cu, builder642f60FourthBMulTable, builder642f60FourthBAddTable, 0xee64b1f5u, 0x5df44367u,
        0x013aed389aef9cd9uL, 0x9ac1ba0fa43555a1uL, builder642f60FourthBFoldTable, 0x3ca485be7caa6cf3uL, 0xcec7175500000000uL, 0x44d63f7b1e64fe52uL, tables,
    )

internal fun builder642f60MidContextStreamWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, 0xef8a98c3u, 0x5251f797u, builder642f60MidContextMulTable, builder642f60MidContextAddTable, 0x9198bbe1u, 0x96d49925u,
        0x10aca1fefeaea819uL, 0x791f2f89d18f0bccuL, builder642f60MidContextFoldTable, 0x7e3d39fbe4db207buL, 0xf948d04d00000000uL, 0xefba822749ae8302uL, tables,
    )

internal fun builder642f60MidSPF0StreamWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, 0x833f922fu, 0xb8f79a5cu, builder642f60MidSPF0MulTable, builder642f60MidSPF0AddTable, 0xe9ed5087u, 0x99a662bcu,
        0x9445eb5f6cc20c37uL, 0x2e115166fc9d38deuL, builder642f60MidSPF0FoldTable, 0x76037a61bba475bduL, 0x7a18645500000000uL, 0xeb599af66ebe44f8uL, tables,
    )

internal fun builder642f60MidSP40BWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, 0x68f1c9c3u, 0x75e3de3du, builder642f60MidSP40BMulTable, builder642f60MidSP40BAddTable, 0x603eaaa7u, 0xf3704eb8u,
        0x339d03216c178183uL, 0xccccddb48073e82duL, builder642f60MidSP40BFoldTable, 0x6255799ade203b13uL, 0x504804cf00000000uL, 0xece1b0fccff7a5d6uL, tables,
    )

internal fun builder642f60SixthAWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, 0xe12f8e63u, 0xed30a70du, builder642f60SixthAMulTable, builder642f60SixthAAddTable, 0x61e5762bu, 0xd79521cbu,
        0x5ebafd23d4800453uL, 0xfce166cf66e4ed89uL, builder642f60SixthAFoldTable, 0xfe6b40e82ac2cfaduL, 0x4b28a40100000000uL, 0x5ffded7fc281e70cuL, tables,
    )

internal fun builder642f60SixthBWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, 0x5f8eb06bu, 0x71dd9075u, builder642f60SixthBMulTable, builder642f60SixthBAddTable, 0x32ca6d69u, 0x5b73a719u,
        0xf82a21269cc1d1dbuL, 0xd1172c1561159fb2uL, builder642f60SixthBFoldTable, 0xad5daa3cdd561923uL, 0x5a9053a700000000uL, 0x06054c9125875977uL, tables,
    )

internal fun builder642f60EighthAWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, 0xcbb0f5d5u, 0xbc0ef378u, builder642f60EighthAMulTable, builder642f60EighthAAddTable, 0xf4ade1bbu, 0x14498d6fu,
        0x9e47779cb45c572fuL, 0x4d028e31657373f8uL, builder642f60EighthAFoldTable, 0xa08be5a120f8c447uL, 0xc729619700000000uL, 0x6e392c9a885df52cuL, tables,
    )

internal fun builder642f60EighthBWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, 0x667888b5u, 0x8f0d98aeu, builder642f60EighthBMulTable, builder642f60EighthBAddTable, 0xea2a6db9u, 0x0a1fb246u,
        0x5642541b8c3e3bb7uL, 0x9965e0d235e6c59buL, builder642f60EighthBFoldTable, 0xb84f64edab558edduL, 0x82850df500000000uL, 0xe5d3a90393662e86uL, tables,
    )
