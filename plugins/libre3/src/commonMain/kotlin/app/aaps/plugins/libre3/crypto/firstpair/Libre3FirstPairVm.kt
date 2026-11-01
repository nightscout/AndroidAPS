package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException

/*
 * The small machines that every step of the first pairing scheme is built from.
 *
 * Ported from the private `vm...` and `step...` helpers of LibreCRKit
 * `Crypto/FirstPairSourceSlice.swift` at pin `a86b92f`.
 *
 * Each machine walks a short program taken out of a shipped table, keeps a small running state,
 * and writes three bits per step. The "magic" number of a call packs, in one word, where the
 * program starts and how many steps of each kind to run.
 */

/** One step of the three bit machine. A null source means that side is not read this step. */
internal fun step(state: Int, src1: Int?, src2: Int?, prog: Int, tables: Libre3FirstPairTables): Int {
    var index = state and 0xf8
    if (src1 != null) index = index xor src1
    if (src2 != null) index = index or (src2 shl 8)
    index = index xor (prog shl 11)
    if (index < 0 || index >= tables.sbox19.size) {
        throw Libre3CryptoException("the first pairing table ${Libre3FirstPairTables.SBOX19} has nothing at offset $index")
    }
    return tables.sbox19.u8(index)
}

/** One step of the sixteen bit machine, with the state masked first. */
internal fun step16Masked(state: Int, src: Int, prog: Int, tables: Libre3FirstPairTables): Int {
    val byteOffset = (((state and 0xff8) xor src) shl 1) or (prog shl 13)
    return ttableBHalfword(byteOffset, tables)
}

/** One step of the sixteen bit machine, with the whole state. */
internal fun step16Full(state: Int, src: Int, prog: Int, tables: Libre3FirstPairTables): Int {
    val byteOffset = (prog shl 13) xor ((state xor src) shl 1)
    return ttableBHalfword(byteOffset, tables)
}

private fun ttableBHalfword(byteOffset: Int, tables: Libre3FirstPairTables): Int {
    if (byteOffset < 0 || byteOffset + 1 >= tables.ttableBExt.size) {
        throw Libre3CryptoException(
            "the first pairing table ${Libre3FirstPairTables.TTABLE_B_EXT} has nothing at offset $byteOffset"
        )
    }
    return tables.ttableBExt.u8(byteOffset) or (tables.ttableBExt.u8(byteOffset + 1) shl 8)
}

/**
 * The plain two source machine, used with three different programs.
 *
 * @param program the shipped table the steps are read from.
 * @param programName that table's file name, for a clear message when it is too short.
 */
private fun vmTwoSources(
    magic: ULong,
    src1: ByteArray,
    src2: ByteArray,
    program: ByteArray,
    programName: String,
    label: String,
    tables: Libre3FirstPairTables,
): ByteArray {
    val progOff = (magic and 0x3fffffuL).toInt()
    val count = ((magic shr 36) and 0x3fffuL).toInt()
    val tail = (magic shr 50).toInt()
    val total = count + tail
    val prog = checkedSlice(program, progOff, total, programName)
    requireSize(src1, count, "$label src1")
    requireSize(src2, total, "$label src2")

    var state = 0
    val out = ByteArray(total)
    for (i in 0 until count) {
        state = step(state, src1.u8(i), src2.u8(i), prog.u8(i), tables)
        out[i] = (state and 7).toByte()
    }
    for (i in 0 until tail) {
        val pos = count + i
        state = step(state, null, src2.u8(pos), prog.u8(pos), tables)
        out[pos] = (state and 7).toByte()
    }
    return out
}

internal fun vm64e2b8(magic: ULong, src1: ByteArray, src2: ByteArray, tables: Libre3FirstPairTables): ByteArray =
    vmTwoSources(magic, src1, src2, tables.prog64e2b8, Libre3FirstPairTables.PROG_64E2B8, "vm64e2b8", tables)

internal fun vm638840(magic: ULong, src1: ByteArray, src2: ByteArray, tables: Libre3FirstPairTables): ByteArray =
    vmTwoSources(magic, src1, src2, tables.prog638840, Libre3FirstPairTables.PROG_638840, "vm638840", tables)

