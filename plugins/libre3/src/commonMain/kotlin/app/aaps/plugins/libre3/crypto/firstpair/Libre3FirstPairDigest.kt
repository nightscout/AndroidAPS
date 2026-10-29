package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException

/*
 * The digest of the first pairing scheme: the `679f48` context, the `df80` round function, and
 * the slices that are read out of a finished context.
 *
 * Ported from LibreCRKit `Crypto/FirstPairSourceSlice.swift` at pin `a86b92f`. Names and numbers
 * are kept as they are in the Swift, because they are addresses in the sensor maker's library.
 */

private const val src4Default00000001Size = 4

/** The four bytes the scheme starts from when nothing else is given. */
internal fun defaultSrc4(): ByteArray = bytesOf(0, 0, 0, 1)

/** Builds the empty `679f48` context out of the shipped seed table. */
internal fun init679f48Context(): ByteArray {
    val tables = Libre3FirstPairTables.get()
    val context = ByteArray(context679f48Size)

    for (spec in init679f48Block18Specs) {
        val src = checkedSlice(tables.seedTables679f48, spec.srcOffset, df80WordSize, Libre3FirstPairTables.SEED_TABLES_679F48)
        replace(context, spec.dstOffset, vm67cc18(spec.magic, src, src, tables))
    }

    val src66 = checkedSlice(tables.seedTables679f48, init679f48Block66SrcOffset, block66Size, Libre3FirstPairTables.SEED_TABLES_679F48)
    val block66 = vm67cc18(0x42000001e72uL, src66, src66, tables)
    for (dstOffset in init679f48Block66DstOffsets) {
        replace(context, dstOffset, block66)
    }
    return context
}

