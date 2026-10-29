package app.aaps.plugins.libre3.crypto.firstpair

/*
 * The word makers of the `6473d0` builder and of the tenth `642f60` round.
 *
 * Ported from LibreCRKit `Crypto/FirstPairSourceSlice.swift` at pin `a86b92f`. They all have the
 * shape of [builder642f60StreamWord] and differ only in their numbers, which are named constants
 * in the original too.
 */

internal fun builder6473d0FirstAWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, builder6473d0FirstA0U32Mul, builder6473d0FirstA0U32Add, builder6473d0FirstAMulTable, builder6473d0FirstAAddTable, builder6473d0FirstAU32Mul, builder6473d0FirstAU32Add,
        builder6473d0FirstAFoldMul, builder6473d0FirstAFoldAdd, builder6473d0FirstAFoldTable,
        builder6473d0FirstALinearMul, builder6473d0FirstAFoldedMul, builder6473d0FirstALinearAdd, tables,
    )

internal fun builder6473d0FirstBWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, builder6473d0FirstB0U32Mul, builder6473d0FirstB0U32Add, builder6473d0FirstBMulTable, builder6473d0FirstBAddTable, builder6473d0FirstBU32Mul, builder6473d0FirstBU32Add,
        builder6473d0FirstBFoldMul, builder6473d0FirstBFoldAdd, builder6473d0FirstBFoldTable,
        builder6473d0FirstBLinearMul, builder6473d0FirstBFoldedMul, builder6473d0FirstBLinearAdd, tables,
    )

internal fun builder6473d0SecondAWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, builder6473d0SecondA0U32Mul, builder6473d0SecondA0U32Add, builder6473d0SecondAMulTable, builder6473d0SecondAAddTable, builder6473d0SecondAU32Mul, builder6473d0SecondAU32Add,
        builder6473d0SecondAFoldMul, builder6473d0SecondAFoldAdd, builder6473d0SecondAFoldTable,
        builder6473d0SecondALinearMul, builder6473d0SecondAFoldedMul, builder6473d0SecondALinearAdd, tables,
    )

internal fun builder6473d0SecondBWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, builder6473d0SecondB0U32Mul, builder6473d0SecondB0U32Add, builder6473d0SecondBMulTable, builder6473d0SecondBAddTable, builder6473d0SecondBU32Mul, builder6473d0SecondBU32Add,
        builder6473d0SecondBFoldMul, builder6473d0SecondBFoldAdd, builder6473d0SecondBFoldTable,
        builder6473d0SecondBLinearMul, builder6473d0SecondBFoldedMul, builder6473d0SecondBLinearAdd, tables,
    )

internal fun builder6473d0ThirdAWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, builder6473d0ThirdA0U32Mul, builder6473d0ThirdA0U32Add, builder6473d0ThirdAMulTable, builder6473d0ThirdAAddTable, builder6473d0ThirdAU32Mul, builder6473d0ThirdAU32Add,
        builder6473d0ThirdAFoldMul, builder6473d0ThirdAFoldAdd, builder6473d0ThirdAFoldTable,
        builder6473d0ThirdALinearMul, builder6473d0ThirdAFoldedMul, builder6473d0ThirdALinearAdd, tables,
    )

internal fun builder6473d0ThirdBWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, builder6473d0ThirdB0U32Mul, builder6473d0ThirdB0U32Add, builder6473d0ThirdBMulTable, builder6473d0ThirdBAddTable, builder6473d0ThirdBU32Mul, builder6473d0ThirdBU32Add,
        builder6473d0ThirdBFoldMul, builder6473d0ThirdBFoldAdd, builder6473d0ThirdBFoldTable,
        builder6473d0ThirdBLinearMul, builder6473d0ThirdBFoldedMul, builder6473d0ThirdBLinearAdd, tables,
    )

