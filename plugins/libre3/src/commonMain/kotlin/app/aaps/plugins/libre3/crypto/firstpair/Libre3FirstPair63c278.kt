package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException

/*
 * The `63c278` schedule builder: three arguments and one scalar in, twenty schedule words out.
 *
 * Ported from LibreCRKit `Crypto/FirstPairSourceSlice.swift` at pin `a86b92f`.
 *
 * The path is: two mixers over 44 word vectors, two tail reducers, an accumulator, a convolution
 * bridge, a third mixer, and then a small branch loop that runs until it reaches its end state.
 */

/** The 44 word vector of a mixer, with the 22 word vector it is mixed against. */
internal class Libre3Vectors63c278(val vec44: ULongArray, val x0Vec22: ULongArray)

/** The four running streams the bridge convolution reads. */
internal class Libre3AccumulatorStreams63c278(
    val sp440Cumulative: ULongArray,
    val sp4f0Words: ULongArray,
    val sp5a0Words: ULongArray,
    val sp390Cumulative: ULongArray,
)

/** The four word streams the branch loop starts from. */
internal class Libre3PrebranchStreams63c278(
    val sp390Static: UIntArray,
    val sp440Words: UIntArray,
    val sp6b0Words: UIntArray,
    val sp658Words: UIntArray,
)

private fun requireArg(arg: ByteArray, label: String) {
    if (arg.size < builder63c278VectorBytes) {
        throw Libre3CryptoException("the 63c278 $label must be at least $builder63c278VectorBytes bytes, not ${arg.size}")
    }
}

private fun requireVec44(vec: ULongArray) {
    if (vec.size != 44) {
        throw Libre3CryptoException("a 63c278 vector must be 44 words, not ${vec.size}")
    }
}

private fun requireVec22(vec: ULongArray) {
    if (vec.size != builder63c278VectorWords) {
        throw Libre3CryptoException("a 63c278 vector must be $builder63c278VectorWords words, not ${vec.size}")
    }
}

internal fun builder63c278InitialVectors(arg0: ByteArray, arg1: ByteArray): Libre3Vectors63c278 {
    requireArg(arg0, "arg0")
    requireArg(arg1, "arg1")
    val tables = Libre3FirstPairTables.get()

    val x1 = ULongArray(44)
    for (index in 0 until builder63c278VectorWords) {
        x1[index] = builder63c278X1Word(readUInt32LE(arg1, index * 4), index, tables)
    }
    for (index in builder63c278VectorWords until 44) {
        x1[index] = 0xb7059a553c133489uL
    }

    val x0 = ULongArray(builder63c278VectorWords) {
        builder63c278X0Word(readUInt32LE(arg0, it * 4), it, tables)
    }
    return Libre3Vectors63c278(x1, x0)
}

internal fun builder63c278SecondInitialVectors(arg0: ByteArray, arg2: ByteArray): Libre3Vectors63c278 {
    requireArg(arg0, "arg0")
    requireArg(arg2, "arg2")
    val tables = Libre3FirstPairTables.get()

    val x2 = ULongArray(44)
    for (index in 0 until builder63c278VectorWords) {
        x2[index] = builder63c278X2Word(readUInt32LE(arg2, index * 4), index, tables)
    }
    for (index in builder63c278VectorWords until 44) {
        x2[index] = 0x9a6e0b3eab651f3duL
    }

    val x0 = ULongArray(builder63c278VectorWords) {
        builder63c278X0BWord(readUInt32LE(arg0, it * 4), it, tables)
    }
    return Libre3Vectors63c278(x2, x0)
}

internal fun builder63c278ScalarMixVector(x1Vec44: ULongArray, x0Vec22: ULongArray, scalar: ULong): ULongArray {
    requireVec44(x1Vec44)
    requireVec22(x0Vec22)
    val tables = Libre3FirstPairTables.get()
    val vec = x1Vec44.copyOf()
    val scalarMul = scalar * 0xc2f49ab55607d661uL + 0x5cd21b4822401581uL
    val scalarAdd = scalar * 0x31979e72b90f9217uL + 0x3a834f793d8d50d2uL

    var carry = vec[0]
    for (index in 0 until builder63c278VectorWords) {
        val seed = builder63c278MixSeed(carry, scalarMul, scalarAdd, tables)
        for (lane in 0 until builder63c278VectorWords) {
            val pos = index + lane
            vec[pos] = vec[pos] + seed.laneAdd + (x0Vec22[lane] * seed.updateMul)
        }
        carry = builder63c278NextCarry(vec[index], vec[index + 1], tables)
        vec[index + 1] = carry
    }
    return vec
}

