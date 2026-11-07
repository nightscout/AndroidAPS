package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException

/*
 * The helpers of the digest: the words that seed a fresh context, the small reducer, the byte
 * expanders, the folds and the packers of the `df80` round function.
 *
 * Ported from LibreCRKit `Crypto/FirstPairSourceSlice.swift` at pin `a86b92f`.
 */

/** Writes the eight seed words into a fresh context. */
internal fun seed67aa8cInitialWords(context: ByteArray, tables: Libre3FirstPairTables) {
    for (spec in aa8cInitialReducerSpecs) {
        val window = context.copyOfRange(spec.srcOffset, spec.srcOffset + df80WordSize)
        val reduced = reducer67ea28Word(vm67cecc(spec.magic, window, window, tables), tables)
        replace(context, spec.dstOffset, reduced)
    }
}

/** Squeezes one wide word down to four bytes, four bits at a time. */
private fun reducer67ea28Word(src: ByteArray, tables: Libre3FirstPairTables): ByteArray {
    val tmp18 = vm67cc18(0x120000048e2uL, src, src, tables)
    var state18 = vm67d524(0xc00f000c0578euL, tmp18, tables)

    var packed = 0u
    var outShift = 0
    var bitBudget = 0x20
    for (roundIndex in 0 until 8) {
        val scratch4 = vm67cc18(0x40000033d7uL, state18, state18, tables)
        if (bitBudget >= 5) {
            state18 = vm67cecc(0x8010000805f94uL, state18, state18, tables)
        }

        val tmp4 = vm67cc18(0x4000004513uL, scratch4, scratch4, tables)
        val tableIndex = tmp4.u8(2) xor (tmp4.u8(3) shl 3)
        if (tableIndex >= tables.reducer67ea28Nibble.size) {
            throw Libre3CryptoException(
                "the first pairing table ${Libre3FirstPairTables.REDUCER_67EA28_NIBBLE} has nothing at offset $tableIndex"
            )
        }
        val tableByte = tables.reducer67ea28Nibble.u8(tableIndex)
        val nibble = if (roundIndex and 1 == 0) (tableByte and 0x0f).toUInt() else (tableByte shr 4).toUInt()

        val mask: UInt
        if (bitBudget >= 4) {
            mask = UInt.MAX_VALUE
            bitBudget -= 4
        } else {
            mask = if (bitBudget == 0) 0u else ((1u shl bitBudget) - 1u)
            bitBudget = 0
        }

        packed = packed or ((nibble and mask) shl outShift)
        outShift += 4
    }

    return u32LEBytes(packed)
}

/** Turns the eight waiting words into the eight state blocks. */
internal fun update67eb94Blocks(wordsLE: List<ByteArray>, tables: Libre3FirstPairTables): ByteArray {
    if (wordsLE.size != 8) {
        throw Libre3CryptoException("the 67eb94 step needs eight words, not ${wordsLE.size}")
    }
    var blocks = ByteArray(0)
    for ((index, word) in wordsLE.withIndex()) {
        val expanded = expand67ed24(word, tables)
        blocks += vm67cc18(eb94UpdateMagics[index], expanded, expanded, tables)
    }
    return blocks
}

