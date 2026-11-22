package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException

/*
 * The caller of the `6388f0` stream: 118 rows, each one `642f60` call, one `6473d0` call and three
 * `64cd40` calls, all sharing one large scratch area.
 *
 * Ported from LibreCRKit `Crypto/FirstPairSourceSlice.swift` at pin `a86b92f`.
 *
 * The offsets into the scratch area are the shape of the original routine and mean nothing on
 * their own. Row 0 and row 59 start from the two point multiplies; every other row carries the
 * answers of the row before it.
 */

/** What one `64cd40` call needs. */
internal class Builder6388f0Caller64CallState(
    val arg0: ByteArray,
    val scalar: ULong,
    val x2Workspace: ByteArray,
    val x3Preimage: ByteArray,
    val stackWindow: ByteArray,
)

/** What one `64cd40` call produced. */
internal class Builder6388f0Caller64Call(
    val arg0: ByteArray,
    val scalar: ULong,
    val x2Workspace: ByteArray,
    val x3Preimage: ByteArray,
    val stackWindow: ByteArray,
    val output: ByteArray,
)

/** Everything one row of the caller produced. */
internal class Builder6388f0SeededCaller64Row(
    val index: Int,
    val current642f60: Builder6388f0Next642f60Inputs,
    val preimages: Builder6473d0OutputPreimages,
    val after642f60: Builder642f60Result,
    val after6473d0: Builder6473d0Result,
    val minimalStack20: ByteArray,
    val first64cd40: Builder6388f0Caller64Call,
    val second64cd40: Builder6388f0Caller64Call,
    val third64cd40: Builder6388f0Caller64Call,
    val next642f60: Builder6388f0Next642f60Inputs,
)

/** One `63c278` schedule taken out of one row. */
internal class Builder6388f0Seeded63c278Stream(
    val rowIndex: Int,
    val arg0: ByteArray,
    val arg1: ByteArray,
    val arg2: ByteArray,
    val scalar: ULong,
    val scheduleWords: UIntArray,
)

/** The two schedules the answer is built from. */
internal class Builder6388f0Seeded63c278Schedules(
    val first: Builder6388f0Seeded63c278Stream,
    val second: Builder6388f0Seeded63c278Stream,
)

/** The seven numbers of one 44 word convolution inside the caller's scratch area. */
internal class Libre3Convolution44Constants(
    val countMul: ULong,
    val countAdd: ULong,
    val productMul: ULong,
    val bPrefixMul: ULong,
    val aPrefixMul: ULong,
    val finalMul: ULong,
    val finalAdd: ULong,
)

private fun checkedReplace(target: ByteArray, at: Int, with: ByteArray) {
    if (at < 0 || at + with.size > target.size) {
        throw Libre3CryptoException("a first pairing write of ${with.size} bytes does not fit at $at")
    }
    with.copyInto(target, at)
}

@Suppress("LongParameterList")
private fun builder6388f0CallerStreamU64(
    word: UInt,
    wordMul: ULong,
    wordAdd: ULong,
    foldTable: Int,
    foldMul: ULong,
    mixMul: ULong,
    mixAdd: ULong,
    tables: Libre3FirstPairTables,
): ULong {
    val folded = fold63c278(word.toULong() * wordMul + wordAdd, foldTable, 8, tables)
    return folded * foldMul + word.toULong() * mixMul + mixAdd
}

/** The same, but the fold starts on the product, before the addend joins in. */
@Suppress("LongParameterList")
private fun builder6388f0CallerStreamU64FirstNibbleBeforeAdd(
    word: UInt,
    wordMul: ULong,
    wordAdd: ULong,
    foldTable: Int,
    foldMul: ULong,
    mixMul: ULong,
    mixAdd: ULong,
    tables: Libre3FirstPairTables,
): ULong {
    val folded = fold63c278FirstNibbleBeforeAdd(word.toULong() * wordMul, wordAdd, foldTable, tables)
    return folded * foldMul + word.toULong() * mixMul + mixAdd
}

@Suppress("LongParameterList")
private fun builder6388f0Convolution44(
    stack: ByteArray,
    aVecOffset: Int,
    aPrefixOffset: Int,
    bVecOffset: Int,
    bPrefixOffset: Int,
    outOffset: Int,
    constants: Libre3Convolution44Constants,
) {
    val aVec = ULongArray(builder63c278VectorWords) { readUInt64LE(stack, aVecOffset + it * 8) }
    val aPrefix = ULongArray(builder63c278VectorWords) { readUInt64LE(stack, aPrefixOffset + it * 8) }
    val bVec = ULongArray(builder63c278VectorWords) { readUInt64LE(stack, bVecOffset + it * 8) }
    val bPrefix = ULongArray(builder63c278VectorWords) { readUInt64LE(stack, bPrefixOffset + it * 8) }

    for (index in 0 until builder64cd40WorkspaceWords) {
        val low = maxOf(0, index - (builder63c278VectorWords - 1))
        val high = minOf(index, builder63c278VectorWords - 1)
        if (low > high) {
            writeUInt64LE(constants.countAdd * constants.finalMul + constants.finalAdd, stack, outOffset + index * 8)
            continue
        }

        var productSum = 0uL
        for (bIndex in low..high) {
            productSum += aVec[index - bIndex] * bVec[bIndex]
        }

        var aSum = aPrefix[index - low]
        val previousAIndex = index - high - 1
        if (previousAIndex >= 0) aSum -= aPrefix[previousAIndex]

        var bSum = bPrefix[high]
        if (low > 0) bSum -= bPrefix[low - 1]

        var out = (high - low + 1).toULong() * constants.countMul + constants.countAdd
        out += productSum * constants.productMul
        out += bSum * constants.bPrefixMul
        out += aSum * constants.aPrefixMul
        out = out * constants.finalMul + constants.finalAdd
        writeUInt64LE(out, stack, outOffset + index * 8)
    }
}