internal fun builder63c278ScalarMix2Vector(x2Vec44: ULongArray, x0Vec22: ULongArray, scalar: ULong): ULongArray {
    requireVec44(x2Vec44)
    requireVec22(x0Vec22)
    val tables = Libre3FirstPairTables.get()
    val vec = x2Vec44.copyOf()
    val scalarMul = scalar * 0xd499812ba25ee663uL + 0x261ebe70f821cbc3uL
    val scalarAdd = scalar * 0xb1af6fa1cb6e1d69uL + 0xbfe73a2bd6da82dcuL

    var carry = vec[0]
    for (index in 0 until builder63c278VectorWords) {
        val seed = builder63c278Mix2Seed(carry, scalarMul, scalarAdd, tables)
        for (lane in 0 until builder63c278VectorWords) {
            val pos = index + lane
            vec[pos] = vec[pos] + seed.laneAdd + (x0Vec22[lane] * seed.updateMul)
        }
        carry = builder63c278NextCarry2(vec[index], vec[index + 1], tables)
        vec[index + 1] = carry
    }
    return vec
}

internal fun builder63c278Tail1U32Words(mixedVec44: ULongArray): UIntArray {
    requireVec44(mixedVec44)
    val tables = Libre3FirstPairTables.get()
    var carry = 0x57078c52164039c3uL
    val out = UIntArray(builder63c278VectorWords)
    for (index in 0 until builder63c278VectorWords) {
        carry *= 0xea79f5006ed1ed3duL
        carry += mixedVec44[builder63c278VectorWords + index] * 0x66df92deb399335buL
        carry += 0x09c9f7e39169d6f1uL

        val folded = fold63c278(carry * 0x4a61801334a2066buL + 0x346cdb9fa10bc247uL, 0x3019f0, 7, tables)
        val word = carry.toUInt() * 0x6d8d9d63u + folded.toUInt() * 0x70000000u + 0xc780a908u
        val foldedTail = fold63c278(folded, 0x3019f0, 9, tables)
        carry = folded * 0xe3d2a03f1bfe297fuL + foldedTail * 0x401d681000000000uL + 0x7b8480dbcf98c453uL

        val tableOffset = (index and 7) * 4
        val mul = u32TableWord63c278(0x123448 + tableOffset, tables)
        val add = u32TableWord63c278(0x123468 + tableOffset, tables)
        out[index] = word * mul + add
    }
    return out
}

internal fun builder63c278Tail2U32Words(mixedVec44: ULongArray): UIntArray {
    requireVec44(mixedVec44)
    val tables = Libre3FirstPairTables.get()
    var carry = 0x7b98879460aee9e2uL
    val out = UIntArray(builder63c278VectorWords)
    for (index in 0 until builder63c278VectorWords) {
        carry *= 0xf65fd3833526aa13uL
        carry += mixedVec44[builder63c278VectorWords + index] * 0x806aa29ec1ed1481uL
        carry += 0xb4f29f8797e744b7uL

        val folded = fold63c278(carry * 0xa05b2cf659a43c93uL + 0x48da81dd905ece62uL, 0x301cf0, 7, tables)
        val word = carry.toUInt() * 0x0080b9a9u + folded.toUInt() * 0xd0000000u + 0xde4d224eu
        val foldedTail = fold63c278(folded, 0x301cf0, 9, tables)
        carry = folded * 0x3fc1e03941c67b59uL + foldedTail * 0xe3984a7000000000uL + 0x05975fb8f5057bb2uL

        val tableOffset = (index and 7) * 4
        val mul = u32TableWord63c278(0x112628 + tableOffset, tables)
        val add = u32TableWord63c278(0x121928 + tableOffset, tables)
        out[index] = word * mul + add
    }
    return out
}