internal fun builder6473d0FourthAWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, builder6473d0FourthA0U32Mul, builder6473d0FourthA0U32Add, builder6473d0FourthAMulTable, builder6473d0FourthAAddTable, builder6473d0FourthAU32Mul, builder6473d0FourthAU32Add,
        builder6473d0FourthAFoldMul, builder6473d0FourthAFoldAdd, builder6473d0FourthAFoldTable,
        builder6473d0FourthALinearMul, builder6473d0FourthAFoldedMul, builder6473d0FourthALinearAdd, tables,
    )

internal fun builder6473d0FourthBWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, builder6473d0FourthB0U32Mul, builder6473d0FourthB0U32Add, builder6473d0FourthBMulTable, builder6473d0FourthBAddTable, builder6473d0FourthBU32Mul, builder6473d0FourthBU32Add,
        builder6473d0FourthBFoldMul, builder6473d0FourthBFoldAdd, builder6473d0FourthBFoldTable,
        builder6473d0FourthBLinearMul, builder6473d0FourthBFoldedMul, builder6473d0FourthBLinearAdd, tables,
    )

internal fun builder6473d0FifthAWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, builder6473d0FifthA0U32Mul, builder6473d0FifthA0U32Add, builder6473d0FifthAMulTable, builder6473d0FifthAAddTable, builder6473d0FifthAU32Mul, builder6473d0FifthAU32Add,
        builder6473d0FifthAFoldMul, builder6473d0FifthAFoldAdd, builder6473d0FifthAFoldTable,
        builder6473d0FifthALinearMul, builder6473d0FifthAFoldedMul, builder6473d0FifthALinearAdd, tables,
    )

internal fun builder6473d0FifthBWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, builder6473d0FifthB0U32Mul, builder6473d0FifthB0U32Add, builder6473d0FifthBMulTable, builder6473d0FifthBAddTable, builder6473d0FifthBU32Mul, builder6473d0FifthBU32Add,
        builder6473d0FifthBFoldMul, builder6473d0FifthBFoldAdd, builder6473d0FifthBFoldTable,
        builder6473d0FifthBLinearMul, builder6473d0FifthBFoldedMul, builder6473d0FifthBLinearAdd, tables,
    )

internal fun builder6473d0SixthAWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, builder6473d0SixthA0U32Mul, builder6473d0SixthA0U32Add, builder6473d0SixthAMulTable, builder6473d0SixthAAddTable, builder6473d0SixthAU32Mul, builder6473d0SixthAU32Add,
        builder6473d0SixthAFoldMul, builder6473d0SixthAFoldAdd, builder6473d0SixthAFoldTable,
        builder6473d0SixthALinearMul, builder6473d0SixthAFoldedMul, builder6473d0SixthALinearAdd, tables,
    )

internal fun builder6473d0SixthBWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, builder6473d0SixthB0U32Mul, builder6473d0SixthB0U32Add, builder6473d0SixthBMulTable, builder6473d0SixthBAddTable, builder6473d0SixthBU32Mul, builder6473d0SixthBU32Add,
        builder6473d0SixthBFoldMul, builder6473d0SixthBFoldAdd, builder6473d0SixthBFoldTable,
        builder6473d0SixthBLinearMul, builder6473d0SixthBFoldedMul, builder6473d0SixthBLinearAdd, tables,
    )

internal fun builder6473d0EighthAWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, builder6473d0EighthA0U32Mul, builder6473d0EighthA0U32Add, builder6473d0EighthAMulTable, builder6473d0EighthAAddTable, builder6473d0EighthAU32Mul, builder6473d0EighthAU32Add,
        builder6473d0EighthAFoldMul, builder6473d0EighthAFoldAdd, builder6473d0EighthAFoldTable,
        builder6473d0EighthALinearMul, builder6473d0EighthAFoldedMul, builder6473d0EighthALinearAdd, tables,
    )