private fun newCallerStack(contextSource: ByteArray, callerStack20: ByteArray, postVectors: Map<Int, ByteArray>): ByteArray {
    if (contextSource.size < 0x420) {
        throw Libre3CryptoException("the 6388f0 caller context must be at least 0x420 bytes, not ${contextSource.size}")
    }
    if (callerStack20.size < builder6473d0CallerStackPreimageBytes) {
        throw Libre3CryptoException(
            "the 6388f0 caller stack must be at least $builder6473d0CallerStackPreimageBytes bytes, not ${callerStack20.size}"
        )
    }
    val stack = ByteArray(builder6388f0CallerStackBytes)
    checkedReplace(stack, 0x230, contextSource)
    checkedReplace(stack, 0x3708, callerStack20)
    for ((offset, raw) in postVectors) {
        checkedReplace(stack, offset, raw)
    }
    return stack
}

private fun callStateFrom(stack: ByteArray, contextSource: ByteArray): Builder6388f0Caller64CallState =
    Builder6388f0Caller64CallState(
        arg0 = stack.copyOfRange(0x330, 0x388),
        scalar = readUInt64LE(contextSource, 0x418),
        x2Workspace = stack.copyOfRange(0x39c8, 0x3b28),
        x3Preimage = stack.copyOfRange(0x3b28, 0x3b80),
        stackWindow = stack.copyOfRange(0x3778, 0x42c8),
    )

/** The last step every call state ends with: join the two convolutions into the work area. */
private fun combineConvolutions(stack: ByteArray, mixMul: ULong, mixAdd: ULong, secondMul: ULong) {
    for (offset in 0 until 0x160 step 0x10) {
        val firstA = readUInt64LE(stack, 0x3ce0 + offset)
        val secondA = readUInt64LE(stack, 0x3ce0 + offset + 8)
        val firstB = readUInt64LE(stack, 0x3b80 + offset)
        val secondB = readUInt64LE(stack, 0x3b80 + offset + 8)
        writeUInt64LE(firstA * mixMul + mixAdd + firstB * secondMul, stack, 0x39c8 + offset)
        writeUInt64LE(secondA * mixMul + mixAdd + secondB * secondMul, stack, 0x39c8 + offset + 8)
    }
}

private fun requireEntryIndex(entryIndex: Int) {
    if (entryIndex < 0) {
        throw Libre3CryptoException("a 6388f0 row index cannot be $entryIndex")
    }
}