internal fun builder63c278AccumulatorStreams(arg2: ByteArray, tail2Words: UIntArray): Libre3AccumulatorStreams63c278 {
    requireArg(arg2, "arg2")
    requireVectorWords(listOf(tail2Words))
    val tables = Libre3FirstPairTables.get()

    val sp5a0 = ULongArray(builder63c278VectorWords)
    val sp440 = ULongArray(builder63c278VectorWords)
    var runningA = 0uL
    for (index in 0 until builder63c278VectorWords) {
        val item = builder63c278AccumAWord(readUInt32LE(arg2, index * 4), index, tables)
        sp5a0[index] = item
        runningA += item
        sp440[index] = runningA
    }

    val sp4f0 = ULongArray(builder63c278VectorWords)
    val sp390 = ULongArray(builder63c278VectorWords)
    var runningB = 0uL
    for (index in tail2Words.indices) {
        val item = builder63c278AccumBWord(tail2Words[index], index, tables)
        sp4f0[index] = item
        runningB += item
        sp390[index] = runningB
    }

    return Libre3AccumulatorStreams63c278(sp440, sp4f0, sp5a0, sp390)
}

internal fun builder63c278BridgeConvolutionVector(streams: Libre3AccumulatorStreams63c278): ULongArray {
    for (stream in listOf(streams.sp440Cumulative, streams.sp4f0Words, streams.sp5a0Words, streams.sp390Cumulative)) {
        if (stream.size != builder63c278VectorWords) {
            throw Libre3CryptoException("a 63c278 stream must be $builder63c278VectorWords words, not ${stream.size}")
        }
    }

    val out = ULongArray(44)
    for (index in 0 until 44) {
        val low = maxOf(index - 21, 0)
        val high = minOf(index, 21)
        // The Swift keeps this guard even though `low` can never be above 21 for these sizes.
        val mixed: ULong = if (low > 21) {
            0x67bdf132221fb4e9uL
        } else {
            val start = index - high
            var dot = 0uL
            for (pos in start..high) {
                dot += streams.sp4f0Words[index - pos] * streams.sp5a0Words[pos]
            }

            var spanA = streams.sp440Cumulative[high]
            val spanB: ULong
            if (index >= 22) {
                spanA -= streams.sp440Cumulative[low - 1]
                spanB = streams.sp390Cumulative[index - low] - streams.sp390Cumulative[index - high - 1]
            } else {
                spanB = streams.sp390Cumulative[index - low]
            }

            0x67bdf132221fb4e9uL +
                (high - low + 1).toULong() * 0x1593d040a4114154uL +
                dot * 0x2edc06a97199e3efuL +
                spanA * 0x0557cced2c1cc47euL +
                spanB * 0xc1edf977b66f09cauL
        }
        out[index] = mixed * 0xb3bd694c1c94d1a7uL + 0x2c0585771e81c36auL
    }
    return out
}

internal fun builder63c278BridgeX0Vector(arg0: ByteArray): ULongArray {
    requireArg(arg0, "arg0")
    val tables = Libre3FirstPairTables.get()
    return ULongArray(builder63c278VectorWords) {
        builder63c278BridgeX0Word(readUInt32LE(arg0, it * 4), it, tables)
    }
}

internal fun builder63c278BridgeMixVector(sp230Vec44: ULongArray, x0Vec22: ULongArray, scalar: ULong): ULongArray {
    requireVec44(sp230Vec44)
    requireVec22(x0Vec22)
    val tables = Libre3FirstPairTables.get()
    val vec = sp230Vec44.copyOf()
    val scalarMul = scalar * 0x5bcfc2db5b41aa8buL + 0xb0be584b9c560cebuL
    val scalarAdd = scalar * 0x7cb9da0648140cfduL + 0xcb165f95963e265buL

    var carry = vec[0]
    for (index in 0 until builder63c278VectorWords) {
        val seed = builder63c278BridgeMixSeed(carry, scalarMul, scalarAdd, tables)
        for (lane in 0 until builder63c278VectorWords) {
            val pos = index + lane
            vec[pos] = vec[pos] + seed.laneAdd + (x0Vec22[lane] * seed.updateMul)
        }
        carry = builder63c278BridgeNextCarry(vec[index], vec[index + 1], tables)
        vec[index + 1] = carry
    }
    return vec
}