internal fun vm67cc18(magic: ULong, src1: ByteArray, src2: ByteArray, tables: Libre3FirstPairTables): ByteArray =
    vmTwoSources(magic, src1, src2, tables.prog67cc18, Libre3FirstPairTables.PROG_67CC18, "vm67cc18", tables)

/**
 * The two source machine with a warm up run, used with two different programs.
 *
 * The warm up steps only move the state on. Nothing is written for them.
 */
private fun vmPrimedTwoSources(
    magic: ULong,
    src1: ByteArray,
    src2: ByteArray,
    program: ByteArray,
    programName: String,
    label: String,
    tables: Libre3FirstPairTables,
): ByteArray {
    val progOff = (magic and 0x3fffffuL).toInt()
    val primer = ((magic shr 22) and 0x3fffuL).toInt()
    val count = ((magic shr 36) and 0x3fffuL).toInt()
    val tail = (magic shr 50).toInt()
    val totalProg = primer + count + tail
    val prog = checkedSlice(program, progOff, totalProg, programName)
    requireSize(src1, primer + count, "$label src1")
    requireSize(src2, primer + count, "$label src2")

    var state = 0
    for (i in 0 until primer) {
        state = step(state, src1.u8(i), src2.u8(i), prog.u8(i), tables)
    }

    val out = ByteArray(count + tail)
    for (i in 0 until count) {
        val srcPos = primer + i
        state = step(state, src1.u8(srcPos), src2.u8(srcPos), prog.u8(srcPos), tables)
        out[i] = (state and 7).toByte()
    }
    for (i in 0 until tail) {
        val progPos = primer + count + i
        state = step(state, null, null, prog.u8(progPos), tables)
        out[count + i] = (state and 7).toByte()
    }
    return out
}

internal fun vm6420d8(magic: ULong, src1: ByteArray, src2: ByteArray, tables: Libre3FirstPairTables): ByteArray =
    vmPrimedTwoSources(magic, src1, src2, tables.prog638840, Libre3FirstPairTables.PROG_638840, "vm6420d8", tables)

internal fun vm67cecc(magic: ULong, src1: ByteArray, src2: ByteArray, tables: Libre3FirstPairTables): ByteArray =
    vmPrimedTwoSources(magic, src1, src2, tables.prog67cc18, Libre3FirstPairTables.PROG_67CC18, "vm67cecc", tables)

/**
 * The one source sixteen bit machine, used with two different programs.
 *
 * The last three steps read the source again from its third byte. That is not a slip in the port:
 * the original does the same, and the whole answer changes if it is "corrected".
 */
private fun vmSingleSource16(
    magic: ULong,
    src: ByteArray,
    program: ByteArray,
    programName: String,
    label: String,
    requirePrimerAtLeastTwo: Boolean,
    tables: Libre3FirstPairTables,
): ByteArray {
    val progOff = (magic and 0x3fffffuL).toInt()
    val primer = ((magic shr 22) and 0x3fffuL).toInt()
    val count = ((magic shr 36) and 0x3fffuL).toInt()
    if (requirePrimerAtLeastTwo && primer < 2) {
        throw Libre3CryptoException("the $label warm up must be at least two steps, not $primer")
    }
    val totalProg = primer + count + 3
    val prog = checkedSlice(program, progOff, totalProg, programName)
    requireSize(src, maxOf(primer + count, 5), "$label src")

    var state = 0
    for (i in 0 until primer) {
        state = step16Masked(state, src.u8(i), prog.u8(i), tables)
    }

    val out = ByteArray(count + 3)
    for (i in 0 until count) {
        val srcPos = primer + i
        state = step16Masked(state, src.u8(srcPos), prog.u8(srcPos), tables)
        out[i] = (state and 7).toByte()
    }

    val tailProg = primer + count
    for (i in 0 until 3) {
        state = step16Full(state, src.u8(2 + i), prog.u8(tailProg + i), tables)
        out[count + i] = (state and 7).toByte()
    }
    return out
}

internal fun vm641fcc(magic: ULong, src: ByteArray, tables: Libre3FirstPairTables): ByteArray =
    vmSingleSource16(
        magic, src, tables.prog638840, Libre3FirstPairTables.PROG_638840, "vm641fcc",
        requirePrimerAtLeastTwo = true, tables = tables,
    )