internal fun builder6473d0EighthBWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, builder6473d0EighthB0U32Mul, builder6473d0EighthB0U32Add, builder6473d0EighthBMulTable, builder6473d0EighthBAddTable, builder6473d0EighthBU32Mul, builder6473d0EighthBU32Add,
        builder6473d0EighthBFoldMul, builder6473d0EighthBFoldAdd, builder6473d0EighthBFoldTable,
        builder6473d0EighthBLinearMul, builder6473d0EighthBFoldedMul, builder6473d0EighthBLinearAdd, tables,
    )
internal fun builder6473d0TenthAWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, builder6473d0TenthA0U32Mul, builder6473d0TenthA0U32Add, builder6473d0TenthAMulTable, builder6473d0TenthAAddTable, builder6473d0TenthAU32Mul, builder6473d0TenthAU32Add,
        builder6473d0TenthAFoldMul, builder6473d0TenthAFoldAdd, builder6473d0TenthAFoldTable,
        builder6473d0TenthALinearMul, builder6473d0TenthAFoldedMul, builder6473d0TenthALinearAdd, tables,
    )

internal fun builder6473d0TenthBWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, builder6473d0TenthB0U32Mul, builder6473d0TenthB0U32Add, builder6473d0TenthBMulTable, builder6473d0TenthBAddTable, builder6473d0TenthBU32Mul, builder6473d0TenthBU32Add,
        builder6473d0TenthBFoldMul, builder6473d0TenthBFoldAdd, builder6473d0TenthBFoldTable,
        builder6473d0TenthBLinearMul, builder6473d0TenthBFoldedMul, builder6473d0TenthBLinearAdd, tables,
    )

internal fun builder6473d0SixthSP750Word(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, builder6473d0SixthB0U32Mul, builder6473d0SixthB0U32Add, builder6473d0SixthAMulTable, builder6473d0SixthAAddTable, builder6473d0SixthAU32Mul, builder6473d0SixthAU32Add,
        builder6473d0SixthBFoldMul, builder6473d0SixthBFoldAdd, builder6473d0SixthBFoldTable,
        builder6473d0SixthBLinearMul, builder6473d0SixthBFoldedMul, builder6473d0SixthBLinearAdd, tables,
    )

internal fun builder6473d0SixthSP698Word(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, builder6473d0SixthA0U32Mul, builder6473d0SixthA0U32Add, builder6473d0SixthBMulTable, builder6473d0SixthBAddTable, builder6473d0SixthBU32Mul, builder6473d0SixthBU32Add,
        builder6473d0SixthAFoldMul, builder6473d0SixthAFoldAdd, builder6473d0SixthAFoldTable,
        builder6473d0SixthALinearMul, builder6473d0SixthAFoldedMul, builder6473d0SixthALinearAdd, tables,
    )

internal fun builder6473d0SeventhSP750Word(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, builder6473d0SeventhSP750A0U32Mul, builder6473d0SeventhSP750A0U32Add, builder6473d0SeventhSP750MulTable, builder6473d0SeventhSP750AddTable, builder6473d0SeventhSP750U32Mul, builder6473d0SeventhSP750U32Add,
        builder6473d0SeventhSP750FoldMul, builder6473d0SeventhSP750FoldAdd, builder6473d0SeventhSP750FoldTable,
        builder6473d0SeventhSP750LinearMul, builder6473d0SeventhSP750FoldedMul, builder6473d0SeventhSP750LinearAdd, tables,
    )

internal fun builder6473d0SeventhSP698Word(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong =
    builder642f60StreamWord(
        word, index, builder6473d0SeventhSP698A0U32Mul, builder6473d0SeventhSP698A0U32Add, builder6473d0SeventhSP698MulTable, builder6473d0SeventhSP698AddTable, builder6473d0SeventhSP698U32Mul, builder6473d0SeventhSP698U32Add,
        builder6473d0SeventhSP698FoldMul, builder6473d0SeventhSP698FoldAdd, builder6473d0SeventhSP698FoldTable,
        builder6473d0SeventhSP698LinearMul, builder6473d0SeventhSP698FoldedMul, builder6473d0SeventhSP698LinearAdd, tables,
    )