internal fun builder63c278BridgeSP128Words(sp230Vec44: ULongArray): UIntArray {
    requireVec44(sp230Vec44)
    val tables = Libre3FirstPairTables.get()
    var carry = 0x18541ef2e5658ac6uL
    val out = UIntArray(builder63c278VectorWords)
    for (index in 0 until builder63c278VectorWords) {
        carry *= 0x590b8c9bda7aa7a5uL
        carry += sp230Vec44[builder63c278VectorWords + index] * 0x93e68b973b124f01uL
        carry += 0x0357d31d6340b07auL

        val folded = fold63c278(carry * 0x052e2e9b238ffd17uL + 0x7a84d77fc047bb5cuL, 0x302070, 7, tables)
        val word = carry.toUInt() * 0xbca742b5u + folded.toUInt() * 0xd0000000u + 0x74c20619u
        val foldedTail = fold63c278(folded, 0x302070, 9, tables)
        carry = folded * 0x9d12b2b955ef375buL + foldedTail * 0xa10c8a5000000000uL + 0x5831e87503aab765uL

        val tableOffset = (index and 7) * 4
        val mul = u32TableWord63c278(0x118568 + tableOffset, tables)
        val add = u32TableWord63c278(0x1234a8 + tableOffset, tables)
        out[index] = word * mul + add
    }
    return out
}

internal fun builder63c278PrebranchInitialStreams(
    arg0: ByteArray,
    tail1Words: UIntArray,
    sp128Words: UIntArray,
): Libre3PrebranchStreams63c278 {
    requireArg(arg0, "arg0")
    requireVectorWords(listOf(tail1Words, sp128Words))
    val tables = Libre3FirstPairTables.get()

    val sp390 = UIntArray(builder63c278VectorWords)
    val sp440 = UIntArray(builder63c278VectorWords)
    val sp6b0 = UIntArray(builder63c278VectorWords)
    val sp658 = UIntArray(builder63c278VectorWords)

    for (index in 0 until builder63c278VectorWords) {
        val tableOffset = (index and 7) * 4
        sp390[index] = foldTableU32Word63c278(0x3020f0 + index * 4, tables)
        sp440[index] = u32TableAffine63c278(tail1Words[index], 0x122228 + tableOffset, 0x11dd08 + tableOffset, tables)
        sp6b0[index] = u32TableAffine63c278(sp128Words[index], 0x1234c8 + tableOffset, 0x11b428 + tableOffset, tables)
        sp658[index] = u32TableAffine63c278(readUInt32LE(arg0, index * 4), 0x114968 + tableOffset, 0x11fda8 + tableOffset, tables)
    }

    return Libre3PrebranchStreams63c278(sp390, sp440, sp6b0, sp658)
}

internal fun builder63c278PrebranchSP4F0Words(arg0: ByteArray): UIntArray {
    requireArg(arg0, "arg0")
    val tables = Libre3FirstPairTables.get()
    val arg0Words = arg0Words63c278(arg0)

    val first = arg0Words[0] * 0x7193fc77u + 0x318e9b49u
    var state = builder63c278PrebranchSP4F0State(first, tables)
    var selected = builder63c278PrebranchSP4F0FoldState(state, tables)
    var carry = state * 0x8e834ce3u + (selected shl 31) + 0x0afac599u

    val out = UIntArray(builder63c278VectorWords)
    var index = 0
    while (true) {
        if (index == builder63c278VectorWords - 1) {
            val storeValue = carry * 0x3f277405u + 0xa0c1d6f4u
            val tableOffset = (index * 4) and 0x1c
            out[index] = u32TableAffine63c278(storeValue, 0x122248 + tableOffset, 0x1172a8 + tableOffset, tables)
            break
        }

        val nextIndex = index + 1
        val nextTableOffset = (nextIndex and 7) * 4
        val value = u32TableAffine63c278(
            arg0Words[nextIndex], 0x11c3e8 + nextTableOffset, 0x11b448 + nextTableOffset, tables,
        )
        carry = carry * 0x3f277405u + value * 0xa8000000u
        state = builder63c278PrebranchSP4F0State(value, tables)
        selected = builder63c278PrebranchSP4F0FoldState(state, tables)
        val word = state * 0x8e834ce3u + (selected shl 31) + 0x0afac599u
        carry = word * 0xb0000000u + carry
        val storeValue = carry + 0xc8c1d6f4u
        val tableOffset = (index * 4) and 0x1c
        out[index] = u32TableAffine63c278(storeValue, 0x122248 + tableOffset, 0x1172a8 + tableOffset, tables)
        carry = word
        index = nextIndex
    }

    return out
}

