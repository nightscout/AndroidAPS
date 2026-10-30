package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException

/*
 * The word makers and the branch loop steps of the `63c278` schedule builder.
 *
 * Ported from LibreCRKit `Crypto/FirstPairSourceSlice.swift` at pin `a86b92f`.
 *
 * Every routine here has the same shape: take a word, run it through a table driven affine step,
 * fold it a fixed number of times, then mix the folded value back in. Only the numbers differ.
 */

/** What one round of a mixer produced. */
internal class MixSeed(val updateMul: ULong, val laneAdd: ULong)

internal fun builder63c278X1Word(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong {
    val w0 = u32Affine63c278(word, index, 0x115488, 0x121908, tables)
    val w = w0 * 0x30316c9du + 0xe533e221u
    var folded = w.toULong() * 0x74ddf8a53c239debuL + 0xc98ef94d2aa6d2f9uL
    folded = fold63c278(folded, 0x301770, 8, tables)
    folded *= 0xff444fcf00000000uL
    return w.toULong() * 0xdda5a8135a0bc9fbuL + folded + 0x8031c96ed30bf85euL
}

internal fun builder63c278X0Word(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong {
    val w0 = u32Affine63c278(word, index, 0x11a9a8, 0x122aa8, tables)
    val w = w0 * 0x707fe555u + 0x1d759ee3u
    var folded = w.toULong() * 0xc7e623dc4156435duL + 0xa7268272249650e4uL
    folded = fold63c278(folded, 0x3017f0, 8, tables)
    folded *= 0xd70f3ef300000000uL
    return w.toULong() * 0x1defa278095a88b9uL + folded + 0xc8066dafe659e3dduL
}

internal fun builder63c278X2Word(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong {
    val w0 = u32Affine63c278(word, index, 0x11f588, 0x123488, tables)
    val w = w0 * 0xe41d161fu + 0xb12fcee1u
    var folded = w.toULong() * 0xf3402af2c5c78103uL + 0x81b5a02882be6230uL
    folded = fold63c278(folded, 0x301a70, 8, tables)
    folded *= 0xc69af5ab00000000uL
    return w.toULong() * 0x057da4120776f3ffuL + folded + 0x7d7d6bb0e7cd07d3uL
}

internal fun builder63c278X0BWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong {
    val w0 = u32Affine63c278(word, index, 0x118f28, 0x118548, tables)
    val w = w0 * 0x4dce977fu + 0x7275db64u
    var folded = w.toULong() * 0x3125dbf4f55c0c6duL + 0x1167036e8591663cuL
    folded = fold63c278(folded, 0x301af0, 8, tables)
    folded *= 0xee1902df00000000uL
    return w.toULong() * 0x41108caa0013530duL + folded + 0x2ce8cc914f903207uL
}

internal fun builder63c278MixSeed(carry: ULong, scalarMul: ULong, scalarAdd: ULong, tables: Libre3FirstPairTables): MixSeed {
    val mixed = carry * scalarMul + scalarAdd
    var folded = mixed * 0xe56ee0d2dabe3103uL + 0xe1a57f65c01b39acuL
    folded = fold63c278(folded, 0x301870, 7, tables)
    folded *= 0x43cf3bc9b0000000uL
    val seed = mixed * 0x47b2ca50a9011f2fuL + folded + 0xa9ccf36f06c69525uL
    return MixSeed(
        seed * 0x707f1c911d72472duL + 0x20d7bce79675ce2euL,
        seed * 0x62d17dd555b3e7b5uL + 0xa95e929c3eca7e5euL,
    )
}

internal fun builder63c278NextCarry(updatedFirst: ULong, updatedSecond: ULong, tables: Libre3FirstPairTables): ULong {
    var folded = updatedFirst * 0x500a38540d22b25buL + 0xae9b83bb74900f1euL
    folded = fold63c278(folded, 0x3018f0, 7, tables)
    val carryMix = folded * 0x6e12b4b0721da33buL + 0x15fb45ff71081e4euL
    var folded2 = carryMix * 0xb926d0a2f88df903uL + 0x931eca912f88a4c7uL
    folded2 = fold63c278(folded2, 0x301970, 9, tables)
    folded2 *= 0x30cbc3f000000000uL
    val mixed2 = carryMix * 0x025241c2cd0d8443uL + folded2
    return mixed2 * 0x8074fb50d5400883uL + updatedSecond + 0x9a2a45734b3e5fb0uL
}

internal fun builder63c278Mix2Seed(carry: ULong, scalarMul: ULong, scalarAdd: ULong, tables: Libre3FirstPairTables): MixSeed {
    val mixed = carry * scalarMul + scalarAdd
    var folded = mixed * 0x126e65dcb0b83de1uL + 0x5454202b530d9481uL
    folded = fold63c278(folded, 0x301b70, 7, tables)
    folded *= 0x3d2ffccf90000000uL
    val seed = mixed * 0x4c89449a165d8427uL + folded + 0x654ba76b767a427cuL
    return MixSeed(
        seed * 0x564d78f55b5eefabuL + 0xf24aa781d14548f5uL,
        seed * 0xeb7cfc7c768d163cuL + 0x09afd4171a0c7a44uL,
    )
}

internal fun builder63c278NextCarry2(updatedFirst: ULong, updatedSecond: ULong, tables: Libre3FirstPairTables): ULong {
    var folded = updatedFirst * 0xbca4dd7019310b05uL + 0x088f442397943c2auL
    folded = fold63c278(folded, 0x301bf0, 7, tables)
    val carryMix = folded * 0x727c48215454885buL + 0xcaf590adfa7e603buL
    var folded2 = carryMix * 0xfb3565409b501139uL + 0x74b39dd74e3ac2eduL
    folded2 = fold63c278(folded2, 0x301c70, 9, tables)
    folded2 *= 0x0081c49000000000uL
    val mixed2 = carryMix * 0xf4d598a4fa80dabfuL + folded2
    return mixed2 * 0x0d5f48e79ddef1c9uL + updatedSecond + 0x9506d95873fe6ec8uL
}

internal fun builder63c278AccumAWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong {
    val w0 = u32Affine63c278(word, index, 0x11a9c8, 0x120f28, tables)
    val w = w0 * 0x2545ee53u + 0xf74fe193u
    var folded = w.toULong() * 0x69289ee9a98801f5uL + 0x89bfbb0b1b21e854uL
    folded = fold63c278(folded, 0x301d70, 8, tables)
    return w.toULong() * 0x12f7e0136d4dad87uL + folded * 0x9917c7f500000000uL + 0xf80d0f670554b0a4uL
}

internal fun builder63c278AccumBWord(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong {
    val w0 = u32Affine63c278(word, index, 0x11bbe8, 0x121948, tables)
    val w = w0 * 0x7dd1ecf7u + 0xdc8c9daeu
    var folded = w.toULong() * 0xf13beb213918d361uL + 0xa1220647c9883100uL
    folded = fold63c278(folded, 0x301df0, 8, tables)
    return w.toULong() * 0x952e2be9091d60c7uL + folded * 0xd89eb2d900000000uL + 0x54e3b2cc004948beuL
}

internal fun builder63c278BridgeX0Word(word: UInt, index: Int, tables: Libre3FirstPairTables): ULong {
    val w0 = u32Affine63c278(word, index, 0x11cd88, 0x112e48, tables)
    val w = w0 * 0x1c8f15cfu + 0x05d1107bu
    var folded = w.toULong() * 0x19d189b1be9d480buL + 0xd2bafb34c1909b26uL
    folded = fold63c278(folded, 0x301e70, 8, tables)
    folded *= 0x2ece929d00000000uL
    return w.toULong() * 0x7529d4f2739a8b41uL + folded + 0xdb7158ce45fcb750uL
}

internal fun builder63c278BridgeMixSeed(carry: ULong, scalarMul: ULong, scalarAdd: ULong, tables: Libre3FirstPairTables): MixSeed {
    val mixed = carry * scalarMul + scalarAdd
    var folded = mixed * 0x34af0af1bbce60dduL + 0x61b88589a4883d43uL
    folded = fold63c278(folded, 0x301ef0, 7, tables)
    folded *= 0x2da4669430000000uL
    val seed = mixed * 0x8f5055af84d40129uL + folded + 0x7bf63147a7179819uL
    return MixSeed(
        seed * 0xdb5bb72dd36c07a9uL + 0x155c3f0a68fbcfe1uL,
        seed * 0x0ee832c1be220ab1uL + 0xcc246f1fe68886a9uL,
    )
}

internal fun builder63c278BridgeNextCarry(updatedFirst: ULong, updatedSecond: ULong, tables: Libre3FirstPairTables): ULong {
    var folded = updatedFirst * 0x060c229ff67c02fbuL + 0xab8d83e0c2b70611uL
    folded = fold63c278(folded, 0x301f70, 7, tables)
    val carryMix = folded * 0x6a605d1236fbedd7uL + 0xfd36e0ea31dbe67cuL
    var folded2 = carryMix * 0x57a20a77f75734e1uL + 0x4cc594baeecf3ecauL
    folded2 = fold63c278(folded2, 0x301ff0, 9, tables)
    folded2 *= 0x9c0c689000000000uL
    val mixed2 = carryMix * 0x1e0bc5b08daead97uL + folded2
    return mixed2 * 0x93bd22efcdeacdc3uL + updatedSecond + 0xc175492c1e8124acuL
}

internal fun builder63c278PrebranchSP4F0FoldState(state: UInt, tables: Libre3FirstPairTables): UInt {
    var folded = state * 0x3dbef531u + 0x554aacd3u
    folded = fold32ByNibbles63c278(folded, 0x3021f8, 7, tables)
    val selected = u32TableWord63c278(0x122ac8 + (folded and 7u).toInt() * 4, tables)
    return selected + (folded shr 3)
}

internal fun builder63c278PrebranchSP4F0State(word: UInt, tables: Libre3FirstPairTables): UInt {
    val half = word shr 1
    val bitTable = u32TableWord63c278(0x126850 + (word and 1u).toInt() * 4, tables)
    return half * 0x0c949fdbu + bitTable
}

/** The numbers of one affine step of the branch loop. */
@Suppress("LongParameterList")
internal class BranchAffine63c278Params(
    val arg0Mul: UInt,
    val arg0Add: UInt,
    val halfMul: UInt,
    val bitTable: Int,
    val preMul: UInt,
    val preAdd: UInt,
    val wordMul: UInt,
    val wordAdd: UInt,
    val foldTable: Int,
    val selectTable: Int,
    val argMulTable: Int,
    val argAddTable: Int,
    val carryMul: UInt,
    val valueMul: UInt,
    val nextMul: UInt,
    val loopAdd: UInt,
    val finalAdd: UInt,
    val outMulTable: Int,
    val outAddTable: Int,
)

/** One list of words that a reducer reads, with the table that scales it. */
internal class StageReducerStream63c278(val words: UIntArray, val mulTable: Int)

/** The numbers of one reducer step of the branch loop. */
@Suppress("LongParameterList")
internal class StageReducer63c278Params(
    val carry: UInt,
    val carryMul: UInt,
    val preMul: UInt,
    val preAdd: UInt,
    val foldTable: Int,
    val reduceMul: UInt,
    val sideMul: UInt,
    val reduceAdd: UInt,
    val folded7Mul: UInt,
    val folded8Mul: UInt,
    val nextAdd: UInt,
    val outMulTable: Int,
    val outAddTable: Int,
)

internal fun builder63c278LoopUpdateSP658Odd(words: UIntArray, tables: Libre3FirstPairTables): UIntArray =
    branchAffineUpdate63c278(
        words,
        BranchAffine63c278Params(
            arg0Mul = 0x33f71427u, arg0Add = 0x58500b33u, halfMul = 0x2cb60683u,
            bitTable = 0x126a48, preMul = 0xc4fb260bu, preAdd = 0xf348f6f7u,
            wordMul = 0x2b1d86b1u, wordAdd = 0xfa05b11du,
            foldTable = 0x302378, selectTable = 0x112648,
            argMulTable = 0x117ba8, argAddTable = 0x11cde8,
            carryMul = 0xa822376du, valueMul = 0xb8000000u, nextMul = 0x30000000u,
            loopAdd = 0x24e24246u, finalAdd = 0x14e24246u,
            outMulTable = 0x1206a8, outAddTable = 0x11b468,
        ),
        tables,
    )

internal fun builder63c278LoopSP658EvenUsesSuccessPath(
    sp658Words: UIntArray,
    sp6b0Words: UIntArray,
    tables: Libre3FirstPairTables,
): Boolean {
    requireVectorWords(listOf(sp658Words, sp6b0Words))
    for (index in builder63c278VectorWords - 1 downTo 0) {
        val tableOffset = (index * 4) and 0x1c
        val check = sp658Words[index] * u32TableWord63c278(0x1154c8 + tableOffset, tables) +
            sp6b0Words[index] * u32TableWord63c278(0x1172c8 + tableOffset, tables) +
            u32TableWord63c278(0x11c468 + tableOffset, tables)
        if (check != 0x59262fedu) {
            val folded = fold32ByNibbles63c278(check, 0x302478, 7, tables)
            return (folded and 0x0fu) == 0x0du
        }
    }
    return true
}

internal fun builder63c278LoopUpdateSP658EvenSuccess(
    sp658Words: UIntArray,
    sp6b0Words: UIntArray,
    tables: Libre3FirstPairTables,
): UIntArray = stageReducer63c278(
    staticPatternWords63c278(0x125a70, 0x125940, 0x126ce0, tables),
    listOf(
        StageReducerStream63c278(sp658Words, 0x11b4c8),
        StageReducerStream63c278(sp6b0Words, 0x122b08),
    ),
    StageReducer63c278Params(
        carry = 0x6238179au, carryMul = 0x2cb31cf5u,
        preMul = 0xeaa360b5u, preAdd = 0x7dcae1fdu,
        foldTable = 0x3025b8, reduceMul = 0x354589c9u,
        sideMul = 0xb0000000u, reduceAdd = 0xb0b43182u,
        folded7Mul = 0x6f16c509u, folded8Mul = 0x0e93af70u,
        nextAdd = 0x29fd0d1cu,
        outMulTable = 0x11d508, outAddTable = 0x123508,
    ),
    tables,
)

internal fun builder63c278LoopUpdateSP6B0Even(words: UIntArray, tables: Libre3FirstPairTables): UIntArray =
    branchAffineUpdate63c278(
        words,
        BranchAffine63c278Params(
            arg0Mul = 0x96928029u, arg0Add = 0x666d5b3au, halfMul = 0x27acf74du,
            bitTable = 0x126a18, preMul = 0x84602417u, preAdd = 0xf95f2c9du,
            wordMul = 0x4bd20bc9u, wordAdd = 0x3a0734cau,
            foldTable = 0x302278, selectTable = 0x11cda8,
            argMulTable = 0x11c428, argAddTable = 0x119748,
            carryMul = 0x96d2d627u, valueMul = 0x98000000u, nextMul = 0x90000000u,
            loopAdd = 0x2f40aa3du, finalAdd = 0x3f40aa3du,
            outMulTable = 0x117b68, outAddTable = 0x121968,
        ),
        tables,
    )

private fun builder63c278LoopUpdateSP440Odd(words: UIntArray, tables: Libre3FirstPairTables): UIntArray =
    branchAffineUpdate63c278(
        words,
        BranchAffine63c278Params(
            arg0Mul = 0x28f734a3u, arg0Add = 0x7fc88b1cu, halfMul = 0xb00c4591u,
            bitTable = 0x126780, preMul = 0x059e578du, preAdd = 0x33273af5u,
            wordMul = 0x9d8dd89fu, wordAdd = 0xa52d9347u,
            foldTable = 0x3022b8, selectTable = 0x113708,
            argMulTable = 0x11d4e8, argAddTable = 0x11c448,
            carryMul = 0xd470f3b3u, valueMul = 0xe8000000u, nextMul = 0xd0000000u,
            loopAdd = 0xadcd0df0u, finalAdd = 0x65cd0df0u,
            outMulTable = 0x11cdc8, outAddTable = 0x117b88,
        ),
        tables,
    )

private fun builder63c278LoopSP440EvenSP4F0Words(words: UIntArray, tables: Libre3FirstPairTables): UIntArray =
    branchAffineUpdate63c278(
        words,
        BranchAffine63c278Params(
            arg0Mul = 0x5888f7f5u, arg0Add = 0xbbf0e3d5u, halfMul = 0x642326dbu,
            bitTable = 0x126858, preMul = 0x246654c1u, preAdd = 0x2e782dc3u,
            wordMul = 0x1101c103u, wordAdd = 0x183fafb9u,
            foldTable = 0x3022f8, selectTable = 0x1154a8,
            argMulTable = 0x11a9e8, argAddTable = 0x122268,
            carryMul = 0x4d140725u, valueMul = 0xa8000000u, nextMul = 0xb0000000u,
            loopAdd = 0x6fe563d6u, finalAdd = 0x8fe563d6u,
            outMulTable = 0x11dd28, outAddTable = 0x120f48,
        ),
        tables,
    )

private fun builder63c278LoopUpdateSP440Even(
    sp440Words: UIntArray,
    sp5a0Words: UIntArray,
    tables: Libre3FirstPairTables,
): UIntArray {
    val sp4f0 = builder63c278LoopSP440EvenSP4F0Words(sp440Words, tables)
    return stageReducer63c278(
        staticPatternWords63c278(0x124f10, 0x125930, 0x126cd8, tables),
        listOf(
            StageReducerStream63c278(sp4f0, 0x114988),
            StageReducerStream63c278(sp5a0Words, 0x11aa08),
        ),
        StageReducer63c278Params(
            carry = 0xd3f16146u, carryMul = 0x84bb8555u,
            preMul = 0xd3dd75bbu, preAdd = 0x4bdc02a1u,
            foldTable = 0x302338, reduceMul = 0x26bbb9ffu,
            sideMul = 0x30000000u, reduceAdd = 0xe8f27692u,
            folded7Mul = 0xef9fd9a7u, folded8Mul = 0x06026590u,
            nextAdd = 0x613a18d6u,
            outMulTable = 0x120688, outAddTable = 0x1185a8,
        ),
        tables,
    )
}

internal fun builder63c278LoopUpdateSP440(words: UIntArray, sp5a0Words: UIntArray, tables: Libre3FirstPairTables): UIntArray =
    if (words[0] and 1u != 0u) {
        builder63c278LoopUpdateSP440Odd(words, tables)
    } else {
        builder63c278LoopUpdateSP440Even(words, sp5a0Words, tables)
    }

private fun builder63c278LoopUpdateSP390Even(words: UIntArray, tables: Libre3FirstPairTables): UIntArray =
    branchAffineUpdate63c278(
        words,
        BranchAffine63c278Params(
            arg0Mul = 0x5b7c4419u, arg0Add = 0xd8c9cb43u, halfMul = 0xa30de075u,
            bitTable = 0x1267f0, preMul = 0x936efcedu, preAdd = 0x32c3c0a7u,
            wordMul = 0x88e44053u, wordAdd = 0xc35d94bbu,
            foldTable = 0x3023b8, selectTable = 0x1149a8,
            argMulTable = 0x11b488, argAddTable = 0x119768,
            carryMul = 0x14c37dcdu, valueMul = 0x18000000u, nextMul = 0x30000000u,
            loopAdd = 0xe4da180fu, finalAdd = 0xccda180fu,
            outMulTable = 0x1168e8, outAddTable = 0x11a048,
        ),
        tables,
    )

private fun builder63c278LoopSP390OddSP4F0Words(words: UIntArray, tables: Libre3FirstPairTables): UIntArray =
    branchAffineUpdate63c278(
        words,
        BranchAffine63c278Params(
            arg0Mul = 0x8e39b739u, arg0Add = 0x7c6d92a6u, halfMul = 0xdca8620du,
            bitTable = 0x126940, preMul = 0x17c4f57fu, preAdd = 0x5b647db4u,
            wordMul = 0xb0fff815u, wordAdd = 0x831b4fffu,
            foldTable = 0x3023f8, selectTable = 0x123e28,
            argMulTable = 0x11f5a8, argAddTable = 0x11bc08,
            carryMul = 0x19f6ba67u, valueMul = 0xb8000000u, nextMul = 0x90000000u,
            loopAdd = 0x85a9b64du, finalAdd = 0xb5a9b64du,
            outMulTable = 0x117bc8, outAddTable = 0x116908,
        ),
        tables,
    )

private fun builder63c278LoopUpdateSP390Odd(
    sp390Words: UIntArray,
    sp5a0Words: UIntArray,
    tables: Libre3FirstPairTables,
): UIntArray {
    val sp4f0 = builder63c278LoopSP390OddSP4F0Words(sp390Words, tables)
    return stageReducer63c278(
        staticPatternWords63c278(0x126330, 0x126020, 0x126a10, tables),
        listOf(
            StageReducerStream63c278(sp4f0, 0x11ce08),
            StageReducerStream63c278(sp5a0Words, 0x122ae8),
        ),
        StageReducer63c278Params(
            carry = 0x1cd91585u, carryMul = 0x1a4cb35bu,
            preMul = 0x5137a735u, preAdd = 0x3e9907e2u,
            foldTable = 0x302438, reduceMul = 0x38fc5a19u,
            sideMul = 0xb0000000u, reduceAdd = 0x0f3d7c5du,
            folded7Mul = 0xa81b54e7u, folded8Mul = 0x7e4ab190u,
            nextAdd = 0x767d913cu,
            outMulTable = 0x11a068, outAddTable = 0x11b4a8,
        ),
        tables,
    )
}

internal fun builder63c278LoopUpdateSP390(words: UIntArray, sp5a0Words: UIntArray, tables: Libre3FirstPairTables): UIntArray =
    if (words[0] and 1u != 0u) {
        builder63c278LoopUpdateSP390Odd(words, sp5a0Words, tables)
    } else {
        builder63c278LoopUpdateSP390Even(words, tables)
    }

internal fun builder63c278LoopUpdateSP390PredicateFalse(
    sp390Words: UIntArray,
    arg0: ByteArray,
    tables: Libre3FirstPairTables,
): UIntArray = stageReducer63c278(
    staticPatternWords63c278(0x126040, 0x126600, 0x126740, tables),
    listOf(
        StageReducerStream63c278(sp390Words, 0x11b4e8),
        StageReducerStream63c278(arg0Words63c278(arg0), 0x11bc28),
    ),
    StageReducer63c278Params(
        carry = 0x6306d080u, carryMul = 0x90b4d58bu,
        preMul = 0x323154f1u, preAdd = 0x154382eeu,
        foldTable = 0x3025f8, reduceMul = 0x30b9cbfbu,
        sideMul = 0x50000000u, reduceAdd = 0x61849d3du,
        folded7Mul = 0x1fb5a053u, folded8Mul = 0x04a5fad0u,
        nextAdd = 0x002fe7efu,
        outMulTable = 0x117be8, outAddTable = 0x1172e8,
    ),
    tables,
)

internal fun builder63c278LoopUpdateSP390PredicateJoin(
    sp390Words: UIntArray,
    sp440Words: UIntArray,
    tables: Libre3FirstPairTables,
): UIntArray = stageReducer63c278(
    staticPatternWords63c278(0x124d90, 0x125c10, 0x126998, tables),
    listOf(
        StageReducerStream63c278(sp390Words, 0x11dd48),
        StageReducerStream63c278(sp440Words, 0x122288),
    ),
    StageReducer63c278Params(
        carry = 0xd554336du, carryMul = 0x43e12a11u,
        preMul = 0xfd350b93u, preAdd = 0xc2fdb2e2u,
        foldTable = 0x302638, reduceMul = 0x419ce971u,
        sideMul = 0x50000000u, reduceAdd = 0x967ae928u,
        folded7Mul = 0xcbd75debu, folded8Mul = 0x428a2150u,
        nextAdd = 0x27f798a1u,
        outMulTable = 0x118f48, outAddTable = 0x11ec28,
    ),
    tables,
)

internal fun builder63c278LoopUpdateSP6B0Failure(
    sp6b0Words: UIntArray,
    sp658Words: UIntArray,
    tables: Libre3FirstPairTables,
): UIntArray = stageReducer63c278(
    staticPatternWords63c278(0x1256b0, 0x125f40, 0x1268d8, tables),
    listOf(
        StageReducerStream63c278(sp6b0Words, 0x1149c8),
        StageReducerStream63c278(sp658Words, 0x11f5c8),
    ),
    StageReducer63c278Params(
        carry = 0x2b0fe6d9u, carryMul = 0x346b3047u,
        preMul = 0xe8d292cbu, preAdd = 0x6376b766u,
        foldTable = 0x3024b8, reduceMul = 0xd98513dbu,
        sideMul = 0xf0000000u, reduceAdd = 0x8af9de6du,
        folded7Mul = 0x74f0e285u, folded8Mul = 0xb0f1d7b0u,
        nextAdd = 0x6ac588e9u,
        outMulTable = 0x11fdc8, outAddTable = 0x115f68,
    ),
    tables,
)

internal fun builder63c278LoopUpdateSP440PredicateTrue(
    sp440Words: UIntArray,
    arg0: ByteArray,
    tables: Libre3FirstPairTables,
): UIntArray = stageReducer63c278(
    staticPatternWords63c278(0x124f20, 0x125a00, 0x126c68, tables),
    listOf(
        StageReducerStream63c278(sp440Words, 0x112668),
        StageReducerStream63c278(arg0Words63c278(arg0), 0x116928),
    ),
    StageReducer63c278Params(
        carry = 0x43bff476u, carryMul = 0x8123c767u,
        preMul = 0xbc55d64fu, preAdd = 0x3db88f4fu,
        foldTable = 0x302538, reduceMul = 0xb7e919a9u,
        sideMul = 0x90000000u, reduceAdd = 0xd881235bu,
        folded7Mul = 0x239e1779u, folded8Mul = 0xc61e8870u,
        nextAdd = 0xa1d86ec1u,
        outMulTable = 0x113728, outAddTable = 0x116948,
    ),
    tables,
)

internal fun builder63c278LoopUpdateSP440PredicateJoin(
    sp440Words: UIntArray,
    sp390Words: UIntArray,
    tables: Libre3FirstPairTables,
): UIntArray = stageReducer63c278(
    staticPatternWords63c278(0x125440, 0x126030, 0x1267b8, tables),
    listOf(
        StageReducerStream63c278(sp440Words, 0x114068),
        StageReducerStream63c278(sp390Words, 0x11c488),
    ),
    StageReducer63c278Params(
        carry = 0xf35277e4u, carryMul = 0x3ae4bb05u,
        preMul = 0x130a19ebu, preAdd = 0x624d99a5u,
        foldTable = 0x302578, reduceMul = 0x53cea2cfu,
        sideMul = 0x30000000u, reduceAdd = 0x81d9862eu,
        folded7Mul = 0x53c4f527u, folded8Mul = 0xc3b0ad90u,
        nextAdd = 0x59871186u,
        outMulTable = 0x120f68, outAddTable = 0x11a088,
    ),
    tables,
)

/** The test that picks which of the two joins the branch loop takes. */
internal fun builder63c278Predicate64D55C(sp440Words: UIntArray, sp390Words: UIntArray, tables: Libre3FirstPairTables): Int {
    for (stream in listOf(sp440Words, sp390Words)) {
        if (stream.size < builder63c278VectorWords) {
            throw Libre3CryptoException("a 63c278 stream must be $builder63c278VectorWords words, not ${stream.size}")
        }
    }
    for (index in builder63c278VectorWords - 1 downTo 0) {
        val tableOffset = (index * 4) and 0x1c
        val check = sp440Words[index] * u32TableWord63c278(0x11fde8 + tableOffset, tables) +
            sp390Words[index] * u32TableWord63c278(0x1206c8 + tableOffset, tables) +
            u32TableWord63c278(0x1185c8 + tableOffset, tables)
        if (check != 0x213734c0u) {
            val folded = fold32ByNibbles63c278(check, 0x3024f8, 7, tables)
            return if ((folded and 0x0fu) != 0u) 1 else 0
        }
    }
    return 0
}

/** True when the branch loop has reached its end state. */
internal fun builder63c278TerminalSP658Ready(sp658Words: UIntArray, tables: Libre3FirstPairTables): Boolean {
    if (sp658Words.size < builder63c278VectorWords) {
        throw Libre3CryptoException("a 63c278 stream must be $builder63c278VectorWords words, not ${sp658Words.size}")
    }
    if (sp658Words[0] * 0x04dc738du != 0x49f4222fu) return false

    for (index in 1 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        val check = foldTableU32Word63c278(0x3021a0 + index * 4, tables) *
            u32TableWord63c278(0x1234e8 + tableOffset, tables) +
            sp658Words[index] * u32TableWord63c278(0x120668 + tableOffset, tables) +
            u32TableWord63c278(0x11e5a8 + tableOffset, tables)
        if (check != 0x0a2c3abeu) return false
    }
    return true
}

private fun branchAffineUpdate63c278(
    words: UIntArray,
    params: BranchAffine63c278Params,
    tables: Libre3FirstPairTables,
): UIntArray {
    if (words.size != builder63c278VectorWords) {
        throw Libre3CryptoException("a 63c278 stream must be $builder63c278VectorWords words, not ${words.size}")
    }
    val first = words[0] * params.arg0Mul + params.arg0Add
    val firstState = branchState63c278(first, params.halfMul, params.bitTable, tables)
    var carry = branchWord63c278(firstState, params, tables)

    val out = UIntArray(builder63c278VectorWords)
    for (index in 0 until builder63c278VectorWords - 1) {
        val nextIndex = index + 1
        val nextTableOffset = (nextIndex and 7) * 4
        val value = u32TableAffine63c278(
            words[nextIndex], params.argMulTable + nextTableOffset, params.argAddTable + nextTableOffset, tables,
        )
        val state = branchState63c278(value, params.halfMul, params.bitTable, tables)
        val word = branchWord63c278(state, params, tables)
        carry = carry * params.carryMul + value * params.valueMul + word * params.nextMul
        val storeValue = carry + params.loopAdd
        val tableOffset = (index * 4) and 0x1c
        out[index] = u32TableAffine63c278(
            storeValue, params.outMulTable + tableOffset, params.outAddTable + tableOffset, tables,
        )
        carry = word
    }

    val finalStore = carry * params.carryMul + params.finalAdd
    val finalOffset = ((builder63c278VectorWords - 1) * 4) and 0x1c
    out[builder63c278VectorWords - 1] = u32TableAffine63c278(
        finalStore, params.outMulTable + finalOffset, params.outAddTable + finalOffset, tables,
    )
    return out
}

private fun branchWord63c278(state: UInt, params: BranchAffine63c278Params, tables: Libre3FirstPairTables): UInt {
    val preFold = state * params.preMul + params.preAdd
    val select = branchSelectBit63c278(preFold, params.foldTable, params.selectTable, tables)
    return state * params.wordMul + params.wordAdd + select
}

private fun branchSelectBit63c278(value: UInt, foldTable: Int, selectTable: Int, tables: Libre3FirstPairTables): UInt {
    val folded = fold32ByNibbles63c278(value, foldTable, 7, tables)
    val selected = u32TableWord63c278(selectTable + (folded and 7u).toInt() * 4, tables) + (folded shr 3)
    return selected shl 31
}

internal fun branchState63c278(word: UInt, halfMul: UInt, bitTable: Int, tables: Libre3FirstPairTables): UInt {
    val bit = u32TableWord63c278(bitTable + (word and 1u).toInt() * 4, tables)
    return (word shr 1) * halfMul + bit
}

/** The repeating pattern of fixed words that a reducer starts from. */
internal fun staticPatternWords63c278(q0: Int, q1: Int, tail: Int, tables: Libre3FirstPairTables): UIntArray {
    val q0Words = UIntArray(4) { u32TableWord63c278(q0 + it * 4, tables) }
    val q1Words = UIntArray(4) { u32TableWord63c278(q1 + it * 4, tables) }
    val tailWords = UIntArray(2) { u32TableWord63c278(tail + it * 4, tables) }
    return q0Words + q1Words + q0Words + q1Words + q0Words + tailWords
}

private fun stageReducer63c278(
    staticWords: UIntArray,
    streams: List<StageReducerStream63c278>,
    params: StageReducer63c278Params,
    tables: Libre3FirstPairTables,
): UIntArray {
    if (staticWords.size != builder63c278VectorWords) {
        throw Libre3CryptoException("a 63c278 stream must be $builder63c278VectorWords words, not ${staticWords.size}")
    }
    val sp230 = staticWords.copyOf()
    for (stream in streams) {
        if (stream.words.size != builder63c278VectorWords) {
            throw Libre3CryptoException("a 63c278 stream must be $builder63c278VectorWords words, not ${stream.words.size}")
        }
        for ((index, word) in stream.words.withIndex()) {
            val tableOffset = (index and 7) * 4
            sp230[index] += word * u32TableWord63c278(stream.mulTable + tableOffset, tables)
        }
    }

    var carry = params.carry
    val out = UIntArray(builder63c278VectorWords)
    for ((index, word) in sp230.withIndex()) {
        carry = carry * params.carryMul + word
        var folded7 = carry * params.preMul + params.preAdd
        folded7 = fold32ByNibbles63c278(folded7, params.foldTable, 7, tables)
        val folded8 = foldTableU32Word63c278(params.foldTable + (folded7 and 0x0fu).toInt() * 4, tables) + (folded7 shr 4)
        val stage = carry * params.reduceMul + folded7 * params.sideMul + params.reduceAdd
        val nextCarry = folded7 * params.folded7Mul + folded8 * params.folded8Mul
        val tableOffset = (index * 4) and 0x1c
        out[index] = u32TableAffine63c278(
            stage, params.outMulTable + tableOffset, params.outAddTable + tableOffset, tables,
        )
        carry = nextCarry + params.nextAdd
    }
    return out
}

/** Every one of these lists must have the twenty two words the stage expects. */
internal fun requireVectorWords(streams: List<UIntArray>) {
    for (stream in streams) {
        if (stream.size != builder63c278VectorWords) {
            throw Libre3CryptoException("a 63c278 stream must be $builder63c278VectorWords words, not ${stream.size}")
        }
    }
}

internal fun u32AffineBytes63c278(
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
        val tableOffset = (index * 4) and 0x1c
        val word = u32TableAffine63c278(
            readUInt32LE(input, index * 4), mulTable + tableOffset, addTable + tableOffset, tables,
        )
        writeUInt32LE(word, out, index * 4)
    }
    return out
}