@Suppress("LongMethod")
internal fun builder6388f0First64cd40CallState(
    contextSource: ByteArray,
    callerStack20: ByteArray,
    postVectors: Map<Int, ByteArray>,
    entryIndex: Int,
): Builder6388f0Caller64CallState {
    requireEntryIndex(entryIndex)
    val tables = Libre3FirstPairTables.get()
    val stack = newCallerStack(contextSource, callerStack20, postVectors)

    val loopSlot = entryIndex % builder6388f0CallerLoopTableRows
    val loopCounter = builder6388f0CallerLoopTableRows - 1 - loopSlot
    val pointerDelta = loopSlot * builder6388f0CallerLoopRowBytes

    val aMul = 0x5025a2599f75877fuL
    val aAdd = 0x4d8a8810a4bbc5a3uL
    val bMul = 0x23c3d48d0602f787uL
    val bAdd = 0x62917fc875cc9e6buL
    val firstMixMul = 0x6f8d70f401079e5buL
    val firstMixAdd = 0x31b3e556163432eduL
    val callerMixMul = 0x30eef2ed3a43a4f9uL
    val callerMixAdd = 0x92ef60a176c7d6c9uL
    val firstFoldMul = 0x838e88db00000000uL
    val callerFoldMul = 0x37fd608100000000uL

    var firstSrcWord = readUInt32LE(stack, 0x38c0) * 0xc938d835u + 0xe6fc451bu
    var callerWord = readUInt32LE(stack, 0x6f8 + loopCounter * 0x58) * 0xc955b06bu + 0x454427dfu
    val firstB = builder6388f0CallerStreamU64(firstSrcWord, aMul, aAdd, 0x300ef0, firstFoldMul, firstMixMul, firstMixAdd, tables)
    val firstA = builder6388f0CallerStreamU64(callerWord, bMul, bAdd, 0x300f70, callerFoldMul, callerMixMul, callerMixAdd, tables)
    writeUInt64LE(firstB, stack, 0x3b80)
    writeUInt64LE(firstB, stack, 0x4130)
    writeUInt64LE(firstA, stack, 0x39c8)
    writeUInt64LE(firstA, stack, 0x4010)

    var prefixB = firstB
    for (index in 1 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        var word = readUInt32LE(stack, 0x38c0 + index * 4)
        word = word * u32TableWord63c278(0x112e28 + tableOffset, tables) + u32TableWord63c278(0x117268 + tableOffset, tables)
        word = word * 0x56d9f19bu + 0x64a9155bu
        val value = builder6388f0CallerStreamU64(word, aMul, aAdd, 0x300ef0, firstFoldMul, firstMixMul, firstMixAdd, tables)
        writeUInt64LE(value, stack, 0x3b80 + index * 8)
        prefixB += value
        writeUInt64LE(prefixB, stack, 0x4130 + index * 8)
    }

    var prefixA = firstA
    var callerStream = 0x1aec - pointerDelta
    for (index in 0 until builder63c278VectorWords - 1) {
        val tableOffset = ((index + 1) and 7) * 4
        var word = readUInt32LE(stack, callerStream + index * 4)
        word = word * u32TableWord63c278(0x117288 + tableOffset, tables) + u32TableWord63c278(0x1205e8 + tableOffset, tables)
        word = word * 0x994a2aa3u + 0x7f433349u
        val value = builder6388f0CallerStreamU64(word, bMul, bAdd, 0x300f70, callerFoldMul, callerMixMul, callerMixAdd, tables)
        writeUInt64LE(value, stack, 0x39d0 + index * 8)
        prefixA += value
        writeUInt64LE(prefixA, stack, 0x4018 + index * 8)
    }

    builder6388f0Convolution44(
        stack, 0x39c8, 0x4010, 0x3b80, 0x4130, 0x3ce0,
        Libre3Convolution44Constants(
            0x4079ef92755bf93auL, 0xb43c87132a6e84d1uL, 0x129a56bce90af833uL,
            0x8de93a973ee9c82buL, 0xb3c2bc6591a8beaauL, 0xe6bf6d3dc98f10f7uL, 0x632718706bc72397uL,
        ),
    )

    val cMul = 0x877a8a4a5f3b0f49uL
    val cAdd = 0xa24f4a31979cc775uL
    val dMul = 0xddfbefdc018359d5uL
    val dAdd = 0x7f589737aa46bdd5uL
    val cMixMul = 0x5a83f7862436b279uL
    val cMixAdd = 0x77cf4bf823a845d0uL
    val dMixMul = 0x80c881a27926eee7uL
    val dMixAdd = 0xeabaf4ef841c8c86uL
    val cFoldMul = 0xde34e64f00000000uL
    val dFoldMul = 0x438d983500000000uL

    firstSrcWord = readUInt32LE(stack, 0x37b8) * 0xff9582fdu + 0xfc52cb23u
    callerWord = readUInt32LE(stack, 0x1b40 + loopCounter * 0x58) * 0xad09fb4bu + 0x4d566e95u
    val firstC = builder6388f0CallerStreamU64(firstSrcWord, cMul, cAdd, 0x300ff0, cFoldMul, cMixMul, cMixAdd, tables)
    val firstD = builder6388f0CallerStreamU64(callerWord, dMul, dAdd, 0x301070, dFoldMul, dMixMul, dMixAdd, tables)
    writeUInt64LE(firstC, stack, 0x39c8)
    writeUInt64LE(firstC, stack, 0x4010)
    writeUInt64LE(firstD, stack, 0x4130)
    writeUInt64LE(firstD, stack, 0x3ef0)

    var prefixC = firstC
    for (index in 1 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        var word = readUInt32LE(stack, 0x37b8 + index * 4)
        word = word * u32TableWord63c278(0x1218c8 + tableOffset, tables) + u32TableWord63c278(0x1221e8 + tableOffset, tables)
        word = word * 0x2c6e5d55u + 0x63f5202du
        val value = builder6388f0CallerStreamU64(word, cMul, cAdd, 0x300ff0, cFoldMul, cMixMul, cMixAdd, tables)
        writeUInt64LE(value, stack, 0x39c8 + index * 8)
        prefixC += value
        writeUInt64LE(prefixC, stack, 0x4010 + index * 8)
    }

    var prefixD = firstD
    callerStream = 0x2f34 - pointerDelta
    for (index in 0 until builder63c278VectorWords - 1) {
        val tableOffset = ((index + 1) and 7) * 4
        var word = readUInt32LE(stack, callerStream + index * 4)
        word = word * u32TableWord63c278(0x119708 + tableOffset, tables) + u32TableWord63c278(0x120608 + tableOffset, tables)
        word = word * 0x206cd1f3u + 0x867e396du
        val value = builder6388f0CallerStreamU64(word, dMul, dAdd, 0x301070, dFoldMul, dMixMul, dMixAdd, tables)
        writeUInt64LE(value, stack, 0x4138 + index * 8)
        prefixD += value
        writeUInt64LE(prefixD, stack, 0x3ef8 + index * 8)
    }

    builder6388f0Convolution44(
        stack, 0x4130, 0x3ef0, 0x39c8, 0x4010, 0x3b80,
        Libre3Convolution44Constants(
            0xd16513f43f99d2c0uL, 0x5bdc507e86f7d211uL, 0x49fd76daa54ce93buL,
            0x4a15e654a01bea9euL, 0xe49b61c39c833ce0uL, 0x9054b9a41de45a5buL, 0x9b016b93e5b24765uL,
        ),
    )

    combineConvolutions(stack, 0xb8bc9deccc0ade89uL, 0xc46ffd16f1b1756fuL, 0x9c308b62a744c677uL)
    return callStateFrom(stack, contextSource)
}