internal fun builder63c278PrebranchSP230Words(sp4f0Words: UIntArray): UIntArray {
    requireVectorWords(listOf(sp4f0Words))
    val tables = Libre3FirstPairTables.get()
    val q0 = UIntArray(4) { u32TableWord63c278(0x125f20 + it * 4, tables) }
    val q1 = UIntArray(4) { u32TableWord63c278(0x125f30 + it * 4, tables) }
    val out = q0 + q1 + q0 + q1 + q0 + q1.copyOfRange(0, 2)

    for ((index, word) in sp4f0Words.withIndex()) {
        val tableOffset = (index and 7) * 4
        val mul = u32TableWord63c278(0x11c408 + tableOffset, tables)
        out[index] = word * mul + out[index]
    }

    for (index in 0 until builder63c278VectorWords) {
        val tableOffset = (index and 7) * 4
        val staticWord = foldTableU32Word63c278(0x302148 + index * 4, tables)
        val mul = u32TableWord63c278(0x118588 + tableOffset, tables)
        out[index] = staticWord * mul + out[index]
    }

    return out
}

internal fun builder63c278PrebranchSP5A0Words(sp230Words: UIntArray): UIntArray {
    requireVectorWords(listOf(sp230Words))
    val tables = Libre3FirstPairTables.get()
    var carry = 0xa7964b7du
    val out = UIntArray(builder63c278VectorWords)
    for ((index, word) in sp230Words.withIndex()) {
        carry = carry * 0x856c3a53u + word
        var folded = carry * 0x287caef9u + 0x0ac0f465u
        folded = fold32ByNibbles63c278(folded, 0x302238, 7, tables)
        val foldedTail = foldTableU32Word63c278(0x302238 + (folded and 0x0fu).toInt() * 4, tables) + (folded shr 4)
        val nextPart = carry * 0xd8018ba1u + folded * 0x70000000u + 0x63f7e16au
        val tail = folded * 0x20718073u + foldedTail * 0xf8e7f8d0u

        val tableOffset = (index * 4) and 0x1c
        out[index] = u32TableAffine63c278(nextPart, 0x11a028 + tableOffset, 0x115f48 + tableOffset, tables)
        carry = tail + 0xe056c4a1u
    }
    return out
}

/**
 * The branch loop, which runs until the `sp658` stream reaches its end state.
 *
 * @param maxIterations a stop, so a wrong input cannot make the app hang. The original uses the
 *   same guard.
 */
internal fun builder63c278BranchLoop(
    arg0: ByteArray,
    streams: Libre3PrebranchStreams63c278,
    sp5a0Words: UIntArray,
    maxIterations: Int = 2000,
): Libre3PrebranchStreams63c278 {
    requireArg(arg0, "arg0")
    requireVectorWords(
        listOf(streams.sp390Static, streams.sp440Words, streams.sp6b0Words, streams.sp658Words, sp5a0Words)
    )
    val tables = Libre3FirstPairTables.get()
    var sp390 = streams.sp390Static
    var sp440 = streams.sp440Words
    var sp6b0 = streams.sp6b0Words
    var sp658 = streams.sp658Words

    repeat(maxIterations) {
        if (builder63c278TerminalSP658Ready(sp658, tables)) {
            return Libre3PrebranchStreams63c278(sp390, sp440, sp6b0, sp658)
        }

        if (sp6b0[0] and 1u == 0u) {
            sp6b0 = builder63c278LoopUpdateSP6B0Even(sp6b0, tables)
            sp440 = builder63c278LoopUpdateSP440(sp440, sp5a0Words, tables)
            return@repeat
        }

        while (sp658[0] and 1u != 0u) {
            sp658 = builder63c278LoopUpdateSP658Odd(sp658, tables)
            sp390 = builder63c278LoopUpdateSP390(sp390, sp5a0Words, tables)
        }

        if (builder63c278LoopSP658EvenUsesSuccessPath(sp658, sp6b0, tables)) {
            sp658 = builder63c278LoopUpdateSP658EvenSuccess(sp658, sp6b0, tables)
            if (builder63c278Predicate64D55C(sp440, sp390, tables) == 0) {
                sp390 = builder63c278LoopUpdateSP390PredicateFalse(sp390, arg0, tables)
            }
            sp390 = builder63c278LoopUpdateSP390PredicateJoin(sp390, sp440, tables)
        } else {
            sp6b0 = builder63c278LoopUpdateSP6B0Failure(sp6b0, sp658, tables)
            if (builder63c278Predicate64D55C(sp440, sp390, tables) != 0) {
                sp440 = builder63c278LoopUpdateSP440PredicateTrue(sp440, arg0, tables)
            }
            sp440 = builder63c278LoopUpdateSP440PredicateJoin(sp440, sp390, tables)
        }
    }

    throw Libre3CryptoException("the 63c278 branch loop did not settle in $maxIterations rounds")
}

