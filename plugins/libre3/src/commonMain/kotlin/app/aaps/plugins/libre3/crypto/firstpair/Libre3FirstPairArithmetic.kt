package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException

/*
 * The word arithmetic of the first pairing scheme.
 *
 * Ported from the private helpers of LibreCRKit `Crypto/FirstPairSourceSlice.swift` at pin
 * `a86b92f`.
 *
 * Every multiply and add here wraps. In Swift that is written `&*` and `&+`; in Kotlin the
 * arithmetic of `UInt` and `ULong` already wraps, so the plain operators are the right port.
 */

/** The running totals of a list of long words. */
internal fun prefixSumsU64(words: ULongArray): ULongArray {
    var total = 0uL
    return ULongArray(words.size) { total += words[it]; total }
}

/** The running totals of a list of long words, with a leading zero. */
internal fun shiftedPrefixSumsU64(words: ULongArray): ULongArray {
    var total = 0uL
    val out = ULongArray(words.size + 1)
    for (index in words.indices) {
        total += words[index]
        out[index + 1] = total
    }
    return out
}

/** The total of one run of a list, read off its running totals. */
internal fun rangeSumFromPrefix(prefix: ULongArray, start: Int, end: Int): ULong {
    if (start > end) return 0uL
    val total = prefix[end]
    if (start == 0) return total
    return total - prefix[start - 1]
}

/** The steps between running totals. */
internal fun diffCumulativeQwords(prefixes: ULongArray): ULongArray {
    if (prefixes.isEmpty()) return ULongArray(0)
    val out = ULongArray(prefixes.size)
    out[0] = prefixes[0]
    for (index in 1 until prefixes.size) {
        out[index] = prefixes[index] - prefixes[index - 1]
    }
    return out
}

/** The running totals of a list of long words. */
internal fun cumulativeQwords(values: ULongArray): ULongArray {
    var total = 0uL
    return ULongArray(values.size) { total += values[it]; total }
}

/** A 44 word convolution of two 22 word lists, mixed as the original does. */
@Suppress("LongParameterList")
internal fun convolutionWorkspaceU64(
    aWords: ULongArray,
    bWords: ULongArray,
    baseAdd: ULong,
    countMul: ULong,
    productMul: ULong,
    sumAMul: ULong,
    sumBMul: ULong,
    finalMul: ULong,
    finalAdd: ULong,
): ByteArray {
    val aPrefix = prefixSumsU64(aWords)
    val bPrefix = prefixSumsU64(bWords)
    val out = ByteArray(44 * 8)

    for (index in 0 until 44) {
        val start = maxOf(0, index - 21)
        val end = minOf(index, 21)
        if (start > end) {
            writeUInt64LE(baseAdd * finalMul + finalAdd, out, index * 8)
            continue
        }
        var productSum = 0uL
        for (pos in start..end) {
            productSum += aWords[pos] * bWords[index - pos]
        }
        val sumA = rangeSumFromPrefix(aPrefix, start, end)
        val sumB = rangeSumFromPrefix(bPrefix, index - end, index - start)
        val mixed = (end - start + 1).toULong() * countMul +
            baseAdd +
            productSum * productMul +
            sumA * sumAMul +
            sumB * sumBMul
        writeUInt64LE(mixed * finalMul + finalAdd, out, index * 8)
    }
    return out
}

/** The twenty two words of a `63c278` argument. */
internal fun arg0Words63c278(arg0: ByteArray): UIntArray {
    if (arg0.size < builder63c278VectorBytes) {
        throw Libre3CryptoException("the 63c278 arg0 must be at least $builder63c278VectorBytes bytes, not ${arg0.size}")
    }
    return UIntArray(builder63c278VectorWords) { readUInt32LE(arg0, it * 4) }
}

internal fun u32Affine63c278(word: UInt, index: Int, mulTable: Int, addTable: Int, tables: Libre3FirstPairTables): UInt {
    val tableOffset = (index * 4) and 0x1c
    val multiplier = u32TableWord63c278(mulTable + tableOffset, tables)
    val addend = u32TableWord63c278(addTable + tableOffset, tables)
    return word * multiplier + addend
}