@Suppress("LongMethod")
internal fun builder6388f0Second64cd40CallState(
    contextSource: ByteArray,
    callerStack20: ByteArray,
    postVectors: Map<Int, ByteArray>,
    first64cd40Output: ByteArray,
    entryIndex: Int,
): Builder6388f0Caller64CallState {
    requireEntryIndex(entryIndex)
    if (first64cd40Output.size < builder63c278VectorBytes) {
        throw Libre3CryptoException("the first 64cd40 answer must be at least $builder63c278VectorBytes bytes")
    }
    val tables = Libre3FirstPairTables.get()
    val stack = newCallerStack(contextSource, callerStack20, postVectors)
    checkedReplace(stack, 0x3b28, first64cd40Output.copyOfRange(0, builder63c278VectorBytes))

    val loopSlot = entryIndex % builder6388f0CallerLoopTableRows
    val loopCounter = builder6388f0CallerLoopTableRows - 1 - loopSlot
    val pointerDelta = loopSlot * builder6388f0CallerLoopRowBytes
    writeUInt32LE(readUInt32LE(stack, 0x6f8 + loopCounter * 0x58), stack, 0x44)
    writeUInt32LE(readUInt32LE(stack, 0x1b40 + loopCounter * 0x58), stack, 0x40)

    for (index in 0 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        val word = readUInt32LE(stack, 0x3b28 + index * 4) * foldTableU32Word63c278(0x300ff0 + tableOffset, tables) +
            u32TableWord63c278(0x1184c8 + tableOffset, tables)
        writeUInt32LE(word, stack, 0x154 + index * 4)
    }

    var callerWord = readUInt32LE(stack, 0x44) * 0xa2e10181u + 0xd0b84b4au
    var postWord = readUInt32LE(stack, 0x3868) * 0xea9bc62bu + 0x295fb23du
    var callerWordMul = 0x67eb8e340bf68edduL
    var callerWordAdd = 0x7194bb146d6a6c98uL
    var callerMixMul = 0x412cb68339b36b19uL
    var callerMixAdd = 0xe7c0e7165633369buL
    var callerFoldMul = 0xf883fc9300000000uL
    var postWordMul = 0x1e34bf9de310fbcbuL
    var postWordAdd = 0xe80eb386bd2c7669uL
    var postMixMul = 0x3f2d22f0405cf24fuL
    var postMixAdd = 0x316c36e4735ae9bcuL
    var postFoldMul = 0xa4e2a4f300000000uL

    var callerValue = builder6388f0CallerStreamU64(
        callerWord, callerWordMul, callerWordAdd, 0x3013f0, callerFoldMul, callerMixMul, callerMixAdd, tables,
    )
    var postValue = builder6388f0CallerStreamU64(
        postWord, postWordMul, postWordAdd, 0x301370, postFoldMul, postMixMul, postMixAdd, tables,
    )
    writeUInt64LE(callerValue, stack, 0x4130)
    writeUInt64LE(callerValue, stack, 0x3ef0)
    writeUInt64LE(postValue, stack, 0x3b80)
    writeUInt64LE(postValue, stack, 0x4010)

    var prefixPost = postValue
    for (index in 1 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        var word = readUInt32LE(stack, 0x3868 + index * 4)
        word = word * u32TableWord63c278(0x11c3c8 + tableOffset, tables) + u32TableWord63c278(0x11d4c8 + tableOffset, tables)
        word = word * 0xc99643bbu + 0xac352509u
        val value = builder6388f0CallerStreamU64(
            word, postWordMul, postWordAdd, 0x301370, postFoldMul, postMixMul, postMixAdd, tables,
        )
        writeUInt64LE(value, stack, 0x3b80 + index * 8)
        prefixPost += value
        writeUInt64LE(prefixPost, stack, 0x4010 + index * 8)
    }

    var prefixCaller = callerValue
    var callerStream = 0x1aec - pointerDelta
    for (index in 0 until builder63c278VectorWords - 1) {
        val tableOffset = ((index + 1) and 7) * 4
        var word = readUInt32LE(stack, callerStream + index * 4)
        word = word * u32TableWord63c278(0x1125e8 + tableOffset, tables) + u32TableWord63c278(0x118f08 + tableOffset, tables)
        word = word * 0x31bbe0b7u + 0x3fe25e18u
        val value = builder6388f0CallerStreamU64(
            word, callerWordMul, callerWordAdd, 0x3013f0, callerFoldMul, callerMixMul, callerMixAdd, tables,
        )
        writeUInt64LE(value, stack, 0x4138 + index * 8)
        prefixCaller += value
        writeUInt64LE(prefixCaller, stack, 0x3ef8 + index * 8)
    }

    builder6388f0Convolution44(
        stack, 0x4130, 0x3ef0, 0x3b80, 0x4010, 0x3ce0,
        Libre3Convolution44Constants(
            0x31387af6df27bc34uL, 0x5e7eda1d7e652662uL, 0xdc67c7dbf68b7273uL,
            0x8f98298f0679fa22uL, 0x662a1479caab56ceuL, 0x26efbb4b51cdc6b5uL, 0xc5b3a8b6b472e5d3uL,
        ),
    )

    callerWord = readUInt32LE(stack, 0x40) * 0x63dc1441u + 0xda7427c7u
    postWord = readUInt32LE(stack, 0x3760) * 0xe609bd27u + 0x93c1ccd4u
    callerWordMul = 0xdca944fb28ac47f7uL
    callerWordAdd = 0xd57d3e716bf087fcuL
    callerMixMul = 0xb241122944abe41duL
    callerMixAdd = 0xeb98df0f724a8bc5uL
    callerFoldMul = 0xd584887500000000uL
    postWordMul = 0x86835750d0f2d33duL
    postWordAdd = 0x93d6710e2805c2cduL
    postMixMul = 0x37836d2c6f35aeafuL
    postMixAdd = 0xabfb5017ca2ca427uL
    postFoldMul = 0x749f87a500000000uL

    callerValue = builder6388f0CallerStreamU64(
        callerWord, callerWordMul, callerWordAdd, 0x3014f0, callerFoldMul, callerMixMul, callerMixAdd, tables,
    )
    postValue = builder6388f0CallerStreamU64(
        postWord, postWordMul, postWordAdd, 0x301470, postFoldMul, postMixMul, postMixAdd, tables,
    )
    writeUInt64LE(callerValue, stack, 0x4010)
    writeUInt64LE(callerValue, stack, 0x3e40)
    writeUInt64LE(postValue, stack, 0x4130)
    writeUInt64LE(postValue, stack, 0x3ef0)

    prefixPost = postValue
    for (index in 1 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        var word = readUInt32LE(stack, 0x3760 + index * 4)
        word = word * u32TableWord63c278(0x11e588 + tableOffset, tables) + u32TableWord63c278(0x122a68 + tableOffset, tables)
        word = word * 0x712dee2fu + 0xecb470d1u
        val value = builder6388f0CallerStreamU64(
            word, postWordMul, postWordAdd, 0x301470, postFoldMul, postMixMul, postMixAdd, tables,
        )
        writeUInt64LE(value, stack, 0x4130 + index * 8)
        prefixPost += value
        writeUInt64LE(prefixPost, stack, 0x3ef0 + index * 8)
    }

    prefixCaller = callerValue
    callerStream = 0x2f34 - pointerDelta
    for (index in 0 until builder63c278VectorWords - 1) {
        val tableOffset = ((index + 1) and 7) * 4
        var word = readUInt32LE(stack, callerStream + index * 4)
        word = word * u32TableWord63c278(0x120628 + tableOffset, tables) + u32TableWord63c278(0x122a88 + tableOffset, tables)
        word = word * 0x26f75f39u + 0x1c4c83fcu
        val value = builder6388f0CallerStreamU64(
            word, callerWordMul, callerWordAdd, 0x3014f0, callerFoldMul, callerMixMul, callerMixAdd, tables,
        )
        writeUInt64LE(value, stack, 0x4018 + index * 8)
        prefixCaller += value
        writeUInt64LE(prefixCaller, stack, 0x3e48 + index * 8)
    }

    builder6388f0Convolution44(
        stack, 0x4010, 0x3e40, 0x4130, 0x3ef0, 0x3b80,
        Libre3Convolution44Constants(
            0xf8f0e2182e743120uL, 0x2ea75adafa845934uL, 0x49eba04bc8aba147uL,
            0x2ae8b5655df9be65uL, 0xcc8eaf52163f5260uL, 0xfee73c0f7de3fa41uL, 0x7572d2a401ed3b6auL,
        ),
    )

    combineConvolutions(stack, 0xf6ebf5f38b50e6e5uL, 0x08494646ffdad49auL, 0x26f0954510cb129fuL)
    return callStateFrom(stack, contextSource)
}

