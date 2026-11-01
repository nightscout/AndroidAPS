package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException

/*
 * The `6388f0` stream: from twenty schedule words down to the blocks the digest reads.
 *
 * Ported from LibreCRKit `Crypto/FirstPairSourceSlice.swift` at pin `a86b92f`.
 *
 * The stream has six layers, and every layer has its own published vector, which is what makes
 * this port checkable one step at a time:
 *
 * schedule words → lane blocks → pack outputs → stage inputs → prefinal → internal → raw blocks.
 */

/** The two lane block runs of one stage. */
internal class Libre3LaneBlocks(val primaryLaneBlocks: ByteArray, val secondaryLaneBlocks: ByteArray)

/** The four packed pieces of one stage. */
internal class Libre3PackOutputs(
    val stageBPackHead16: ByteArray,
    val stageBPackBody16: ByteArray,
    val stageAPackHead16: ByteArray,
    val stageAPackBody16: ByteArray,
)

/** The two stage sources of one stream. */
internal class Libre3StageInputs(val stageASource: ByteArray, val stageBSource: ByteArray)

internal fun builder6388f0FinalRawBlocks(internalBlocks: ByteArray): ByteArray {
    if (internalBlocks.size % block66Size != 0) {
        throw Libre3CryptoException("the internal blocks must be a whole number of $block66Size byte blocks, not ${internalBlocks.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val out = ByteArray(internalBlocks.size)
    var write = 0
    for (start in internalBlocks.indices step block66Size) {
        val block = internalBlocks.copyOfRange(start, start + block66Size)
        vm638840(0x42000007e29uL, block, block, tables).copyInto(out, write)
        write += block66Size
    }
    return out
}

/** Note that the two blocks change places here. That is what the original does. */
internal fun builder6388f0PrefinalLen32InternalBlocks(prefinalSourceBlocks: ByteArray): ByteArray {
    if (prefinalSourceBlocks.size != 2 * block66Size) {
        throw Libre3CryptoException("the prefinal source must be two $block66Size byte blocks, not ${prefinalSourceBlocks.size} bytes")
    }
    val tables = Libre3FirstPairTables.get()
    val call0 = prefinalSourceBlocks.copyOfRange(0, block66Size)
    val call1 = prefinalSourceBlocks.copyOfRange(block66Size, 2 * block66Size)
    val block1 = vm638840(0x42000003bf9uL, call0, call0, tables)
    val block0 = vm638840(0x42000003bf9uL, call1, call1, tables)
    return block0 + block1
}

internal fun builder6388f0Len32PrefinalSourcesFromWorkspace(workspaceSource: ByteArray): ByteArray {
    if (workspaceSource.size != builder6388f0WorkspaceSize) {
        throw Libre3CryptoException("the 6388f0 work area must be $builder6388f0WorkspaceSize bytes, not ${workspaceSource.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val workspace = vm638840(0x10a000006cd1uL, workspaceSource, workspaceSource, tables)
    if (workspace.size != builder6388f0WorkspaceSize) {
        throw Libre3CryptoException("the 6388f0 work area came back as ${workspace.size} bytes")
    }

    val firstPrefinal = workspace.copyOfRange(0, block66Size)
    val updated = vm6420d8(0x1000ca0100063f1uL, workspace, workspace, tables)
    if (updated.size != builder6388f0WorkspaceSize) {
        throw Libre3CryptoException("the 6388f0 work area came back as ${updated.size} bytes")
    }

    val secondPrefinal = updated.copyOfRange(0, block66Size)
    return firstPrefinal + secondPrefinal
}

internal fun builder6388f0Len32PrefinalSourcesFromStageInputs(stageASource: ByteArray, stageBSource: ByteArray): ByteArray {
    if (stageASource.size != builder6388f0StageSize) {
        throw Libre3CryptoException("the 6388f0 stage A source must be $builder6388f0StageSize bytes, not ${stageASource.size}")
    }
    if (stageBSource.size != builder6388f0StageSize) {
        throw Libre3CryptoException("the 6388f0 stage B source must be $builder6388f0StageSize bytes, not ${stageBSource.size}")
    }

    val tables = Libre3FirstPairTables.get()
    val stageA = vm638840(0x11a000003e76uL, stageASource, stageASource, tables)
    val stageB = vm638840(0x11a000004f0cuL, stageBSource, stageA, tables)
    val stageC = vm638840(0x11a000004b16uL, stageB, stageA, tables)
    val stageDSource = stageC.copyOfRange(0, builder6388f0WorkspaceSize)
    val stageD = vm638840(0x10a0000078bbuL, stageDSource, stageDSource, tables)
    return builder6388f0Len32PrefinalSourcesFromWorkspace(stageD)
}

/** Joins one head block and nineteen body blocks, dropping the first two bytes of each body block. */
internal fun pack6388f0Twenty16To282(head16: ByteArray, bodyBlocks16: ByteArray): ByteArray {
    if (head16.size != builder6388f0LaneBlockSize) {
        throw Libre3CryptoException("the 6388f0 pack head must be $builder6388f0LaneBlockSize bytes, not ${head16.size}")
    }
    val expectedBody = (builder6388f0LaneBlockCount - 1) * builder6388f0LaneBlockSize
    if (bodyBlocks16.size != expectedBody) {
        throw Libre3CryptoException("the 6388f0 pack body must be $expectedBody bytes, not ${bodyBlocks16.size}")
    }

    var out = head16.copyOf()
    for (offset in 0 until bodyBlocks16.size step builder6388f0LaneBlockSize) {
        out += bodyBlocks16.copyOfRange(offset + 2, offset + builder6388f0LaneBlockSize)
    }
    return out
}

internal fun builder6388f0Len32StageInputsFromPackOutputs(pack: Libre3PackOutputs): Libre3StageInputs {
    val stageBPack = pack6388f0Twenty16To282(pack.stageBPackHead16, pack.stageBPackBody16)
    val tables = Libre3FirstPairTables.get()
    val stageBSource = vm638840(0x11a000000a2cuL, stageBPack, stageBPack, tables)
    val stageASource = pack6388f0Twenty16To282(pack.stageAPackHead16, pack.stageAPackBody16)
    return Libre3StageInputs(stageASource, stageBSource)
}

internal fun builder6388f0PackOutputsFromLaneBlocks(lanes: Libre3LaneBlocks): Libre3PackOutputs {
    if (lanes.primaryLaneBlocks.size != builder6388f0LaneBlocksSize) {
        throw Libre3CryptoException("the primary lane blocks must be $builder6388f0LaneBlocksSize bytes, not ${lanes.primaryLaneBlocks.size}")
    }
    if (lanes.secondaryLaneBlocks.size != builder6388f0LaneBlocksSize) {
        throw Libre3CryptoException("the secondary lane blocks must be $builder6388f0LaneBlocksSize bytes, not ${lanes.secondaryLaneBlocks.size}")
    }

    val tables = Libre3FirstPairTables.get()
    val primary = ArrayList<ByteArray>(builder6388f0LaneBlockCount)
    val secondary = ArrayList<ByteArray>(builder6388f0LaneBlockCount)
    for (offset in 0 until builder6388f0LaneBlocksSize step builder6388f0LaneBlockSize) {
        primary.add(lanes.primaryLaneBlocks.copyOfRange(offset, offset + builder6388f0LaneBlockSize))
        secondary.add(lanes.secondaryLaneBlocks.copyOfRange(offset, offset + builder6388f0LaneBlockSize))
    }

    val stageBPackHead = vm638840(0x10000000388uL, primary[0], primary[0], tables)
    var stageBPackBody = ByteArray(0)
    for (index in 1 until primary.size) {
        val block = primary[index]
        stageBPackBody += vm638840(0x10000003bd7uL, block, block, tables)
    }

    val stageAPackHead = vm638840(0x100000062d7uL, secondary[0], secondary[0], tables)
    var stageAPackBody = ByteArray(0)
    for (index in 1 until secondary.size) {
        val block = secondary[index]
        stageAPackBody += vm638840(0x10000008177uL, block, block, tables)
    }

    return Libre3PackOutputs(stageBPackHead, stageBPackBody, stageAPackHead, stageAPackBody)
}

/** Turns twenty schedule words into the two lane block runs. */
internal fun builder6388f0LaneBlocksFromScheduleWords(scheduleWords: UIntArray): Libre3LaneBlocks {
    if (scheduleWords.size != builder6388f0LaneBlockCount) {
        throw Libre3CryptoException("the 6388f0 schedule must be $builder6388f0LaneBlockCount words, not ${scheduleWords.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val primaryStatic = checkedSlice(
        tables.laneTables6388f0, builder6388f0LanePrimaryStaticOffset, builder6388f0LaneTableExpandedSize,
        Libre3FirstPairTables.LANE_TABLES_6388F0,
    )
    val secondaryStatic = checkedSlice(
        tables.laneTables6388f0, builder6388f0LaneSecondaryStaticOffset, builder6388f0LaneTableExpandedSize,
        Libre3FirstPairTables.LANE_TABLES_6388F0,
    )

    var primaryLanes = ByteArray(0)
    var secondaryLanes = ByteArray(0)

    for ((index, word) in scheduleWords.withIndex()) {
        val selector = selector6388f0(index, word, tables)
        var primaryState = checkedSlice(
            tables.laneTables6388f0, builder6388f0LaneAInitOffset, builder6388f0LaneBlockSize,
            Libre3FirstPairTables.LANE_TABLES_6388F0,
        ) + bytesOf(0x05, 0x04)
        var secondaryState = checkedSlice(
            tables.laneTables6388f0, builder6388f0LaneBInitOffset, builder6388f0LaneBlockSize,
            Libre3FirstPairTables.LANE_TABLES_6388F0,
        ) + bytesOf(0x05, 0x02)

        for (shift in intArrayOf(24, 16, 8, 0)) {
            val primaryPrimer = lanePrefixed6388f0(0x03000000u, primaryState)
            primaryState = vm638840(0x1200000712fuL, primaryPrimer, primaryPrimer, tables)

            val secondaryPrimer = lanePrefixed6388f0(0x01000000u, secondaryState)
            secondaryState = vm638840(0x120000003aauL, secondaryPrimer, secondaryPrimer, tables)

            val selectorByte = ((selector shr shift) and 0xffu).toInt()
            primaryState = vm638840(
                0x1200000551auL, primaryState,
                laneTable6388f0Expand(builder6388f0LaneATableOffset, selectorByte, tables), tables,
            )
            secondaryState = vm638840(
                0x12000000c60uL, secondaryState,
                laneTable6388f0Expand(builder6388f0LaneBTableOffset, selectorByte, tables), tables,
            )
        }

        val primarySource = vm638840(0x12000003d45uL, primaryState, primaryStatic, tables)
        val secondarySource = vm638840(0x12000005e9duL, secondaryState, secondaryStatic, tables)
        primaryLanes += vm638840(0x10000000214uL, primarySource, primarySource, tables)
        secondaryLanes += vm638840(0x10000003231uL, secondarySource, secondarySource, tables)
    }

    return Libre3LaneBlocks(primaryLanes, secondaryLanes)
}

private fun laneTable6388f0Expand(tableOffset: Int, selectorByte: Int, tables: Libre3FirstPairTables): ByteArray {
    val rowOffset = tableOffset + selectorByte * builder6388f0LaneTablePackedRowSize
    val row = checkedSlice(
        tables.laneTables6388f0, rowOffset, builder6388f0LaneTablePackedRowSize, Libre3FirstPairTables.LANE_TABLES_6388F0,
    )
    val out = ByteArray(builder6388f0LaneTableExpandedSize)
    var write = 0
    for (packed in row) {
        out[write++] = (packed.toInt() and 7).toByte()
        out[write++] = ((packed.toInt() and 0xFF) shr 3 and 7).toByte()
    }
    return out
}

private fun lanePrefixed6388f0(prefixWord: UInt, state18: ByteArray): ByteArray {
    requireSize(state18, 14, "6388f0 lane state")
    return u32LEBytes(prefixWord) + state18.copyOfRange(0, 14)
}

private fun selector6388f0(index: Int, scheduleWord: UInt, tables: Libre3FirstPairTables): UInt {
    val tableOffset = (index * 4) and 0x1c
    if (tableOffset + 4 > tables.selectorMul6388f0.size || tableOffset + 4 > tables.selectorAdd6388f0.size) {
        throw Libre3CryptoException(
            "the first pairing table ${Libre3FirstPairTables.SELECTOR_MUL_6388F0} has nothing at offset $tableOffset"
        )
    }
    val multiplier = readUInt32LE(tables.selectorMul6388f0, tableOffset).toULong()
    val addend = readUInt32LE(tables.selectorAdd6388f0, tableOffset).toULong()
    return ((scheduleWord.toULong() * multiplier + addend) and 0xffffffffuL).toUInt()
}

internal fun deriveFrom6388f0InternalStreams(
    firstInternalBlocks: ByteArray,
    secondInternalBlocks: ByteArray,
    src4: ByteArray = defaultSrc4(),
    offset: Int = 0,
    length: Int = 0x10,
): ByteArray = deriveFrom64d774RawStreams(
    builder6388f0FinalRawBlocks(firstInternalBlocks),
    builder6388f0FinalRawBlocks(secondInternalBlocks),
    src4, offset, length,
)

internal fun deriveFrom6388f0PrefinalLen32Streams(
    firstPrefinalBlocks: ByteArray,
    secondPrefinalBlocks: ByteArray,
    src4: ByteArray = defaultSrc4(),
    offset: Int = 0,
    length: Int = 0x10,
): ByteArray = deriveFrom6388f0InternalStreams(
    builder6388f0PrefinalLen32InternalBlocks(firstPrefinalBlocks),
    builder6388f0PrefinalLen32InternalBlocks(secondPrefinalBlocks),
    src4, offset, length,
)

internal fun deriveFrom6388f0WorkspaceLen32Streams(
    firstWorkspaceSource: ByteArray,
    secondWorkspaceSource: ByteArray,
    src4: ByteArray = defaultSrc4(),
    offset: Int = 0,
    length: Int = 0x10,
): ByteArray = deriveFrom6388f0PrefinalLen32Streams(
    builder6388f0Len32PrefinalSourcesFromWorkspace(firstWorkspaceSource),
    builder6388f0Len32PrefinalSourcesFromWorkspace(secondWorkspaceSource),
    src4, offset, length,
)

internal fun deriveFrom6388f0StageLen32Streams(
    first: Libre3StageInputs,
    second: Libre3StageInputs,
    src4: ByteArray = defaultSrc4(),
    offset: Int = 0,
    length: Int = 0x10,
): ByteArray = deriveFrom6388f0PrefinalLen32Streams(
    builder6388f0Len32PrefinalSourcesFromStageInputs(first.stageASource, first.stageBSource),
    builder6388f0Len32PrefinalSourcesFromStageInputs(second.stageASource, second.stageBSource),
    src4, offset, length,
)

internal fun deriveFrom6388f0PackLen32Streams(
    first: Libre3PackOutputs,
    second: Libre3PackOutputs,
    src4: ByteArray = defaultSrc4(),
    offset: Int = 0,
    length: Int = 0x10,
): ByteArray = deriveFrom6388f0StageLen32Streams(
    builder6388f0Len32StageInputsFromPackOutputs(first),
    builder6388f0Len32StageInputsFromPackOutputs(second),
    src4, offset, length,
)

internal fun deriveFrom6388f0LaneLen32Streams(
    first: Libre3LaneBlocks,
    second: Libre3LaneBlocks,
    src4: ByteArray = defaultSrc4(),
    offset: Int = 0,
    length: Int = 0x10,
): ByteArray = deriveFrom6388f0PackLen32Streams(
    builder6388f0PackOutputsFromLaneBlocks(first),
    builder6388f0PackOutputsFromLaneBlocks(second),
    src4, offset, length,
)

internal fun deriveFrom6388f0ScheduleLen32Streams(
    firstScheduleWords: UIntArray,
    secondScheduleWords: UIntArray,
    src4: ByteArray = defaultSrc4(),
    offset: Int = 0,
    length: Int = 0x10,
): ByteArray = deriveFrom6388f0LaneLen32Streams(
    builder6388f0LaneBlocksFromScheduleWords(firstScheduleWords),
    builder6388f0LaneBlocksFromScheduleWords(secondScheduleWords),
    src4, offset, length,
)
