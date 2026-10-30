package app.aaps.plugins.libre3.crypto

/**
 * Turns the 66 byte pairing source into the 16 byte key that protects the Phase 5 message.
 *
 * Ported from LibreCRKit `Crypto/Phase5KeySchedule.swift` at pin `a86b92f`, itself a clean room
 * port of the sensor maker's own routine.
 *
 * The routine is a very small machine: it walks a list of calls, each call feeding bytes through a
 * lookup table with a running state, and the last step reads sixteen bytes out of a fixed region.
 * The numbers below are addresses and offsets of that routine and mean nothing on their own.
 */
object Libre3Phase5KeySchedule {

    /** The source this routine expects. */
    const val SOURCE_SIZE = 66

    /**
     * @param source66 the 66 byte source that the first pairing produced.
     * @return the 16 byte key for `Libre3LibAes`.
     * @throws Libre3CryptoException on a wrong size or a missing table.
     */
    fun deriveRawKey(source66: ByteArray): ByteArray {
        if (source66.size != SOURCE_SIZE) {
            throw Libre3CryptoException("the pairing source must be 66 bytes, not ${source66.size}")
        }
        val sbox = table("sbox_19bit_lib_986819.bin", 0x80000)
        val region = table("phase5_keysched_region_274000.bin", REGION_LENGTH)
        val program = region.copyOfRange(PROGRAM_BASE - REGION_BASE, PROGRAM_BASE - REGION_BASE + 0x1000)

        // Step one: the whole source through the machine once.
        val inputBuffer = ByteArray(0x90)
        val stage1 = runWriting(sbox, source66, source66, program.copyOfRange(STAGE1_OFFSET, STAGE1_OFFSET + SOURCE_SIZE), SOURCE_SIZE)
        stage1.copyInto(inputBuffer, 0)

        // Step two: a fixed list of calls, each writing its answer into the working area.
        val stack = ByteArray(0x180)
        for (call in PHASE1_CALLS) {
            val totalRead = when (call.op) {
                Op.D -> 6
                Op.B -> call.countA + call.countB
                Op.A -> call.countB
            }
            val src1 = sourceBytes(call.src1, inputBuffer, stack, totalRead)
            val src2 = sourceBytes(call.src2, inputBuffer, stack, totalRead)
            val programPart = program.copyOfRange(call.programOffset, call.programOffset + totalRead)
            val out = when (call.op) {
                Op.A -> runWriting(sbox, src1, src2, programPart, call.countB)
                Op.B -> runSkippingThenWriting(sbox, src1, src2, programPart, call.countA, call.countB, 0)
                Op.D -> runWriting(sbox, src1, src2, programPart, 6)
            }
            out.copyInto(stack, call.destination)
        }

        // Step three: sixteen rounds, each squeezing six bytes down to one.
        val squeezed = ByteArray(16)
        for (iteration in 0 until 16) {
            val chunkOffset = PHASE2_CHUNK_OFFSETS[iteration]
            for (round in 0 until 4) {
                val src1Offset = if (round == 0) chunkOffset else PHASE2_SCRATCH_SOURCES[round]
                val src1 = stack.copyOfRange(src1Offset, src1Offset + 6)
                val src2Offset = PHASE2_SRC2_ADDRESSES[iteration][round] - REGION_BASE
                val src2 = region.copyOfRange(src2Offset, src2Offset + 6)
                val programOffset = PHASE2_PROGRAM_OFFSETS[iteration][round]
                val out = runWriting(sbox, src1, src2, program.copyOfRange(programOffset, programOffset + 6), 6)
                out.copyInto(stack, PHASE2_SCRATCH_DESTINATIONS[round])
            }
            val compressed = squeeze(stack.copyOfRange(SQUEEZE_ARG, SQUEEZE_ARG + 6), sbox, program, region)
            squeezed[iteration] = compressed
            stack[SQUEEZE_ACCUMULATOR] = compressed
        }

        // Step four: each squeezed byte picks one key byte out of the region.
        val key = ByteArray(16)
        for (iteration in 0 until 16) {
            val tableOffset = if (iteration == 0) 0 else iteration * 0x100 + 0x40
            val offset = POST_LOOP_BASE - REGION_BASE + tableOffset + (squeezed[iteration].toInt() and 0xFF)
            key[KEY_POSITION[iteration]] = region[offset]
        }
        return key
    }

    /** Feeds every byte through the table and keeps every answer. */
    private fun runWriting(sbox: ByteArray, src1: ByteArray, src2: ByteArray, program: ByteArray, length: Int): ByteArray {
        var state = 0
        val out = ByteArray(length)
        for (i in 0 until length) {
            state = step(state, src1[i], src2[i], program[i], sbox)
            out[i] = (state and 7).toByte()
        }
        return out
    }