@Suppress("LongMethod")
internal fun builder6388f0Third64cd40CallState(
    contextSource: ByteArray,
    callerStack20: ByteArray,
    postVectors: Map<Int, ByteArray>,
    second64cd40Output: ByteArray,
    entryIndex: Int,
): Builder6388f0Caller64CallState {
    requireEntryIndex(entryIndex)
    if (second64cd40Output.size < builder63c278VectorBytes) {
        throw Libre3CryptoException("the second 64cd40 answer must be at least $builder63c278VectorBytes bytes")
    }
    val tables = Libre3FirstPairTables.get()
    val stack = newCallerStack(contextSource, callerStack20, postVectors)
    checkedReplace(stack, 0x3b28, second64cd40Output.copyOfRange(0, builder63c278VectorBytes))

    val loopSlot = entryIndex % builder6388f0CallerLoopTableRows
    val loopCounter = builder6388f0CallerLoopTableRows - 1 - loopSlot
    val pointerDelta = loopSlot * builder6388f0CallerLoopRowBytes
    writeUInt32LE(readUInt32LE(stack, 0x6f8 + loopCounter * 0x58), stack, 0x44)
    writeUInt32LE(readUInt32LE(stack, 0x1b40 + loopCounter * 0x58), stack, 0x40)

    for (index in 0 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        val word = readUInt32LE(stack, 0x3b28 + index * 4) * u32TableWord63c278(0x114948 + tableOffset, tables) +
            u32TableWord63c278(0x1184e8 + tableOffset, tables)
        writeUInt32LE(word, stack, 0xfc + index * 4)
    }

    var callerWord = readUInt32LE(stack, 0x44) * 0x52b341e9u + 0x8fe4704au
    var postWord = readUInt32LE(stack, 0x3810) * 0xb1c3b83du + 0x4ac96e8du
    var callerWordMul = 0xc601c25eb7863abbuL
    var callerWordAdd = 0x8a9ac40e5bfb780duL
    var callerMixMul = 0xe76d920aeec9873duL
    var callerMixAdd = 0x63d22c2ddb82d5a1uL
    var callerFoldMul = 0x9897ad9900000000uL
    var postWordMul = 0x16aacea9a72f0c45uL
    var postWordAdd = 0xe952bbc97872445cuL
    var postMixMul = 0x9c887c5c45db1a3buL
    var postMixAdd = 0x6536302ead1b2169uL
    var postFoldMul = 0x7db1cb8100000000uL

    var callerValue = builder6388f0CallerStreamU64(
        callerWord, callerWordMul, callerWordAdd, 0x3015f0, callerFoldMul, callerMixMul, callerMixAdd, tables,
    )
    var postValue = builder6388f0CallerStreamU64(
        postWord, postWordMul, postWordAdd, 0x301570, postFoldMul, postMixMul, postMixAdd, tables,
    )
    writeUInt64LE(callerValue, stack, 0x4130)
    writeUInt64LE(callerValue, stack, 0x3ef0)
    writeUInt64LE(postValue, stack, 0x3b80)
    writeUInt64LE(postValue, stack, 0x4010)

    var prefixPost = postValue
    for (index in 1 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        var word = readUInt32LE(stack, 0x3810 + index * 4)
        word = word * u32TableWord63c278(0x120648 + tableOffset, tables) + u32TableWord63c278(0x122208 + tableOffset, tables)
        word = word * 0xad44242bu + 0x28772583u
        val value = builder6388f0CallerStreamU64(
            word, postWordMul, postWordAdd, 0x301570, postFoldMul, postMixMul, postMixAdd, tables,
        )
        writeUInt64LE(value, stack, 0x3b80 + index * 8)
        prefixPost += value
        writeUInt64LE(prefixPost, stack, 0x4010 + index * 8)
    }

    var prefixCaller = callerValue
    var callerStream = 0x1aec - pointerDelta
    for (index in 0 until builder63c278VectorWords - 1) {
        val tableOffset = ((index + 1) and 7) * 4
        var word = readUInt32LE(stack, callerStream + index * 4)
        word = word * u32TableWord63c278(0x119728 + tableOffset, tables) + u32TableWord63c278(0x1218e8 + tableOffset, tables)
        word = word * 0xb7911189u + 0x50798488u
        val value = builder6388f0CallerStreamU64(
            word, callerWordMul, callerWordAdd, 0x3015f0, callerFoldMul, callerMixMul, callerMixAdd, tables,
        )
        writeUInt64LE(value, stack, 0x4138 + index * 8)
        prefixCaller += value
        writeUInt64LE(prefixCaller, stack, 0x3ef8 + index * 8)
    }

    builder6388f0Convolution44(
        stack, 0x4130, 0x3ef0, 0x3b80, 0x4010, 0x3ce0,
        Libre3Convolution44Constants(
            0x02949f32b4f07fd8uL, 0xbea7dd815afcbcc1uL, 0x7611b37d7c8f4475uL,
            0xb4709b2cff94859cuL, 0xde2dc8d44e8f4662uL, 0x3b985d4b603f64d9uL, 0x9a4acc2ae823c739uL,
        ),
    )

    callerWord = readUInt32LE(stack, 0x40) * 0x10aa89f9u + 0x5f38d605u
    postWord = readUInt32LE(stack, 0x3708) * 0x49dc9b53u + 0x4de59f05u
    callerWordMul = 0x8b7f3e328f16058buL
    callerWordAdd = 0xc7947e77ef912670uL
    callerMixMul = 0x272b96c7a7cb8ff9uL
    callerMixAdd = 0x7dff808a85cebcacuL
    callerFoldMul = 0xbcbba6f500000000uL
    postWordMul = 0x4cea0abb01866b97uL
    postWordAdd = 0xd8436bdaf28ca051uL
    postMixMul = 0x4c0e84f7d0089f9buL
    postMixAdd = 0xb5d5f7a06307c689uL
    postFoldMul = 0x832f036300000000uL

    callerValue = builder6388f0CallerStreamU64FirstNibbleBeforeAdd(
        callerWord, callerWordMul, callerWordAdd, 0x3016f0, callerFoldMul, callerMixMul, callerMixAdd, tables,
    )
    postValue = builder6388f0CallerStreamU64(
        postWord, postWordMul, postWordAdd, 0x301670, postFoldMul, postMixMul, postMixAdd, tables,
    )
    writeUInt64LE(callerValue, stack, 0x4010)
    writeUInt64LE(callerValue, stack, 0x3e40)
    writeUInt64LE(postValue, stack, 0x4130)
    writeUInt64LE(postValue, stack, 0x3ef0)

    prefixPost = postValue
    for (index in 1 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        var word = readUInt32LE(stack, 0x3708 + index * 4)
        word = word * u32TableWord63c278(0x115f28 + tableOffset, tables) + u32TableWord63c278(0x118508 + tableOffset, tables)
        word = word * 0x68c9b103u + 0x45ce4a73u
        val value = builder6388f0CallerStreamU64(
            word, postWordMul, postWordAdd, 0x301670, postFoldMul, postMixMul, postMixAdd, tables,
        )
        writeUInt64LE(value, stack, 0x4130 + index * 8)
        prefixPost += value
        writeUInt64LE(prefixPost, stack, 0x3ef0 + index * 8)
    }

    prefixCaller = callerValue
    callerStream = 0x2f34 - pointerDelta
    for (index in 0 until builder63c278VectorWords - 1) {
        val tableOffset = ((index + 1) and 7) * 4
        var word = readUInt32LE(stack, callerStream + index * 4)
        word = word * u32TableWord63c278(0x115468 + tableOffset, tables) + u32TableWord63c278(0x118528 + tableOffset, tables)
        word = word * 0xf6c4e17du + 0x0882a9cfu
        val value = builder6388f0CallerStreamU64FirstNibbleBeforeAdd(
            word, callerWordMul, callerWordAdd, 0x3016f0, callerFoldMul, callerMixMul, callerMixAdd, tables,
        )
        writeUInt64LE(value, stack, 0x4018 + index * 8)
        prefixCaller += value
        writeUInt64LE(prefixCaller, stack, 0x3e48 + index * 8)
    }

    builder6388f0Convolution44(
        stack, 0x4010, 0x3e40, 0x4130, 0x3ef0, 0x3b80,
        Libre3Convolution44Constants(
            0x638b8c646690163auL, 0x96f60e030a9158deuL, 0x59ee1b3f304ea615uL,
            0x3a17e3aa11527f5euL, 0xcf4a3bc55798adefuL, 0x15bcf2fe3c06e5afuL, 0xcc8339ef4cba9cd0uL,
        ),
    )

    combineConvolutions(stack, 0x929296759110b0a3uL, 0x0ede13af97827959uL, 0x70b7f6eaabceff57uL)
    return callStateFrom(stack, contextSource)
}