internal fun vm67d524(magic: ULong, src: ByteArray, tables: Libre3FirstPairTables): ByteArray =
    vmSingleSource16(
        magic, src, tables.prog67cc18, Libre3FirstPairTables.PROG_67CC18, "vm67d524",
        requirePrimerAtLeastTwo = false, tables = tables,
    )

/** The fixed length machine of the 66 byte blocks. */
internal fun vm67076c(magic: ULong, src1: ByteArray, src2: ByteArray, tables: Libre3FirstPairTables): ByteArray {
    val progOff = (magic and 0x3fffffuL).toInt()
    val prog = checkedSlice(tables.prog67076c, progOff, block66Size, Libre3FirstPairTables.PROG_67076C)
    requireSize(src1, block66Size, "vm67076c src1")
    requireSize(src2, block66Size, "vm67076c src2")

    var state = 0
    val out = ByteArray(block66Size)
    for (i in 0 until block66Size) {
        state = step(state, src1.u8(i), src2.u8(i), prog.u8(i), tables)
        out[i] = (state and 7).toByte()
    }
    return out
}

/** The machine with a fixed opening, used on the 130 byte scratch area. */
internal fun vm64e17c(src0: ByteArray, src1: ByteArray, tables: Libre3FirstPairTables): ByteArray {
    requireSize(src0, scratch130Size, "vm64e17c src0")
    requireSize(src1, scratch130Size, "vm64e17c src1")

    var state = step(0, src0.u8(0), src1.u8(0), 14, tables)
    for ((index, progByte) in intArrayOf(22, 9, 33).withIndex()) {
        val pos = index + 1
        state = step(state, src0.u8(pos), src1.u8(pos), progByte, tables)
    }

    val prog = checkedSlice(tables.prog64e2b8, vm64e17cProgOffset, vm64e17cProgLength, Libre3FirstPairTables.PROG_64E2B8)
    val out = ByteArray(scratch130Size)
    for (i in 0 until vm64e17cProgLength) {
        state = step(state, src0.u8(4 + i), src1.u8(4 + i), prog.u8(i), tables)
        out[i] = (state and 7).toByte()
    }
    for ((i, progByte) in intArrayOf(12, 17, 18, 27).withIndex()) {
        val pos = vm64e17cProgLength + i
        state = step(state, null, null, progByte, tables)
        out[pos] = (state and 7).toByte()
    }
    return out
}

/** Puts a 66 byte block into the 130 byte scratch area, at the place the machine expects. */
internal fun shiftedScratch(block66: ByteArray): ByteArray {
    val scratch = ByteArray(scratch130Size)
    scratch[0x3f] = 3
    for (i in 0 until block66Size) {
        scratch[0x40 + i] = block66[i]
    }
    return scratch
}

/** Turns a long word into 48 three bit values, through the length table. */
internal fun expandU64Trits(value: ULong, tableOffset: Int, tables: Libre3FirstPairTables): ByteArray {
    val out = ByteArray(48)
    var write = 0
    for (shift in 0 until 64 step 8) {
        val index = tableOffset + ((value shr shift) and 0xffuL).toInt() * 3
        val packed = checkedSlice(tables.finalLenTables, index, 3, Libre3FirstPairTables.FINAL_LEN_TABLES)
        for (byte in packed) {
            out[write++] = (byte.toInt() and 7).toByte()
            out[write++] = ((byte.toInt() and 0xFF) shr 3).toByte()
        }
    }
    return out
}

/** Squeezes a 48 byte source down to 34 bytes, six at a time. */
internal fun fold48To34(firstMagic: ULong, tailMagic: ULong, src48: ByteArray, tables: Libre3FirstPairTables): ByteArray {
    requireSize(src48, 0x30, "679f48 length fold source")
    var out = vm67cc18(firstMagic, src48, src48, tables)
    for (offset in 6 until 0x30 step 6) {
        val src = src48.copyOfRange(offset, src48.size)
        val chunk = vm67cc18(tailMagic, src, src, tables)
        out += chunk.copyOfRange(2, 6)
    }
    return out
}