    /** Feeds the first bytes only to move the state on, then keeps the answers of the rest. */
    private fun runSkippingThenWriting(
        sbox: ByteArray,
        src1: ByteArray,
        src2: ByteArray,
        program: ByteArray,
        skip: Int,
        keep: Int,
        tail: Int,
    ): ByteArray {
        var state = 0
        for (i in 0 until skip) state = step(state, src1[i], src2[i], program[i], sbox)

        val out = ByteArray(keep + tail)
        for (i in 0 until keep) {
            val index = skip + i
            state = step(state, src1[index], src2[index], program[index], sbox)
            out[i] = (state and 7).toByte()
        }
        for (i in 0 until tail) {
            val index = skip + keep + i
            val sboxIndex = ((state and 0xF8) or ((program[index].toInt() and 0xFF) shl 11)) and 0x7FFFF
            state = sbox[sboxIndex].toInt() and 0xFF
            out[keep + i] = (state and 7).toByte()
        }
        return out
    }

    /** One move of the machine: three bytes and the running state pick the next state. */
    private fun step(state: Int, src1: Byte, src2: Byte, program: Byte, sbox: ByteArray): Int {
        val index = (
            ((state and 0xF8) xor (src1.toInt() and 0xFF)) or
                (((src2.toInt() and 0xFF) shl 8) xor ((program.toInt() and 0xFF) shl 11))
            ) and 0x7FFFF
        return sbox[index].toInt() and 0xFF
    }

    /** Squeezes six bytes down to one. */
    private fun squeeze(arg: ByteArray, sbox: ByteArray, program: ByteArray, region: ByteArray): Byte {
        val scratch = scramble(arg, sbox)
        val firstProgram = program.copyOfRange(SQUEEZE_A1, SQUEEZE_A1 + 4)
        val secondProgram = program.copyOfRange(SQUEEZE_A2, SQUEEZE_A2 + 4)

        val kept = runWriting(sbox, scratch.copyOfRange(0, 4), scratch.copyOfRange(0, 4), firstProgram, 4)

        val mixLength = SQUEEZE_B_SKIP + SQUEEZE_B_KEEP + SQUEEZE_B_TAIL
        val mixed = runSkippingThenWriting(
            sbox, scratch, scratch, program.copyOfRange(SQUEEZE_B, SQUEEZE_B + mixLength),
            SQUEEZE_B_SKIP, SQUEEZE_B_KEEP, SQUEEZE_B_TAIL,
        )
        val afterMix = scratch.copyOf()
        mixed.copyInto(afterMix, 0)

        val low = runWriting(sbox, kept, kept, secondProgram, 4)
        val lowIndex = (low[2].toInt() and 0xFF) xor ((low[3].toInt() and 0xFF) shl 3)
        val lowByte = region[SQUEEZE_SBOX + lowIndex].toInt() and 0xFF

        val second = afterMix.copyOfRange(0, 4)
        val keptAgain = runWriting(sbox, second, second, firstProgram, 4)
        val high = runWriting(sbox, keptAgain, keptAgain, secondProgram, 4)
        val highIndex = (high[2].toInt() and 0xFF) xor ((high[3].toInt() and 0xFF) shl 3)
        val highByte = region[SQUEEZE_SBOX + highIndex].toInt() and 0xFF

        return ((highByte and 0xF0) or (lowByte and 0x0F)).toByte()
    }

    /** Mixes six bytes through the wide half of the table. */
    private fun scramble(arg: ByteArray, sbox: ByteArray): ByteArray {
        val out = ByteArray(6)
        fun at(index: Int) = arg[index].toInt() and 0xFF

        var x = halfword(sbox, at(0) + 0x2000)
        x = halfword(sbox, ((x and 0xFF8) xor at(1)) or 0x2000)
        x = halfword(sbox, ((x and 0xFF8) xor at(2)) or 0x2000)
        x = halfword(sbox, ((x and 0xFF8) xor at(3)) or 0x4000)
        out[0] = (x and 7).toByte()

        x = halfword(sbox, (((x and 0xFF8) xor at(4)) or 0x21000) + 0xD000)
        out[1] = (x and 7).toByte()
        x = halfword(sbox, ((x and 0xFF8) xor at(5)) or 0x21000)
        out[2] = (x and 7).toByte()

        x = halfword(sbox, (x xor at(2)) xor 0x6000)
        out[3] = (x and 7).toByte()
        x = halfword(sbox, (x xor at(3)) xor 0x2000)
        out[4] = (x and 7).toByte()
        x = halfword(sbox, (x xor at(4)) xor 0x4000)
        out[5] = (x and 7).toByte()
        return out
    }