internal fun builder6388f0Call64Call(state: Builder6388f0Caller64CallState): Builder6388f0Caller64Call =
    Builder6388f0Caller64Call(
        state.arg0, state.scalar, state.x2Workspace, state.x3Preimage, state.stackWindow,
        packUInt32LE(builder64cd40OutputWords(state.arg0, state.scalar, state.x2Workspace)),
    )

/** One row of the caller: one `642f60` call, one `6473d0` call, three `64cd40` calls. */
internal fun builder6388f0SeededCaller64Row(
    index: Int,
    current642f60: Builder6388f0Next642f60Inputs,
    preimages: Builder6473d0OutputPreimages,
    contextSource: ByteArray? = null,
): Builder6388f0SeededCaller64Row {
    val context = contextSource ?: builder6388f0CallerContextFromBundle()

    val after642f60 = builder642f60Outputs(current642f60.x0, current642f60.x1, current642f60.x2, context)
    val after6473d0 = builder6473d0Outputs(
        after642f60.out0, after642f60.out1, after642f60.out2, context, preimages.out0, preimages.out1,
    )
    val minimalStack20 = builder6473d0MinimalStack20FromPreimages(preimages)
    val postVectors = builder6473d0PostVectors(after6473d0)

    val first64cd40 = builder6388f0Call64Call(
        builder6388f0First64cd40CallState(context, minimalStack20, postVectors, index)
    )
    val second64cd40 = builder6388f0Call64Call(
        builder6388f0Second64cd40CallState(context, minimalStack20, postVectors, first64cd40.output, index)
    )
    val third64cd40 = builder6388f0Call64Call(
        builder6388f0Third64cd40CallState(context, minimalStack20, postVectors, second64cd40.output, index)
    )
    val next642f60 = builder6388f0Next642f60InputsFrom64cd40Outputs(
        first64cd40.output, second64cd40.output, third64cd40.output,
    )

    return Builder6388f0SeededCaller64Row(
        index, current642f60, preimages, after642f60, after6473d0, minimalStack20,
        first64cd40, second64cd40, third64cd40, next642f60,
    )
}