internal fun u32Affine633fa8Tail(word: UInt, index: Int, mulTable: Int, addTable: Int, tables: Libre3FirstPairTables): UInt {
    val tableOffset = (index * 4) and 0x1c
    val multiplier = u32TableWord633fa8Tail(mulTable + tableOffset, tables)
    val addend = u32TableWord633fa8Tail(addTable + tableOffset, tables)
    return word * multiplier + addend
}

internal fun u32AffineInverse63c278(word: UInt, index: Int, mulTable: Int, addTable: Int, tables: Libre3FirstPairTables): UInt {
    val tableOffset = (index * 4) and 0x1c
    val multiplier = u32TableWord63c278(mulTable + tableOffset, tables)
    if (multiplier and 1u != 1u) {
        throw Libre3CryptoException("the 63c278 multiplier $multiplier is even, so it cannot be undone")
    }
    val addend = u32TableWord63c278(addTable + tableOffset, tables)
    return (word - addend) * modularInverseOddUInt32(multiplier)
}

internal fun u32TableAffine63c278(word: UInt, mulTable: Int, addTable: Int, tables: Libre3FirstPairTables): UInt {
    val multiplier = u32TableWord63c278(mulTable, tables)
    val addend = u32TableWord63c278(addTable, tables)
    return word * multiplier + addend
}

internal fun u32AffineInverseBytes63c278(
    input: ByteArray,
    mulTable: Int,
    addTable: Int,
    label: String,
    tables: Libre3FirstPairTables,
): ByteArray {
    if (input.size < builder63c278VectorBytes) {
        throw Libre3CryptoException("the $label must be at least $builder63c278VectorBytes bytes, not ${input.size}")
    }
    val out = ByteArray(builder63c278VectorBytes)
    for (index in 0 until builder63c278VectorWords) {
        val word = u32AffineInverse63c278(readUInt32LE(input, index * 4), index, mulTable, addTable, tables)
        writeUInt32LE(word, out, index * 4)
    }
    return out
}

/** The odd word that undoes a multiply, by five rounds of Newton's step. */
internal fun modularInverseOddUInt32(value: UInt): UInt {
    var inverse = value
    repeat(5) {
        inverse *= (2u - value * inverse)
    }
    return inverse
}

internal fun u32TableWord63c278(absoluteOffset: Int, tables: Libre3FirstPairTables): UInt {
    val relative = absoluteOffset - table63c278U32Base
    if (relative < 0 || relative + 4 > tables.u32Tables63c278.size) {
        throw Libre3CryptoException(
            "the first pairing table ${Libre3FirstPairTables.U32_TABLES_63C278} has nothing at offset $absoluteOffset"
        )
    }
    return readUInt32LE(tables.u32Tables63c278, relative)
}

internal fun u32TableWord633fa8Tail(absoluteOffset: Int, tables: Libre3FirstPairTables): UInt {
    if (absoluteOffset >= table63c278U32Base) return u32TableWord63c278(absoluteOffset, tables)
    val relative = absoluteOffset - table633fa8TailU32LowBase
    if (relative < 0 || relative + 4 > tables.tailU32LowTables633fa8.size) {
        throw Libre3CryptoException(
            "the first pairing table ${Libre3FirstPairTables.TAIL_U32_LOW_TABLES_633FA8} has nothing at offset $absoluteOffset"
        )
    }
    return readUInt32LE(tables.tailU32LowTables633fa8, relative)
}

internal fun foldTableU32Word63c278(absoluteOffset: Int, tables: Libre3FirstPairTables): UInt {
    val relative = absoluteOffset - table63c278FoldBase
    if (relative < 0 || relative + 4 > tables.foldTables63c278.size) {
        throw Libre3CryptoException(
            "the first pairing table ${Libre3FirstPairTables.FOLD_TABLES_63C278} has nothing at offset $absoluteOffset"
        )
    }
    return readUInt32LE(tables.foldTables63c278, relative)
}