internal fun builder63c278FinalScheduleFromSP440U32(sp440Words: UIntArray): UIntArray {
    if (sp440Words.size < builder63c278VectorWords) {
        throw Libre3CryptoException("a 63c278 stream must be $builder63c278VectorWords words, not ${sp440Words.size}")
    }
    val tables = Libre3FirstPairTables.get()
    val staged = UIntArray(builder63c278VectorWords) {
        val tableOffset = (it * 4) and 0x1c
        u32TableAffine63c278(sp440Words[it], 0x117308 + tableOffset, 0x11b508 + tableOffset, tables)
    }

    return UIntArray(20) {
        val tableOffset = (it * 4) and 0x1c
        u32TableAffine63c278(staged[it], 0x11f5e8 + tableOffset, 0x1154e8 + tableOffset, tables)
    }
}

/** The whole `63c278` builder, from three arguments and a scalar to twenty schedule words. */
internal fun builder63c278ScheduleWords(arg0: ByteArray, arg1: ByteArray, arg2: ByteArray, scalar: ULong): UIntArray {
    val initial = builder63c278InitialVectors(arg0, arg1)
    val mixed = builder63c278ScalarMixVector(initial.vec44, initial.x0Vec22, scalar)
    val tail1 = builder63c278Tail1U32Words(mixed)

    val second = builder63c278SecondInitialVectors(arg0, arg2)
    val mixed2 = builder63c278ScalarMix2Vector(second.vec44, second.x0Vec22, scalar)
    val tail2 = builder63c278Tail2U32Words(mixed2)

    val accum = builder63c278AccumulatorStreams(arg2, tail2)
    val bridge = builder63c278BridgeConvolutionVector(accum)
    val bridgeX0 = builder63c278BridgeX0Vector(arg0)
    val bridgeMixed = builder63c278BridgeMixVector(bridge, bridgeX0, scalar)
    val sp128 = builder63c278BridgeSP128Words(bridgeMixed)

    val prebranch = builder63c278PrebranchInitialStreams(arg0, tail1, sp128)
    val pre4f0 = builder63c278PrebranchSP4F0Words(arg0)
    val pre230 = builder63c278PrebranchSP230Words(pre4f0)
    val pre5a0 = builder63c278PrebranchSP5A0Words(pre230)
    val settled = builder63c278BranchLoop(arg0, prebranch, pre5a0)
    return builder63c278FinalScheduleFromSP440U32(settled.sp440Words)
}

/** Two `63c278` schedules joined into one slice of the answer. */
@Suppress("LongParameterList")
internal fun deriveFrom63c278ScheduleInputs(
    arg0: ByteArray,
    firstArg1: ByteArray,
    firstArg2: ByteArray,
    secondArg1: ByteArray,
    secondArg2: ByteArray,
    scalar: ULong,
    src4: ByteArray = defaultSrc4(),
    offset: Int = 0,
    length: Int = 0x10,
): ByteArray = deriveFrom6388f0ScheduleLen32Streams(
    builder63c278ScheduleWords(arg0, firstArg1, firstArg2, scalar),
    builder63c278ScheduleWords(arg0, secondArg1, secondArg2, scalar),
    src4, offset, length,
)

/** The same, with the fixed argument and scalar that the first pairing uses. */
internal fun deriveFromPre63c278ScheduleInputs(
    firstArg1: ByteArray,
    firstArg2: ByteArray,
    secondArg1: ByteArray,
    secondArg2: ByteArray,
    src4: ByteArray = defaultSrc4(),
    offset: Int = 0,
    length: Int = 0x10,
): ByteArray = deriveFrom63c278ScheduleInputs(
    pre63c278Arg0Source, firstArg1, firstArg2, secondArg1, secondArg2, pre63c278Scalar, src4, offset, length,
)