/**
 * All the rows of the caller.
 *
 * Row 0 and row 59 start from the two point multiplies. Every other row carries what the row
 * before it produced.
 */
internal fun builder6388f0SeededCaller64Rows(
    starts: Builder6388f0FirstPair642f60Starts,
    row0LowPreimages: Builder6473d0OutputPreimages,
    contextSource: ByteArray? = null,
    limit: Int = 118,
    x2Source: ByteArray? = null,
): List<Builder6388f0SeededCaller64Row> {
    val rows = ArrayList<Builder6388f0SeededCaller64Row>(limit)
    walkCaller64Rows(starts, row0LowPreimages, contextSource, limit, x2Source) { rows.add(it) }
    return rows
}

/**
 * Walks the rows of the caller and hands each one to [onRow] as it is made.
 *
 * Only two rows are ever needed for the answer, but every row has to be walked because each one
 * carries the one before it. Handing them out one at a time lets the driver keep only what it
 * needs: a whole run of 118 rows holds a few megabytes, which is worth avoiding on a phone.
 */
@Suppress("LongParameterList", "LongMethod")
private fun walkCaller64Rows(
    starts: Builder6388f0FirstPair642f60Starts,
    row0LowPreimages: Builder6473d0OutputPreimages,
    contextSource: ByteArray?,
    limit: Int,
    x2Source: ByteArray?,
    onRow: (Builder6388f0SeededCaller64Row) -> Unit,
) {
    if (limit < 0 || limit > builder6388f0FirstPairStreamRows) {
        throw Libre3CryptoException("the caller can run 0 to $builder6388f0FirstPairStreamRows rows, not $limit")
    }

    val context = contextSource ?: builder6388f0CallerContextFromBundle()

    val row0Out0 = builder6388f0RecoverStreamStartOut0SeedFrom642f60X0(starts.row0.x0)
    val row0Out1 = builder6388f0RecoverStreamStartOut1SeedFrom642f60X1(starts.row0.x1)
    val row59Out0 = builder6388f0RecoverStreamStartOut0SeedFrom642f60X0(starts.row59.x0)
    val row59Out1 = builder6388f0RecoverStreamStartOut1SeedFrom642f60X1(starts.row59.x1)

    val row0Start = builder6388f0StreamStart642f60Inputs(row0Out0, row0Out1, x2Source)
    val row59Start = builder6388f0StreamStart642f60Inputs(row59Out0, row59Out1, x2Source)

    var previous6473d0: Builder6473d0Result? = null
    var carried642f60: Builder6388f0Next642f60Inputs? = null
    var activeOut0Seed: ByteArray? = null
    var activeOut1Seed: ByteArray? = null

    for (index in 0 until limit) {
        val current642f60: Builder6388f0Next642f60Inputs
        val preimages: Builder6473d0OutputPreimages

        when {
            index == 0                                     -> {
                current642f60 = row0Start
                activeOut0Seed = row0Out0
                activeOut1Seed = row0Out1
                preimages = Builder6473d0OutputPreimages(
                    row0LowPreimages.out4, row0LowPreimages.out3, row0LowPreimages.out2, row0Out1, row0Out0,
                )
            }

            index == builder6388f0CallerLoopTableRows      -> {
                val previous = previous6473d0
                    ?: throw Libre3CryptoException("row $index has no earlier row to carry from")
                current642f60 = row59Start
                activeOut0Seed = row59Out0
                activeOut1Seed = row59Out1
                preimages = Builder6473d0OutputPreimages(
                    previous.out4, previous.out3, previous.out2, row59Out1, row59Out0,
                )
            }

            else                                           -> {
                val carried = carried642f60
                val previous = previous6473d0
                val out0Seed = activeOut0Seed
                val out1Seed = activeOut1Seed
                if (carried == null || previous == null || out0Seed == null || out1Seed == null) {
                    throw Libre3CryptoException("row $index has no earlier row to carry from")
                }
                current642f60 = carried
                preimages = Builder6473d0OutputPreimages(
                    previous.out4, previous.out3, previous.out2, out1Seed, out0Seed,
                )
            }
        }

        val row = builder6388f0SeededCaller64Row(index, current642f60, preimages, context)
        onRow(row)
        previous6473d0 = row.after6473d0
        carried642f60 = row.next642f60
    }
}