internal fun foldTableU64Word63c278(absoluteOffset: Int, tables: Libre3FirstPairTables): ULong {
    val relative = absoluteOffset - table63c278FoldBase
    if (relative < 0 || relative + 8 > tables.foldTables63c278.size) {
        throw Libre3CryptoException(
            "the first pairing table ${Libre3FirstPairTables.FOLD_TABLES_63C278} has nothing at offset $absoluteOffset"
        )
    }
    return readUInt64LE(tables.foldTables63c278, relative)
}

/** One piece of a table read as words. */
internal class TableSegment(val offset: Int, val byteCount: Int)

internal fun u32WordsFromTableSegments(segments: List<TableSegment>, tables: Libre3FirstPairTables): UIntArray {
    val out = ArrayList<UInt>()
    for (segment in segments) {
        require(segment.byteCount % 4 == 0) { "a table piece must be a whole number of words" }
        for (offset in 0 until segment.byteCount step 4) {
            out.add(u32TableWord63c278(segment.offset + offset, tables))
        }
    }
    return out.toUIntArray()
}

/** Folds a long word, four bits at a time, through the `63c278` fold table. */
internal fun fold63c278(value: ULong, tableOffset: Int, rounds: Int, tables: Libre3FirstPairTables): ULong {
    var folded = value
    repeat(rounds) {
        val relative = tableOffset - table63c278FoldBase + (folded and 0x0fuL).toInt() * 8
        if (relative < 0 || relative + 8 > tables.foldTables63c278.size) {
            throw Libre3CryptoException(
                "the first pairing table ${Libre3FirstPairTables.FOLD_TABLES_63C278} has nothing at offset $tableOffset"
            )
        }
        folded = readUInt64LE(tables.foldTables63c278, relative) + (folded shr 4)
    }
    return folded
}

/** Folds a long word through the `633fa8` tail fold table. */
internal fun fold633fa8Tail(value: ULong, tableOffset: Int, rounds: Int, tables: Libre3FirstPairTables): ULong {
    var folded = value
    repeat(rounds) {
        val relative = tableOffset - table633fa8TailFoldBase + (folded and 0x0fuL).toInt() * 8
        if (relative < 0 || relative + 8 > tables.tailFoldTables633fa8.size) {
            throw Libre3CryptoException(
                "the first pairing table ${Libre3FirstPairTables.TAIL_FOLD_TABLES_633FA8} has nothing at offset $tableOffset"
            )
        }
        folded = readUInt64LE(tables.tailFoldTables633fa8, relative) + (folded shr 4)
    }
    return folded
}

/** The first round folds the product, then the addend joins in. */
internal fun fold63c278FirstNibbleBeforeAdd(product: ULong, addend: ULong, tableOffset: Int, tables: Libre3FirstPairTables): ULong {
    val relative = tableOffset - table63c278FoldBase + (product and 0x0fuL).toInt() * 8
    if (relative < 0 || relative + 8 > tables.foldTables63c278.size) {
        throw Libre3CryptoException(
            "the first pairing table ${Libre3FirstPairTables.FOLD_TABLES_63C278} has nothing at offset $tableOffset"
        )
    }
    val folded = readUInt64LE(tables.foldTables63c278, relative) + ((product + addend) shr 4)
    return fold63c278(folded, tableOffset, 7, tables)
}

/** Folds a word, four bits at a time. */
internal fun fold32ByNibbles63c278(value: UInt, tableOffset: Int, rounds: Int, tables: Libre3FirstPairTables): UInt {
    var folded = value
    repeat(rounds) {
        val word = foldTableU32Word63c278(tableOffset + (folded and 0x0fu).toInt() * 4, tables)
        folded = word + (folded shr 4)
    }
    return folded
}