/** Puts the first four bytes into a fresh context and marks them as waiting. */
internal fun update67aa8cLen4Initial(context: ByteArray, src4: ByteArray): ByteArray {
    if (src4.size != src4Default00000001Size) {
        throw Libre3CryptoException("the 67aa8c source must be 4 bytes, not ${src4.size}")
    }
    if (context.size < context679f48Size) {
        throw Libre3CryptoException("the 67aa8c context must be at least $context679f48Size bytes, not ${context.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val ctx = context.copyOf()
    if (ctx.u8(0x1a4) != 0) {
        throw Libre3CryptoException("the 67aa8c context already has bytes waiting")
    }

    ctx[0x1a4] = 1
    seed67aa8cInitialWords(ctx, tables)
    replace(ctx, 0x1a5, src4)
    writeUInt32LE(4u, ctx, 0x1e8)
    return ctx
}

/** Turns the eight waiting words into the eight state blocks. */
internal fun apply67eb94PendingBlocks(context: ByteArray): ByteArray {
    if (context.size < context679f48Size) {
        throw Libre3CryptoException("the 67eb94 context must be at least $context679f48Size bytes, not ${context.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val ctx = context.copyOf()
    if (ctx.u8(0x1a4) == 0) return ctx

    ctx[0x1a4] = 0
    val words = ArrayList<ByteArray>(8)
    for (index in 0 until 8) {
        val start = 0x1ec + index * 4
        words.add(ctx.copyOfRange(start, start + 4))
    }
    replace(ctx, 0x114, update67eb94Blocks(words, tables))
    return ctx
}

/** Turns up to sixteen plain bytes into one 66 byte block. */
internal fun encode67d630Block(src: ByteArray): ByteArray {
    if (src.isEmpty() || src.size > 0x10) {
        throw Libre3CryptoException("a 67d630 block must be one to sixteen bytes, not ${src.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val scratch16 = ByteArray(0x10)
    for (index in src.indices) {
        scratch16[0x10 - src.size + index] = src[src.size - 1 - index]
    }

    var sideA = ByteArray(0)
    var sideB = ByteArray(0)
    for (byte in scratch16) {
        sideA += expandRawByte67d630(byte.toInt() and 0xFF, raw67d630TableAOffset, tables)
        sideB += expandRawByte67d630(byte.toInt() and 0xFF, raw67d630TableBOffset, tables)
    }

    val foldedA = fold96To66(0x600000032b2uL, 0x60000005e9cuL, sideA, tables)
    val mixedA = vm67cc18(0x42000004b29uL, foldedA, foldedA, tables)

    val foldedB = fold96To66(0x60000000133uL, 0x600000033dbuL, sideB, tables)
    val mixedB = vm67cc18(0x42000000263uL, foldedB, foldedB, tables)

    val mixed = vm67cc18(0x42000000b0euL, mixedA, mixedB, tables)
    return vm67d524(0xc03f000c0112fuL, mixed, tables)
}

/** Pushes every waiting byte through the block encoder and into the context. */
internal fun apply67eb94WithPendingRawAdapter(context: ByteArray): ByteArray {
    if (context.size < context679f48Size) {
        throw Libre3CryptoException("the 67eb94 context must be at least $context679f48Size bytes, not ${context.size}")
    }
    var ctx = context.copyOf()
    if (ctx.u8(0x1a4) == 0) return ctx
    val pendingLength = readUInt32LE(ctx, 0x1e8)
    if (pendingLength > 0x40u) {
        throw Libre3CryptoException("the 67eb94 context says $pendingLength bytes are waiting, which cannot be")
    }

    ctx = apply67eb94PendingBlocks(ctx)
    val pending = checkedSlice(ctx, 0x1a5, pendingLength.toInt(), "67eb94 pending bytes")
    var offset = 0
    while (offset < pending.size) {
        val end = minOf(offset + 0x10, pending.size)
        val chunk = pending.copyOfRange(offset, end)
        val encoded = encode67d630Block(chunk)
        ctx = apply67dd7cUpdateUntilDF80(ctx, encoded, chunk.size)
        offset = end
    }
    writeUInt32LE(0u, ctx, 0x1e8)
    return ctx
}

/** Adds one encoded block to the context, running the round function whenever four are full. */
internal fun apply67dd7cUpdateUntilDF80(context: ByteArray, encoded66: ByteArray, rawLength: Int): ByteArray {
    if (context.size < context679f48Size) {
        throw Libre3CryptoException("the 67dd7c context must be at least $context679f48Size bytes, not ${context.size}")
    }
    if (encoded66.size != block66Size) {
        throw Libre3CryptoException("a 67dd7c encoded block must be $block66Size bytes, not ${encoded66.size}")
    }
    if (rawLength <= 0 || rawLength > 0x10) {
        throw Libre3CryptoException("a 67dd7c block must carry one to sixteen plain bytes, not $rawLength")
    }

    val tables = Libre3FirstPairTables.get()
    val ctx = context.copyOf()
    val contextLength = readUInt64LE(ctx, 0)
    val low = (contextLength and 0x0fuL).toInt()
    val room = 0x10 - low
    var blockIndex = readUInt32LE(ctx, 0x110)
    val slot = 0x08 + blockIndex.toInt() * block66Size

    if (low != 0) {
        var staged = vm67cc18(0x42000005c05uL, encoded66, encoded66, tables)
        repeat(low) {
            staged = vm67cecc(0x1003e001002eafuL, staged, staged, tables)
        }

        val pad = checkedSlice(
            tables.finalizerTables,
            finalizerDD7CPadOffset + (low xor 0x0f) * block66Size,
            block66Size,
            Libre3FirstPairTables.FINALIZER_TABLES,
        )
        val current = checkedSlice(ctx, slot, block66Size, "67dd7c context slot")
        val prefix = vm67cc18(0x42000002fd4uL, current, pad, tables)
        replace(ctx, slot, vm67cc18(0x42000003060uL, prefix, staged, tables))
    } else {
        replace(ctx, slot, vm67cc18(0x42000001c66uL, encoded66, encoded66, tables))
    }

    if (room <= rawLength) {
        blockIndex += 1u
        writeUInt32LE(blockIndex, ctx, 0x110)
        if (blockIndex == 4u) {
            val transformed = df80Transform(ctx.copyOfRange(0x114, 0x1a4), ctx.copyOfRange(0x08, 0x110))
            replace(ctx, 0x114, transformed)
            blockIndex = 0u
            writeUInt32LE(0u, ctx, 0x110)
        }

        if (room < rawLength) {
            var remainder = vm67cc18(0x42000003d8buL, encoded66, encoded66, tables)
            repeat(room) {
                remainder = shift67dd7cRemainder(remainder, tables)
            }
            val nextSlot = 0x08 + blockIndex.toInt() * block66Size
            replace(ctx, nextSlot, vm67cc18(0x420000008deuL, remainder, remainder, tables))
        }
    }

    writeUInt64LE(contextLength + rawLength.toULong(), ctx, 0)
    return ctx
}

/** Turns the blocks of an earlier stage into the blocks this stage feeds on. */
internal fun previousDescriptorBlocksToDD7CInputs(previousBlocks: ByteArray): ByteArray {
    if (previousBlocks.size % block66Size != 0) {
        throw Libre3CryptoException("the earlier blocks must be a whole number of $block66Size byte blocks, not ${previousBlocks.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val out = ByteArray(previousBlocks.size)
    var write = 0
    for (start in previousBlocks.indices step block66Size) {
        val block = previousBlocks.copyOfRange(start, start + block66Size)
        val encoded = vm67cc18(0x42000001341uL, block, block, tables)
        val staged = vm67cc18(0x420000053bauL, encoded, encoded, tables)
        val done = vm67cc18(0x42000000c2cuL, staged, staged, tables)
        done.copyInto(out, write)
        write += done.size
    }
    return out
}

/** Runs a whole set of earlier blocks through the context and closes it. */
internal fun finalized679f48ContextFromInputs(previousBlocks: ByteArray, src4: ByteArray = defaultSrc4()): ByteArray {
    var context = init679f48Context()
    context = update67aa8cLen4Initial(context, src4)
    context = apply67eb94WithPendingRawAdapter(context)

    val fullUpdates = previousDescriptorBlocksToDD7CInputs(previousBlocks)
    for (start in fullUpdates.indices step block66Size) {
        context = apply67dd7cUpdateUntilDF80(context, fullUpdates.copyOfRange(start, start + block66Size), 0x10)
    }

    context = apply67eb94WithPendingRawAdapter(context)
    return finalize679f48ToSecondDF80(context)
}

/** The whole path from a set of earlier blocks to a slice of the answer. */
internal fun deriveFrom679f48Inputs(
    previousBlocks: ByteArray,
    src4: ByteArray = defaultSrc4(),
    offset: Int = 0,
    length: Int = 0x10,
): ByteArray {
    val context = finalized679f48ContextFromInputs(previousBlocks, src4)
    return deriveFromFinalized679f48Context(context, offset, length)
}

internal fun constructor670978Ptr28Blocks(rawDescriptorBlocks: ByteArray): ByteArray =
    constructor67076cBlocks(rawDescriptorBlocks, 0x42000000000uL)

internal fun constructor670a54Ptr10Blocks(rawDescriptorBlocks: ByteArray): ByteArray =
    constructor67076cBlocks(rawDescriptorBlocks, 0x42000000042uL)

internal fun deriveFrom660448RawDescriptor(
    rawDescriptorBlocks: ByteArray,
    src4: ByteArray = defaultSrc4(),
    offset: Int = 0,
    length: Int = 0x10,
): ByteArray {
    val previousBlocks = constructor670978Ptr28Blocks(rawDescriptorBlocks)
    return deriveFrom679f48Inputs(previousBlocks, src4, offset, length)
}

internal fun deriveFrom660448Sources(
    firstRawBlocks: ByteArray,
    secondRawBlocks: ByteArray,
    src4: ByteArray = defaultSrc4(),
    offset: Int = 0,
    length: Int = 0x10,
): ByteArray = deriveFrom660448RawDescriptor(firstRawBlocks + secondRawBlocks, src4, offset, length)

internal fun deriveFrom64d774RawStreams(
    firstRawBlocks: ByteArray,
    secondRawBlocks: ByteArray,
    src4: ByteArray = defaultSrc4(),
    offset: Int = 0,
    length: Int = 0x10,
): ByteArray {
    val firstFor660448 = constructor670a54Ptr10Blocks(firstRawBlocks)
    val secondFor660448 = constructor670a54Ptr10Blocks(secondRawBlocks)
    return deriveFrom660448Sources(firstFor660448, secondFor660448, src4, offset, length)
}

/** Pads the last block, writes the length, and runs the round function one last time. */
internal fun finalize679f48ToSecondDF80(context: ByteArray): ByteArray {
    if (context.size < context679f48Size) {
        throw Libre3CryptoException("the 679f48 context must be at least $context679f48Size bytes, not ${context.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val ctx = context.copyOf()
    val contextLength = readUInt64LE(ctx, 0)
    if (contextLength > Long.MAX_VALUE.toULong()) {
        throw Libre3CryptoException("the 679f48 context length is out of range")
    }
    val low = (contextLength and 0x0fuL).toInt()
    var blockIndex = readUInt32LE(ctx, 0x110)
    if (blockIndex > 4u) {
        throw Libre3CryptoException("the 679f48 context has a block index of $blockIndex, which cannot be")
    }

    val slot = 0x08 + blockIndex.toInt() * block66Size
    if (low != 0) {
        val padIndex = low xor 0x0f
        val pad1 = checkedSlice(
            tables.finalizerTables, finalizerDD7CPadOffset + padIndex * block66Size, block66Size,
            Libre3FirstPairTables.FINALIZER_TABLES,
        )
        val pad2 = checkedSlice(
            tables.finalizerTables, finalizerPad2Offset + padIndex * block66Size, block66Size,
            Libre3FirstPairTables.FINALIZER_TABLES,
        )
        val current = checkedSlice(ctx, slot, block66Size, "679f48 finalizer context")
        val mixed = vm67cc18(0x42000005702uL, current, pad1, tables)
        replace(ctx, slot, vm67cc18(0x42000005c47uL, mixed, pad2, tables))
    } else {
        val staticBlock = checkedSlice(
            tables.finalizerTables, finalizerZeroLowBlockOffset, block66Size, Libre3FirstPairTables.FINALIZER_TABLES,
        )
        replace(ctx, slot, vm67cc18(0x42000000ffbuL, staticBlock, staticBlock, tables))
    }

    if (low > 7 || blockIndex <= 2u) {
        blockIndex += 1u
        writeUInt32LE(blockIndex, ctx, 0x110)
        if (blockIndex == 4u) {
            val transformed = df80Transform(ctx.copyOfRange(0x114, 0x1a4), ctx.copyOfRange(0x08, 0x110))
            replace(ctx, 0x114, transformed)
            blockIndex = 0u
            writeUInt32LE(0u, ctx, 0x110)
        }

        if (blockIndex <= 3u) {
            val staticBlock = checkedSlice(
                tables.finalizerTables, finalizerStaticBlockOffset, block66Size, Libre3FirstPairTables.FINALIZER_TABLES,
            )
            while (blockIndex < 4u) {
                val fillSlot = 0x08 + blockIndex.toInt() * block66Size
                replace(ctx, fillSlot, vm67cc18(0x42000005d9duL, staticBlock, staticBlock, tables))
                blockIndex += 1u
                writeUInt32LE(blockIndex, ctx, 0x110)
            }
        }
    }

    writeUInt64LE(contextLength shl 3, ctx, 0)
    val finalLength = final679f48LengthBlock(contextLength.toInt())
    val finalMixed = vm67cc18(0x420000040aauL, finalLength, ctx.copyOfRange(0xce, 0x110), tables)
    replace(ctx, 0xce, finalMixed)
    val transformed = df80Transform(ctx.copyOfRange(0x114, 0x1a4), ctx.copyOfRange(0x08, 0x110))
    replace(ctx, 0x114, transformed)
    return ctx
}

/** Closes a context that is still open, then reads a slice out of it. */
internal fun deriveFrom679f48Context(context: ByteArray, offset: Int = 0, length: Int = 0x10): ByteArray =
    deriveFromFinalized679f48Context(finalize679f48ToSecondDF80(context), offset, length)

/** The round function: four full blocks in, a new state out. */
internal fun df80Transform(state: ByteArray, blocks: ByteArray): ByteArray {
    val workspace = df80InitialWorkspace(blocks)
    val schedule = df80ExpandedSchedule(workspace)
    return df80CompressState(state, schedule)
}

internal fun df80CompressState(state: ByteArray, schedule: ByteArray): ByteArray {
    if (state.size != df80StateSize) {
        throw Libre3CryptoException("the df80 state must be $df80StateSize bytes, not ${state.size}")
    }
    if (schedule.size != df80ScheduleSize) {
        throw Libre3CryptoException("the df80 schedule must be $df80ScheduleSize bytes, not ${schedule.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val original = ArrayList<ByteArray>(8)
    for (start in 0 until df80StateSize step df80WordSize) {
        original.add(state.copyOfRange(start, start + df80WordSize))
    }

    var s0 = vm67cc18(0x1200000189duL, original[0], original[0], tables)
    var s1 = vm67cc18(0x12000001e60uL, original[1], original[1], tables)
    var s2 = vm67cc18(0x1200000152duL, original[2], original[2], tables)
    var s3 = vm67cc18(0x120000029eduL, original[3], original[3], tables)
    var s4 = vm67cc18(0x120000040ecuL, original[4], original[4], tables)
    var s5 = vm67cc18(0x120000036eduL, original[5], original[5], tables)
    var s6 = vm67cc18(0x12000003423uL, original[6], original[6], tables)
    var s7 = vm67cc18(0x12000004056uL, original[7], original[7], tables)

    val staticB = checkedSlice(tables.df80RoundTables, df80ScheduleSize, df80WordSize, Libre3FirstPairTables.DF80_ROUND_TABLES)

    for (offset in 0 until df80ScheduleSize step df80WordSize) {
        val word = schedule.copyOfRange(offset, offset + df80WordSize)
        val roundA = checkedSlice(tables.df80RoundTables, offset, df80WordSize, Libre3FirstPairTables.DF80_ROUND_TABLES)

        var tmp80 = vm67cecc(0x0c00f000c01a40uL, s4, s4, tables)
        var tmp58 = packDF80Zeros12Marker3(s4)
        var tmp22 = vm67cc18(0x120000032dcuL, tmp58, tmp58, tables)
        val mix10 = vm67cc18(0x12000001105uL, tmp22, tmp80, tables)

        tmp22 = vm67cecc(0x1800c0018050f7uL, s4, s4, tables)
        tmp80 = vm67cc18(0x12000002cc3uL, s4, s4, tables)
        tmp58 = packDF80Zeros8Zero6(tmp80)
        var t64 = vm67cc18(0x120000030c4uL, tmp58, tmp58, tables)
        val mix14 = vm67cc18(0x12000001251uL, t64, tmp22, tables)

        val mix15 = vm67cecc(0x34005003403785uL, s4, s4, tables)
        tmp80 = vm67cc18(0x120000025a0uL, s4, s4, tables)
        tmp58 = packDF80Zeros2Marker6(tmp80)
        val mix17 = vm67cc18(0x12000000d92uL, tmp58, tmp58, tables)
        val mix18 = vm67cc18(0x120000026f3uL, mix17, mix15, tables)
        val mix19 = vm67cc18(0x12000000e82uL, mix10, mix14, tables)
        var t76 = vm67cc18(0x120000023eeuL, mix18, mix19, tables)
        t76 = vm67cc18(0x120000043a5uL, s7, t76, tables)

        t64 = vm67cc18(0x12000005386uL, roundA, word, tables)
        val t52 = vm67cc18(0x12000004501uL, t76, t64, tables)

        tmp58 = vm67cc18(0x12000000ce4uL, s4, s5, tables)
        tmp80 = vm67cc18(0x12000003aa4uL, staticB, s4, tables)
        tmp22 = vm67cc18(0x12000000f71uL, tmp80, s6, tables)
        val t40 = vm67cc18(0x120000020bcuL, tmp58, tmp22, tables)
        val tmp92 = vm67cc18(0x12000000aa6uL, t52, t40, tables)

        tmp80 = vm67cecc(0x04011000404984uL, s0, s0, tables)
        tmp58 = packDF80Zeros14Marker1(s0)
        tmp22 = vm67cc18(0x1200000190buL, tmp58, tmp58, tables)
        val t2e = vm67cc18(0x12000003cd1uL, tmp22, tmp80, tables)

        tmp22 = vm67cecc(0x1c00b001c048a7uL, s0, s0, tables)
        tmp80 = vm67cc18(0x12000003683uL, s0, s0, tables)
        tmp58 = packDF80Zeros9(tmp80)
        tmp58 = vm67cc18(0x120000017b1uL, tmp58, tmp58, tables)
        val t1c = vm67cc18(0x12000000d5euL, tmp58, tmp22, tables)

        tmp80 = vm67cecc(0x2c007002c000bauL, s0, s0, tables)
        tmp58 = packDF80Zeros4Marker3(s0)
        tmp22 = vm67cc18(0x120000001fduL, tmp58, tmp58, tables)
        val tmp34First = vm67cc18(0x12000006062uL, tmp22, tmp80, tables)

        tmp80 = vm67cc18(0x12000004ffbuL, t2e, t1c, tables)
        tmp58 = vm67cc18(0x120000032cauL, tmp34First, tmp80, tables)

        tmp22 = vm67cc18(0x12000000d08uL, s0, s1, tables)
        val tmp34 = vm67cc18(0x120000019b6uL, s0, s2, tables)
        val t2eSecond = vm67cc18(0x12000004352uL, s1, s2, tables)
        val t1cSecond = vm67cc18(0x12000000f5fuL, tmp22, tmp34, tables)
        tmp80 = vm67cc18(0x12000004010uL, t2eSecond, t1cSecond, tables)
        val tmpa4 = vm67cc18(0x120000035d7uL, tmp58, tmp80, tables)

        val newS7 = vm67cc18(0x12000003bc0uL, s6, s6, tables)
        val newS6 = vm67cc18(0x120000042aauL, s5, s5, tables)
        val newS5 = vm67cc18(0x120000042bcuL, s4, s4, tables)
        val newS4 = vm67cc18(0x12000006050uL, s3, tmp92, tables)
        val newS3 = vm67cc18(0x120000047c5uL, s2, s2, tables)
        val newS2 = vm67cc18(0x12000004e83uL, s1, s1, tables)
        val newS1 = vm67cc18(0x120000055c9uL, s0, s0, tables)
        val newS0 = vm67cc18(0x12000002088uL, tmp92, tmpa4, tables)

        s0 = newS0
        s1 = newS1
        s2 = newS2
        s3 = newS3
        s4 = newS4
        s5 = newS5
        s6 = newS6
        s7 = newS7
    }

    return vm67cc18(0x12000001eb4uL, original[0], s0, tables) +
        vm67cc18(0x12000005b5buL, original[1], s1, tables) +
        vm67cc18(0x120000042e6uL, original[2], s2, tables) +
        vm67cc18(0x12000000b94uL, original[3], s3, tables) +
        vm67cc18(0x1200000383auL, original[4], s4, tables) +
        vm67cc18(0x12000003581uL, original[5], s5, tables) +
        vm67cc18(0x12000004deauL, original[6], s6, tables) +
        vm67cc18(0x12000000dd8uL, original[7], s7, tables)
}

internal fun df80ExpandedSchedule(initialWorkspace: ByteArray): ByteArray {
    if (initialWorkspace.size != df80InitialWorkspaceSize) {
        throw Libre3CryptoException("the df80 work area must be $df80InitialWorkspaceSize bytes, not ${initialWorkspace.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val schedule = initialWorkspace + ByteArray(df80DerivedScheduleSize)

    for (offset in 0 until df80DerivedScheduleSize step df80WordSize) {
        val w0 = schedule.copyOfRange(offset, offset + df80WordSize)
        val w1 = schedule.copyOfRange(offset + 0x12, offset + 0x24)
        val w9 = schedule.copyOfRange(offset + 0xa2, offset + 0xb4)
        val w14 = schedule.copyOfRange(offset + 0xfc, offset + 0x10e)

        var tmp22 = vm67cecc(0x2400900240463cuL, w14, w14, tables)
        var tmp80 = vm67cc18(0x12000005af5uL, w14, w14, tables)
        var tmp58 = packDF80Zeros6Marker(0x06, tmp80)
        var tmp34 = vm67cc18(0x12000005ddfuL, tmp58, tmp58, tables)
        var tmpa4 = vm67cc18(0x12000000523uL, tmp34, tmp22, tables)

        tmp22 = vm67cecc(0x2800800280004auL, w14, w14, tables)
        tmp80 = vm67cc18(0x12000002717uL, w14, w14, tables)
        tmp58 = packDF80Zeros5Marker6(tmp80)
        tmp34 = vm67cc18(0x1200000304euL, tmp58, tmp58, tables)
        tmp58 = vm67cc18(0x120000016d7uL, tmp34, tmp22, tables)

        tmp80 = vm67cecc(0x1400d001403ea3uL, w14, w14, tables)
        tmp22 = vm67cc18(0x120000037a4uL, tmpa4, tmp58, tables)
        val tmp92 = vm67cc18(0x120000034f0uL, tmp80, tmp22, tables)
        tmpa4 = vm67cc18(0x1200000050buL, tmp92, w9, tables)

        tmp22 = vm67cecc(0x1000e0010042f8uL, w1, w1, tables)
        tmp80 = vm67cc18(0x12000000251uL, w1, w1, tables)
        tmp58 = packDF80Zeros11Marker5(tmp80)
        tmp34 = vm67cc18(0x12000005e68uL, tmp58, tmp58, tables)
        val tmp118 = vm67cc18(0x12000004120uL, tmp34, tmp22, tables)

        tmp80 = vm67cecc(0x24009002402391uL, w1, w1, tables)
        tmp58 = packDF80Zeros6Marker(0x07, w1)
        tmp22 = vm67cc18(0x12000000da4uL, tmp58, tmp58, tables)
        tmp34 = vm67cc18(0x12000003e91uL, tmp22, tmp80, tables)

        tmp80 = vm67cecc(0x08010000802f7euL, w1, w1, tables)
        tmp22 = vm67cc18(0x120000041eauL, tmp118, tmp34, tables)
        tmp58 = vm67cc18(0x12000000846uL, tmp80, tmp22, tables)
        tmp80 = vm67cc18(0x120000019eauL, tmp58, w0, tables)
        val derived = vm67cc18(0x12000003ac8uL, tmpa4, tmp80, tables)
        replace(schedule, offset + df80InitialWorkspaceSize, derived)
    }

    return schedule
}

internal fun df80InitialWorkspace(blocks: ByteArray): ByteArray {
    if (blocks.size != df80InputBlockCount * block66Size) {
        throw Libre3CryptoException("the df80 input must be $df80InputBlockCount blocks, not ${blocks.size} bytes")
    }
    val tables = Libre3FirstPairTables.get()
    val workspace = ByteArray(df80InitialWorkspaceSize)

    for (index in 0 until df80InputBlockCount) {
        val start = index * block66Size
        val dst = index * df80InitialWorkspaceStride
        val src = blocks.copyOfRange(start, start + block66Size)
        val sideA = vm67cc18(0x22000002444uL, src, src, tables)
        val sideB = vm67cecc(0x22008004942uL, src, src, tables)

        replace(workspace, dst + 0x36, vm67cc18(0x12000000deauL, sideA, sideA, tables))
        replace(workspace, dst + 0x24, vm67cecc(0x12004003dcduL, sideA, sideA, tables))
        replace(workspace, dst + 0x12, vm67cc18(0x120000052bbuL, sideB, sideB, tables))
        replace(workspace, dst, vm67cecc(0x12004003d69uL, sideB, sideB, tables))
    }

    return workspace
}

/** The block that carries the length of everything the context has seen. */
internal fun final679f48LengthBlock(contextLength: Int): ByteArray {
    if (contextLength < 0) {
        throw Libre3CryptoException("the 679f48 context length must not be negative")
    }
    val tables = Libre3FirstPairTables.get()
    val bitLength = contextLength.toULong() shl 3
    val sideA = expandU64Trits(bitLength, 0, tables)
    val sideB = expandU64Trits(bitLength, 0x300, tables)

    val foldedA = fold48To34(0x600000051duL, 0x6000002556uL, sideA, tables)
    val laneA = vm67cecc(0x800220000010a1uL, foldedA, foldedA, tables)

    val foldedB = fold48To34(0x60000018afuL, 0x6000005ee6uL, sideB, tables)
    val laneB = vm67cecc(0x80022000004224uL, foldedB, foldedB, tables)

    val mixed = vm67cc18(0x420000007c0uL, laneA, laneB, tables)
    return vm67d524(0xc03f000c0192fuL, mixed, tables)
}

internal fun deriveFrom67cc18Sources(sourceChunks: ByteArray, offset: Int = 0, length: Int = 0x10): ByteArray {
    if (sourceChunks.size % block66Size != 0) {
        throw Libre3CryptoException("the 67cc18 sources must be a whole number of $block66Size byte blocks, not ${sourceChunks.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val encoded = ByteArray(sourceChunks.size)
    var write = 0
    for (start in sourceChunks.indices step block66Size) {
        val chunk = sourceChunks.copyOfRange(start, start + block66Size)
        vm67cc18(0x420000059c9uL, chunk, chunk, tables).copyInto(encoded, write)
        write += block66Size
    }
    return derive64de54Slice(encoded, offset, length)
}

internal fun deriveFrom67a960Inputs(src1: ByteArray, src2: ByteArray, offset: Int = 0, length: Int = 0x10): ByteArray {
    if (src1.size != scratch130Size || src2.size != scratch130Size) {
        throw Libre3CryptoException("the 67a960 sources must both be $scratch130Size bytes")
    }
    val tables = Libre3FirstPairTables.get()
    val source67a978 = vm67cc18(0x1c0012000003b1cuL, src1, src2, tables)
    return deriveFrom67a978Source(source67a978, offset, length)
}

internal fun deriveFromFinalized679f48Context(context: ByteArray, offset: Int = 0, length: Int = 0x10): ByteArray {
    if (context.size < context679f48Size) {
        throw Libre3CryptoException("the 679f48 context must be at least $context679f48Size bytes, not ${context.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val (src1, src2) = postDF80_67a960Inputs(context, tables)
    return deriveFrom67a960Inputs(src1, src2, offset, length)
}

internal fun deriveFrom67a978Source(source: ByteArray, offset: Int = 0, length: Int = 0x10): ByteArray {
    if (source.size != scratch130Size) {
        throw Libre3CryptoException("the 67a978 source must be $scratch130Size bytes, not ${source.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val source67a990 = vm67cc18(0x82000000477uL, source, source, tables)
    return deriveFrom67a990Source(source67a990, offset, length)
}

internal fun deriveFrom67a990Source(source: ByteArray, offset: Int = 0, length: Int = 0x10): ByteArray {
    if (source.size != scratch130Size) {
        throw Libre3CryptoException("the 67a990 source must be $scratch130Size bytes, not ${source.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val window = vm67cc18(0x82000003c2duL, source, source, tables)
    val chunks = final67cc18Sources(window, tables)
    return deriveFrom67cc18Sources(chunks, offset, length)
}

/** Reads a run of bytes out of a set of encoded blocks. */
internal fun derive64de54Slice(encodedBlocks: ByteArray, offset: Int = 0, length: Int = 0x10): ByteArray {
    if (offset < 0 || length < 0) {
        throw Libre3CryptoException("a slice cannot start at $offset for $length bytes")
    }
    if (encodedBlocks.size % block66Size != 0) {
        throw Libre3CryptoException("the encoded blocks must be a whole number of $block66Size byte blocks, not ${encodedBlocks.size}")
    }

    val tables = Libre3FirstPairTables.get()
    val sourceBlocks = ArrayList<ByteArray>(encodedBlocks.size / block66Size)
    for (start in encodedBlocks.indices step block66Size) {
        sourceBlocks.add(encodedBlocks.copyOfRange(start, start + block66Size))
    }
    if (sourceBlocks.isEmpty() && length > 0) {
        throw Libre3CryptoException("a slice was asked for, but there is no source at all")
    }

    val expanded = sourceBlocks.map { vm64e2b8(0x42000000106uL, it, it, tables) }

    val startBlock = offset shr 4
    val lowNibble = offset and 0x0f
    val outBlocks = (length + 0x0f) shr 4
    val stageBlocks = ArrayList<ByteArray>(outBlocks)

    for (outIndex in 0 until outBlocks) {
        val idx = startBlock + outIndex
        if (idx >= expanded.size) {
            throw Libre3CryptoException("the slice starts past the end of the source, at block $idx")
        }

        val scratchSrc2 = shiftedScratch(expanded[idx])
        val scratch = if (idx + 1 < expanded.size) {
            val src1 = expanded[idx + 1] + ByteArray(64)
            vm64e2b8(0x100042000000148uL, src1, scratchSrc2, tables)
        } else {
            vm64e2b8(0x82000000084uL, scratchSrc2, scratchSrc2, tables)
        }

        var shifted = scratch
        repeat(16 - lowNibble) {
            shifted = vm64e17c(shifted, shifted, tables)
        }
        stageBlocks.add(vm64e2b8(0x42000000000uL, shifted, shifted, tables))
    }

    val out = ByteArray(stageBlocks.size * block66Size)
    var write = 0
    for (block in stageBlocks) {
        vm64e2b8(0x42000000042uL, block, block, tables).copyInto(out, write)
        write += block66Size
    }
    return out
}

private fun final67cc18Sources(window: ByteArray, tables: Libre3FirstPairTables): ByteArray {
    requireSize(window, scratch130Size, "67a990 overlap window")
    val firstSrc = window.copyOfRange(0x40, 0x40 + block66Size)
    val secondSrc = window.copyOfRange(0, block66Size)
    return vm67cc18(0x420000054c3uL, firstSrc, firstSrc, tables) +
        vm67cc18(0x420000054c3uL, secondSrc, secondSrc, tables)
}

private fun postDF80_67a960Inputs(context: ByteArray, tables: Libre3FirstPairTables): Pair<ByteArray, ByteArray> {
    requireSize(context, context679f48Size, "679f48 context")
    val state = context.copyOfRange(0x114, 0x1a4)

    val buf3b0 = ByteArray(scratch130Size)
    val buf320 = ByteArray(scratch130Size)
    val buf3e = ByteArray(scratch130Size)
    val bufd = ByteArray(scratch130Size)

    buf3b0[15] = 6
    replace(buf3b0, 16, state.copyOfRange(0x5a, 0x6c))
    replace(
        buf3e, 0,
        vm67cc18(0x40012000002ba3uL, context.copyOfRange(0x180, 0x180 + 34), buf3b0.copyOfRange(0, 34), tables),
    )

    buf320[15] = 6
    replace(buf320, 16, state.copyOfRange(0x36, 0x48))
    replace(
        buf3b0, 0x20,
        vm67cc18(0x4001200000255cuL, context.copyOfRange(0x15c, 0x15c + 34), buf320.copyOfRange(0, 34), tables),
    )

    replace(buf3b0, 0, ByteArray(31))
    buf3b0[31] = 7
    replace(
        bufd, 0,
        vm67cc18(0x80022000000ca2uL, buf3e.copyOfRange(0, 66), buf3b0.copyOfRange(0, 66), tables),
    )

    replace(buf3b0, 0, ByteArray(16))
    replace(buf3b0, 16, state.copyOfRange(0x12, 0x24))
    replace(
        buf3e, 0,
        vm67cc18(0x40012000005f0euL, context.copyOfRange(0x138, 0x138 + 34), buf3b0.copyOfRange(0, 34), tables),
    )

    replace(buf3b0, 0, ByteArray(31))
    buf3b0[31] = 7
    replace(buf3b0, 32, state.copyOfRange(0, 0x12))
    replace(
        buf320, 0x40,
        vm67cc18(0x400220000013d9uL, buf3e.copyOfRange(0, 50), buf3b0.copyOfRange(0, 50), tables),
    )

    replace(buf320, 0, ByteArray(0x40))
    replace(
        buf3b0, 0x10,
        vm67cc18(0xc00420000016e9uL, bufd.copyOfRange(0, 114), buf320.copyOfRange(0, 114), tables),
    )
    replace(buf3b0, 0, ByteArray(15))
    buf3b0[15] = 1

    val src1 = context.copyOfRange(0x192, 0x1a4) + ByteArray(112)
    return src1 to buf3b0
}