/** Turns raw blocks into the blocks the digest reads, with the program the caller picks. */
internal fun constructor67076cBlocks(rawDescriptorBlocks: ByteArray, magic: ULong): ByteArray {
    if (rawDescriptorBlocks.size % block66Size != 0) {
        throw Libre3CryptoException("the raw blocks must be a whole number of $block66Size byte blocks, not ${rawDescriptorBlocks.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val out = ByteArray(rawDescriptorBlocks.size)
    var write = 0
    for (start in rawDescriptorBlocks.indices step block66Size) {
        val block = rawDescriptorBlocks.copyOfRange(start, start + block66Size)
        vm67076c(magic, block, block, tables).copyInto(out, write)
        write += block66Size
    }
    return out
}

/** Turns one four byte word into one eighteen byte block. */
private fun expand67ed24(wordLE: ByteArray, tables: Libre3FirstPairTables): ByteArray {
    requireSize(wordLE, 4, "67ed24 word")
    val sideA = expandWordTrits(wordLE, expand67ed24AOffset, tables)
    val sideB = expandWordTrits(wordLE, expand67ed24BOffset, tables)

    val foldedA = fold24To18(0x600000133buL, 0x6000003479uL, sideA, tables)
    val wideA = vm67cecc(0x40012000000028uL, foldedA, foldedA, tables)

    val foldedB = fold24To18(0x6000004936uL, 0x6000000000uL, sideB, tables)
    val wideB = vm67cecc(0x40012000004683uL, foldedB, foldedB, tables)

    val mixed = vm67cc18(0x22000004d74uL, wideA, wideB, tables)
    return vm67d524(0xc01f000c05d34uL, mixed, tables)
}

/** Turns four bytes into 24 three bit values, through the finalizer table. */
internal fun expandWordTrits(wordLE: ByteArray, tableOffset: Int, tables: Libre3FirstPairTables): ByteArray {
    requireSize(wordLE, 4, "67ed24 word")
    val out = ByteArray(24)
    var write = 0
    for (index in 0 until 4) {
        val tableIndex = tableOffset + wordLE.u8(index) * 3
        val packed = checkedSlice(tables.finalizerTables, tableIndex, 3, Libre3FirstPairTables.FINALIZER_TABLES)
        for (value in packed) {
            out[write++] = (value.toInt() and 7).toByte()
            out[write++] = ((value.toInt() and 0xFF) shr 3).toByte()
        }
    }
    return out
}

/** Squeezes a 24 byte source down to 18 bytes. */
internal fun fold24To18(firstMagic: ULong, tailMagic: ULong, src24: ByteArray, tables: Libre3FirstPairTables): ByteArray {
    requireSize(src24, 24, "67ed24 fold source")
    val out = vm67cc18(firstMagic, src24, src24, tables)
    var tail = ByteArray(0)
    for (offset in 6..18 step 6) {
        val src = src24.copyOfRange(offset, src24.size)
        tail += vm67cc18(tailMagic, src, src, tables)
    }
    return out + tail.copyOfRange(2, 6) + tail.copyOfRange(8, 12) + tail.copyOfRange(14, 18)
}

/** Turns one raw byte into six three bit values, through the seed table. */
internal fun expandRawByte67d630(byte: Int, tableOffset: Int, tables: Libre3FirstPairTables): ByteArray {
    val index = tableOffset + byte * 3
    val packed = checkedSlice(tables.seedTables679f48, index, 3, Libre3FirstPairTables.SEED_TABLES_679F48)
    val out = ByteArray(6)
    var write = 0
    for (value in packed) {
        out[write++] = (value.toInt() and 7).toByte()
        out[write++] = ((value.toInt() and 0xFF) shr 3).toByte()
    }
    return out
}

/** Squeezes a 96 byte source down to 66 bytes. */
internal fun fold96To66(firstMagic: ULong, tailMagic: ULong, src96: ByteArray, tables: Libre3FirstPairTables): ByteArray {
    requireSize(src96, 0x60, "67d630 fold source")
    val padded = src96 + ByteArray(0x60)
    val head = padded.copyOfRange(0, 0x60)
    val first = vm67cc18(firstMagic, head, head, tables)
    var out = first.copyOfRange(0, 6)
    for (offset in 6 until 0x60 step 6) {
        val src = padded.copyOfRange(offset, offset + 0x60)
        val chunk = vm67cc18(tailMagic, src, src, tables)
        out += chunk.copyOfRange(2, 6)
    }
    return out
}

/** Moves a block on by one plain byte. */
internal fun shift67dd7cRemainder(block66: ByteArray, tables: Libre3FirstPairTables): ByteArray {
    requireSize(block66, block66Size, "67dd7c remainder block")
    val shifted = bytesOf(0, 0, 0, 3) + block66.copyOfRange(0, 0x3e)
    return vm67cc18(0x42000001974uL, shifted, shifted, tables)
}

private fun packDF80(zeros: Int, marker: Int?, src: ByteArray, take: Int): ByteArray {
    requireSize(src, take, "67df80 pack")
    val head = ByteArray(zeros)
    val middle = if (marker == null) ByteArray(0) else bytesOf(marker)
    return head + middle + src.copyOfRange(0, take)
}

internal fun packDF80Zeros6Marker(marker: Int, src: ByteArray): ByteArray = packDF80(6, marker, src, 11)

internal fun packDF80Zeros5Marker6(src: ByteArray): ByteArray = packDF80(5, 6, src, 12)

internal fun packDF80Zeros11Marker5(src: ByteArray): ByteArray = packDF80(11, 5, src, 6)

internal fun packDF80Zeros12Marker3(src: ByteArray): ByteArray = packDF80(12, 3, src, 5)

internal fun packDF80Zeros8Zero6(src: ByteArray): ByteArray = packDF80(9, 6, src, 8)

internal fun packDF80Zeros2Marker6(src: ByteArray): ByteArray = packDF80(2, 6, src, 15)

internal fun packDF80Zeros14Marker1(src: ByteArray): ByteArray = packDF80(14, 1, src, 3)

internal fun packDF80Zeros9(src: ByteArray): ByteArray = packDF80(9, null, src, 9)

internal fun packDF80Zeros4Marker3(src: ByteArray): ByteArray = packDF80(4, 3, src, 13)