/** The stream word of the `633fa8` tail. */
@Suppress("LongParameterList")
internal fun builder633fa8TailStreamU64(
    word: UInt,
    wordMul: ULong,
    wordAdd: ULong,
    foldTable: Int,
    foldMul: ULong,
    mixMul: ULong,
    mixAdd: ULong,
    tables: Libre3FirstPairTables,
): ULong {
    val folded = fold633fa8Tail(word.toULong() * wordMul + wordAdd, foldTable, 8, tables)
    return folded * foldMul + word.toULong() * mixMul + mixAdd
}

/** A piece of the `process2(5)` public table. */
internal fun process2P5PublicTableBlock(libOffset: Int, byteCount: Int, tables: Libre3FirstPairTables): ByteArray {
    val relative = libOffset - process2P5PublicTableBase
    if (relative < 0 || relative + byteCount > tables.process2PublicTables.size) {
        throw Libre3CryptoException(
            "the first pairing table ${Libre3FirstPairTables.PROCESS2_PUBLIC_TABLES} has nothing at offset $libOffset"
        )
    }
    return tables.process2PublicTables.copyOfRange(relative, relative + byteCount)
}

internal fun process2P5PublicTableByte(libOffset: Int, tables: Libre3FirstPairTables): Int =
    process2P5PublicTableBlock(libOffset, 1, tables).u8(0)

internal fun process2P5PublicTableUInt64(libOffset: Int, tables: Libre3FirstPairTables): ULong =
    readUInt64LE(process2P5PublicTableBlock(libOffset, 8, tables), 0)

internal fun foldProcess2P5Public(value: ULong, tableOffset: Int, rounds: Int, tables: Libre3FirstPairTables): ULong {
    var folded = value
    repeat(rounds) {
        folded = process2P5PublicTableUInt64(tableOffset + (folded and 0x0fuL).toInt() * 8, tables) + (folded shr 4)
    }
    return folded
}

/** What one run of the three bit unpacker produced, and where it stopped. */
internal class Unpacked3Bit(val values: ByteArray, val nextOffset: Int)

/**
 * Reads [count] three bit values out of a packed source.
 *
 * The bit walk below looks odd, and it is kept exactly as the original: a value that starts at bit
 * six or seven is read across two bytes, and the walk then restarts inside the second byte.
 */
internal fun unpack3BitStream5bdd14(source: ByteArray, offset: Int, count: Int): Unpacked3Bit {
    if (count < 0) {
        throw Libre3CryptoException("a three bit unpack cannot ask for $count values")
    }
    if (offset < 0 || offset >= source.size) {
        throw Libre3CryptoException("a three bit unpack cannot start at $offset of ${source.size} bytes")
    }

    var pointer = offset
    var bitOffset = 0
    val out = ByteArray(count)
    var write = 0
    var remaining = count
    while (remaining > 0) {
        val currentBit = bitOffset and 0xff
        when {
            currentBit == 8  -> {
                pointer += 1
                if (pointer >= source.size) {
                    throw Libre3CryptoException("a three bit unpack ran past the end of its source")
                }
                out[write++] = (source.u8(pointer) and 7).toByte()
                bitOffset = 3
            }

            currentBit == 0  -> {
                out[write++] = (source.u8(pointer) and 7).toByte()
                bitOffset = 3
            }

            currentBit <= 5  -> {
                out[write++] = ((source.u8(pointer) shr currentBit) and 7).toByte()
                bitOffset = currentBit + 3
            }

            else             -> {
                val spanBits = currentBit - 5
                if (pointer + 1 >= source.size) {
                    throw Libre3CryptoException("a three bit unpack ran past the end of its source")
                }
                val low = source.u8(pointer) shr currentBit
                val high = source.u8(pointer + 1) and ((1 shl spanBits) - 1)
                out[write++] = ((low or ((high shl (8 - currentBit)) and 0xFF)) and 7).toByte()
                pointer += 1
                bitOffset = spanBits
            }
        }
        remaining -= 1
    }

    pointer += 1
    if (pointer > source.size) {
        throw Libre3CryptoException("a three bit unpack ran past the end of its source")
    }
    return Unpacked3Bit(out, pointer)
}