    /** Two bytes of the wide half of the table, read as one number. */
    private fun halfword(sbox: ByteArray, index: Int): Int {
        val offset = SCRAMBLER_OFFSET + index * 2
        if (offset < 0 || offset + 1 >= sbox.size) {
            throw Libre3CryptoException("the table is too small for this routine")
        }
        return (sbox[offset].toInt() and 0xFF) or ((sbox[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun sourceBytes(source: Src, inputBuffer: ByteArray, stack: ByteArray, count: Int): ByteArray =
        when (source) {
            is Src.Input -> inputBuffer.copyOfRange(0, count)
            is Src.Stack -> stack.copyOfRange(source.offset, source.offset + count)
        }

    private fun table(name: String, expectedSize: Int): ByteArray {
        val bytes = Libre3RuntimeTables.load(name)
            ?: throw Libre3CryptoException("this build does not ship the table $name")
        if (bytes.size != expectedSize) {
            throw Libre3CryptoException("the table $name is ${bytes.size} bytes, not $expectedSize")
        }
        return bytes
    }

    private enum class Op { A, B, D }

    private sealed interface Src {

        data object Input : Src
        data class Stack(val offset: Int) : Src
    }

    private class Call(
        val op: Op,
        val programOffset: Int,
        val countA: Int,
        val countB: Int,
        val src1: Src,
        val src2: Src,
        val destination: Int,
    )

    private const val REGION_BASE = 0x274000
    private const val REGION_LENGTH = 0x2000
    private const val PROGRAM_BASE = 0x274624
    private const val STAGE1_OFFSET = 0x124
    private const val SQUEEZE_SBOX = 0x9d3
    private const val POST_LOOP_BASE = 0x274a13
    private const val SCRAMBLER_OFFSET = 0x20001

    private const val SQUEEZE_A1 = 465
    private const val SQUEEZE_B = 194
    private const val SQUEEZE_B_SKIP = 2
    private const val SQUEEZE_B_KEEP = 4
    private const val SQUEEZE_B_TAIL = 2
    private const val SQUEEZE_A2 = 833
    private const val SQUEEZE_ARG = 0xd6
    private const val SQUEEZE_ACCUMULATOR = 0x104

    private val PHASE2_SCRATCH_DESTINATIONS = intArrayOf(0xb4, 0x92, 0xe8, 0xd6)
    private val PHASE2_SCRATCH_SOURCES = intArrayOf(0, 0xb4, 0x92, 0xe8)

    private val PHASE2_CHUNK_OFFSETS = intArrayOf(
        0x8c, 0x86, 0x80, 0x7a, 0x74, 0x6e, 0x68, 0x62,
        0x5c, 0x56, 0x50, 0x4a, 0x44, 0x3e, 0x38, 0x32,
    )

    private val PHASE2_PROGRAM_OFFSETS = arrayOf(
        intArrayOf(611, 364, 805, 382), intArrayOf(581, 208, 569, 160),
        intArrayOf(124, 136, 765, 737), intArrayOf(106, 112, 370, 18),
        intArrayOf(731, 523, 425, 843), intArrayOf(553, 895, 675, 715),
        intArrayOf(487, 24, 398, 879), intArrayOf(12, 743, 821, 30),
        intArrayOf(669, 657, 873, 118), intArrayOf(547, 517, 587, 188),
        intArrayOf(166, 0, 182, 286), intArrayOf(837, 36, 651, 867),
        intArrayOf(404, 202, 663, 376), intArrayOf(605, 130, 575, 681),
        intArrayOf(6, 599, 749, 499), intArrayOf(645, 493, 358, 541),
    )

    private val PHASE2_SRC2_ADDRESSES = arrayOf(
        intArrayOf(0x2749bb, 0x2749c1, 0x2749c7, 0x2749cd),
        intArrayOf(0x275a53, 0x275a59, 0x275a5f, 0x275a65),
        intArrayOf(0x275a6b, 0x275a71, 0x275a77, 0x275a7d),
        intArrayOf(0x275a83, 0x275a89, 0x275a8f, 0x275a95),
        intArrayOf(0x275a9b, 0x275aa1, 0x275aa7, 0x275aad),
        intArrayOf(0x275ab3, 0x275ab9, 0x275abf, 0x275ac5),
        intArrayOf(0x275acb, 0x275ad1, 0x275ad7, 0x275add),
        intArrayOf(0x275ae3, 0x275ae9, 0x275aef, 0x275af5),
        intArrayOf(0x275afb, 0x275b01, 0x275b07, 0x275b0d),
        intArrayOf(0x275b13, 0x275b19, 0x275b1f, 0x275b25),
        intArrayOf(0x275b2b, 0x275b31, 0x275b37, 0x275b3d),
        intArrayOf(0x275b43, 0x275b49, 0x275b4f, 0x275b55),
        intArrayOf(0x275b5b, 0x275b61, 0x275b67, 0x275b6d),
        intArrayOf(0x275b73, 0x275b79, 0x275b7f, 0x275b85),
        intArrayOf(0x275b8b, 0x275b91, 0x275b97, 0x275b9d),
        intArrayOf(0x275ba3, 0x275ba9, 0x275baf, 0x275bb5),
    )

    private val KEY_POSITION = intArrayOf(3, 2, 1, 0, 7, 6, 5, 4, 11, 10, 9, 8, 15, 14, 13, 12)

    private val PHASE1_CALLS = listOf(
        Call(Op.A, 0x1af, 0, 34, Src.Input, Src.Input, 0xb4),
        Call(Op.B, 0x0d6, 32, 34, Src.Input, Src.Input, 0x92),
        Call(Op.A, 0x269, 0, 18, Src.Stack(0xb4), Src.Stack(0xb4), 0xe8),
        Call(Op.B, 0x303, 16, 18, Src.Stack(0xb4), Src.Stack(0xb4), 0xd6),
        Call(Op.A, 0x02a, 0, 10, Src.Stack(0xe8), Src.Stack(0xe8), 0x104),
        Call(Op.B, 0x1d5, 8, 10, Src.Stack(0xe8), Src.Stack(0xe8), 0xfa),
        Call(Op.D, 0x211, 0, 6, Src.Stack(0x104), Src.Stack(0x104), 0x32),
        Call(Op.B, 0x32b, 4, 6, Src.Stack(0x104), Src.Stack(0x104), 0x38),
        Call(Op.D, 0x1f9, 0, 6, Src.Stack(0xfa), Src.Stack(0xfa), 0x3e),
        Call(Op.B, 0x2d1, 4, 6, Src.Stack(0xfa), Src.Stack(0xfa), 0x44),
        Call(Op.A, 0x0ac, 0, 10, Src.Stack(0xd6), Src.Stack(0xd6), 0x104),
        Call(Op.B, 0x2b9, 8, 10, Src.Stack(0xd6), Src.Stack(0xd6), 0xfa),
        Call(Op.D, 0x1a3, 0, 6, Src.Stack(0x104), Src.Stack(0x104), 0x4a),
        Call(Op.B, 0x034, 4, 6, Src.Stack(0x104), Src.Stack(0x104), 0x50),
        Call(Op.D, 0x217, 0, 6, Src.Stack(0xfa), Src.Stack(0xfa), 0x56),
        Call(Op.B, 0x27b, 4, 6, Src.Stack(0xfa), Src.Stack(0xfa), 0x5c),
        Call(Op.A, 0x08e, 0, 18, Src.Stack(0x92), Src.Stack(0x92), 0xe8),
        Call(Op.B, 0x03e, 16, 18, Src.Stack(0x92), Src.Stack(0x92), 0xd6),
        Call(Op.A, 0x22f, 0, 10, Src.Stack(0xe8), Src.Stack(0xe8), 0x104),
        Call(Op.B, 0x351, 8, 10, Src.Stack(0xe8), Src.Stack(0xe8), 0xfa),
        Call(Op.D, 0x1ff, 0, 6, Src.Stack(0x104), Src.Stack(0x104), 0x62),
        Call(Op.B, 0x375, 4, 6, Src.Stack(0x104), Src.Stack(0x104), 0x68),
        Call(Op.D, 0x33b, 0, 6, Src.Stack(0xfa), Src.Stack(0xfa), 0x6e),
        Call(Op.B, 0x2af, 4, 6, Src.Stack(0xfa), Src.Stack(0xfa), 0x74),
        Call(Op.A, 0x184, 0, 10, Src.Stack(0xd6), Src.Stack(0xd6), 0x104),
        Call(Op.B, 0x385, 8, 10, Src.Stack(0xd6), Src.Stack(0xd6), 0xfa),
        Call(Op.D, 0x118, 0, 6, Src.Stack(0x104), Src.Stack(0x104), 0x7a),
        Call(Op.B, 0x2f3, 4, 6, Src.Stack(0x104), Src.Stack(0x104), 0x80),
        Call(Op.D, 0x251, 0, 6, Src.Stack(0xfa), Src.Stack(0xfa), 0x86),
        Call(Op.B, 0x060, 4, 6, Src.Stack(0xfa), Src.Stack(0xfa), 0x8c),
    )
}