internal fun builder6388f0SeededCaller64RowsFromFirstPairStreamSeeds(
    seeds: Builder6388f0FirstPairStreamSeeds,
    contextSource: ByteArray? = null,
    limit: Int = 118,
    x2Source: ByteArray? = null,
): List<Builder6388f0SeededCaller64Row> {
    val starts = builder6388f0FirstPair642f60StreamStarts(seeds, x2Source)
    val row0LowPreimages = Builder6473d0OutputPreimages(
        seeds.row0Out4, seeds.row0Out3, seeds.row0Out2, seeds.row0Out1, seeds.row0Out0,
    )
    return builder6388f0SeededCaller64Rows(starts, row0LowPreimages, contextSource, limit, x2Source)
}

/** The two `63c278` schedules the answer is built from: the last row of each half. */
internal fun builder6388f0Seeded63c278SchedulesFromRows(
    rows: List<Builder6388f0SeededCaller64Row>,
    arg0: ByteArray = pre63c278Arg0Source,
    scalar: ULong = pre63c278Scalar,
): Builder6388f0Seeded63c278Schedules {
    if (rows.size < builder6388f0FirstPairStreamRows) {
        throw Libre3CryptoException("the caller must have run $builder6388f0FirstPairStreamRows rows, not ${rows.size}")
    }
    return builder6388f0Seeded63c278SchedulesFromTwoRows(
        rows[builder6388f0CallerLoopTableRows - 1],
        rows[builder6388f0FirstPairStreamRows - 1],
        arg0,
        scalar,
    )
}

/** The same, from the only two rows that are read, so a whole run need not be kept. */
internal fun builder6388f0Seeded63c278SchedulesFromTwoRows(
    firstRow: Builder6388f0SeededCaller64Row,
    secondRow: Builder6388f0SeededCaller64Row,
    arg0: ByteArray = pre63c278Arg0Source,
    scalar: ULong = pre63c278Scalar,
): Builder6388f0Seeded63c278Schedules {
    if (arg0.size < builder63c278VectorBytes) {
        throw Libre3CryptoException("the 63c278 arg0 must be at least $builder63c278VectorBytes bytes, not ${arg0.size}")
    }
    val arg0Prefix = arg0.copyOfRange(0, builder63c278VectorBytes)

    fun stream(row: Builder6388f0SeededCaller64Row): Builder6388f0Seeded63c278Stream {
        val arg1 = row.next642f60.x0
        val arg2 = row.next642f60.x2
        return Builder6388f0Seeded63c278Stream(
            row.index, arg0Prefix, arg1, arg2, scalar,
            builder63c278ScheduleWords(arg0Prefix, arg1, arg2, scalar),
        )
    }

    return Builder6388f0Seeded63c278Schedules(stream(firstRow), stream(secondRow))
}

internal fun deriveFrom6388f0SeededCaller64Rows(
    rows: List<Builder6388f0SeededCaller64Row>,
    arg0: ByteArray = pre63c278Arg0Source,
    scalar: ULong = pre63c278Scalar,
    src4: ByteArray = defaultSrc4(),
    offset: Int = 0,
    length: Int = 0x10,
): ByteArray {
    val schedules = builder6388f0Seeded63c278SchedulesFromRows(rows, arg0, scalar)
    return deriveFrom6388f0ScheduleLen32Streams(
        schedules.first.scheduleWords, schedules.second.scheduleWords, src4, offset, length,
    )
}

/**
 * The whole path from the eleven seeds of a first pairing to the sixty six byte source.
 *
 * The two rows the answer is built from are the last of each half. Every other row is walked and
 * then let go, so a pairing on a phone does not hold the whole run at once.
 */
internal fun deriveFrom6388f0FirstPairStreamSeeds(
    seeds: Builder6388f0FirstPairStreamSeeds,
    src4: ByteArray = defaultSrc4(),
    offset: Int = 0,
    length: Int = 0x10,
): ByteArray {
    val firstIndex = builder6388f0CallerLoopTableRows - 1
    val secondIndex = builder6388f0FirstPairStreamRows - 1
    var first: Builder6388f0SeededCaller64Row? = null
    var second: Builder6388f0SeededCaller64Row? = null

    val starts = builder6388f0FirstPair642f60StreamStarts(seeds)
    val row0LowPreimages = Builder6473d0OutputPreimages(
        seeds.row0Out4, seeds.row0Out3, seeds.row0Out2, seeds.row0Out1, seeds.row0Out0,
    )
    walkCaller64Rows(starts, row0LowPreimages, null, builder6388f0FirstPairStreamRows, null) { row ->
        if (row.index == firstIndex) first = row
        if (row.index == secondIndex) second = row
    }

    val firstRow = first ?: throw Libre3CryptoException("the caller never reached row $firstIndex")
    val secondRow = second ?: throw Libre3CryptoException("the caller never reached row $secondIndex")
    val schedules = builder6388f0Seeded63c278SchedulesFromTwoRows(firstRow, secondRow)
    return deriveFrom6388f0ScheduleLen32Streams(
        schedules.first.scheduleWords, schedules.second.scheduleWords, src4, offset, length,
    )
}
