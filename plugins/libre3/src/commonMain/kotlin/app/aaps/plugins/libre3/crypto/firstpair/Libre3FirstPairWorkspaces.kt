package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException

/*
 * The three work area engines `64bd0c`, `64c524` and `64cd40`.
 *
 * Ported from LibreCRKit `Crypto/FirstPairSourceSlice.swift` at pin `a86b92f`.
 *
 * All three do the same thing with their own numbers: turn one argument into twenty two long
 * words, add them into a work area twenty two times over, then squeeze the tail of that work area
 * down to twenty two words. They are kept apart, as in the original, because their fold tables
 * differ and a shared body would make a wrong table hard to see.
 */

/** What one round of a work area update uses. */
internal class Libre3WorkspaceRoundParams(val multiplier: ULong, val broadcast: ULong)

/** What the first fold of a final step produced. */
internal class Libre3FinalFoldParts(val nextBase: ULong, val side: UInt, var folded: ULong)

private fun requireWorkspace(bytes: ByteArray, needed: Int, label: String) {
    if (bytes.size < needed) {
        throw Libre3CryptoException("the $label must be at least $needed bytes, not ${bytes.size}")
    }
}

private fun requireArg(arg: ByteArray, label: String) {
    if (arg.size < builder63c278VectorBytes) {
        throw Libre3CryptoException("the $label must be at least $builder63c278VectorBytes bytes, not ${arg.size}")
    }
}

private fun requireArgWords(words: ULongArray) {
    if (words.size != builder63c278VectorWords) {
        throw Libre3CryptoException("the argument must be $builder63c278VectorWords long words, not ${words.size}")
    }
}

/** The shared shape of the three work area updates. */
private fun workspaceAfterUpdate(
    arg0U64Words: ULongArray,
    scalar: ULong,
    x2Workspace: ByteArray,
    workspaceWords: Int,
    params: (ULong, ULong) -> Libre3WorkspaceRoundParams,
    rewrite: (ULong, ULong) -> ULong,
): ByteArray {
    requireArgWords(arg0U64Words)
    val x2Words = ULongArray(workspaceWords) { readUInt64LE(x2Workspace, it * 8) }
    var carryWord = x2Words[0]
    for (base in 0 until builder63c278VectorWords) {
        val round = params(scalar, carryWord)
        for (offset in arg0U64Words.indices) {
            val pos = base + offset
            x2Words[pos] = x2Words[pos] + round.broadcast + arg0U64Words[offset] * round.multiplier
        }
        carryWord = rewrite(x2Words[base], x2Words[base + 1])
        x2Words[base + 1] = carryWord
    }
    return packUInt64LE64cd40(x2Words)
}

// ---------------------------------------------------------------- 64bd0c

internal fun builder64bd0cArg0U64Words(arg0: ByteArray): ULongArray {
    requireArg(arg0, "64bd0c arg0")
    val tables = Libre3FirstPairTables.get()
    return ULongArray(builder63c278VectorWords) { index ->
        val affine = u32Affine63c278(
            readUInt32LE(arg0, index * 4), index, builder64bd0cArg0MulTable, builder64bd0cArg0AddTable, tables,
        )
        val word = affine * 0x3e251f3fu + 0xc80f68f4u
        val folded = fold63c278(
            word.toULong() * 0xf636dda3668409f3uL + 0xa1898a9b9b0c347buL, builder64bd0cArg0FoldTable, 8, tables,
        )
        word.toULong() * 0x57c9f2b4caac6659uL + folded * 0xa43bca7d00000000uL + 0x6de2d7b43700ac09uL
    }
}

private fun builder64bd0cWorkspaceParams(scalar: ULong, firstX2Word: ULong, tables: Libre3FirstPairTables): Libre3WorkspaceRoundParams {
    val seedA = scalar * 0x9cbd06d772de1901uL + 0x34e214bca24f560cuL
    val seedB = scalar * 0xbe4812554b30ebf8uL + 0xc770490f6d646597uL
    var mixed = seedA * firstX2Word + seedB
    var folded = mixed * 0x213ec1d8d1bc2d9buL + 0x3cda12a384db6d3buL
    folded = fold63c278(folded, builder64bd0cWorkspaceFold1Table, 7, tables)
    mixed = mixed * 0x91ab7a47a981923buL + folded * 0x0ed61381f0000000uL + 0xab62fec0d215095buL
    return Libre3WorkspaceRoundParams(
        mixed * 0x8493b5edc5e368a1uL + 0x6f8e182c75ab0bb8uL,
        mixed * 0x2698148ddd26a50euL + 0x740f3b32b62a7210uL,
    )
}

private fun builder64bd0cRewriteSecondWord(first: ULong, second: ULong, tables: Libre3FirstPairTables): ULong {
    var folded = first * 0xe121bd3e759b23f3uL + 0x8c105c11c96e758buL
    folded = fold63c278(folded, builder64bd0cRewriteFold1Table, 7, tables)
    var mixed = folded * 0x4afc5649aee85307uL + 0xdc13fe8d315ad1a7uL
    var folded2 = mixed * 0x7c64ef86eb0d2547uL + 0x95549ebb3b944abeuL
    folded2 = fold63c278(folded2, builder64bd0cRewriteFold2Table, 9, tables)
    mixed = mixed * 0xd9ef08a678eb7ba3uL + folded2 * 0x8e62b3b000000000uL
    return mixed * 0x3352cbd4c2b4f2efuL + second + 0x793cd011929995d8uL
}

private fun builder64bd0cFinalFirstFold(value: ULong, tables: Libre3FirstPairTables): Libre3FinalFoldParts {
    var folded = value * 0xbfeaa39c4f3a2fdfuL + 0xb07328e69628c835uL
    var side = value.toUInt() * 0x48daeaa5u
    folded = fold63c278(folded, builder64bd0cFinalFoldTable, 7, tables)
    side += folded.toUInt() * 0x50000000u
    val nextBase = folded
    folded = fold63c278(folded, builder64bd0cFinalFoldTable, 1, tables)
    return Libre3FinalFoldParts(nextBase, side + 0xdfea4892u, folded)
}

internal fun builder64bd0cWorkspaceAfterUpdate(arg0U64Words: ULongArray, scalar: ULong, x2Workspace: ByteArray): ByteArray {
    requireWorkspace(x2Workspace, builder64bd0cWorkspaceBytes, "64bd0c x2 work area")
    val tables = Libre3FirstPairTables.get()
    return workspaceAfterUpdate(
        arg0U64Words, scalar, x2Workspace, builder64bd0cWorkspaceWords,
        { s, w -> builder64bd0cWorkspaceParams(s, w, tables) },
        { a, b -> builder64bd0cRewriteSecondWord(a, b, tables) },
    )
}

internal fun builder64bd0cFinalU32Words(x2Workspace: ByteArray): UIntArray {
    requireWorkspace(x2Workspace, builder64bd0cWorkspaceBytes, "64bd0c x2 work area")
    val tables = Libre3FirstPairTables.get()
    val x2Words = ULongArray(builder64bd0cWorkspaceWords) { readUInt64LE(x2Workspace, it * 8) }
    var carry = 0xa8100bf8a7268389uL
    val out = UIntArray(builder63c278VectorWords)

    for (index in 0 until builder63c278VectorWords) {
        val tailWord = x2Words[builder63c278VectorWords + index]
        val mixed = carry * 0xdc6110b4d93c58f7uL + tailWord * 0x29221b50b5648139uL
        val firstFold = builder64bd0cFinalFirstFold(mixed + 0x02ea5a475ff009a0uL, tables)
        firstFold.folded = fold63c278(firstFold.folded, builder64bd0cFinalFoldTable, 8, tables)
        val nextCarry = firstFold.nextBase * 0x1323954bb9644419uL + firstFold.folded * 0x69bbbe7000000000uL
        val tableOffset = (index * 4) and 0x1c
        out[index] = u32TableAffine63c278(
            firstFold.side, builder64bd0cFinalOutMulTable + tableOffset, builder64bd0cFinalOutAddTable + tableOffset, tables,
        )
        carry = nextCarry + 0x7a8f00bf503f94fbuL
    }
    return out
}

internal fun builder64bd0cOutputWords(arg0: ByteArray, scalar: ULong, x2Workspace: ByteArray): UIntArray =
    builder64bd0cFinalU32Words(
        builder64bd0cWorkspaceAfterUpdate(builder64bd0cArg0U64Words(arg0), scalar, x2Workspace)
    )

// ---------------------------------------------------------------- 64c524

internal fun builder64c524Arg0U64Words(arg0: ByteArray): ULongArray {
    requireArg(arg0, "64c524 arg0")
    val tables = Libre3FirstPairTables.get()
    return ULongArray(builder63c278VectorWords) { index ->
        val affine = u32Affine63c278(
            readUInt32LE(arg0, index * 4), index, builder64c524Arg0MulTable, builder64c524Arg0AddTable, tables,
        )
        val word = affine * 0xc0134d17u + 0x71ee3738u
        val folded = fold63c278(
            word.toULong() * 0x30f8c406f090e325uL + 0x7a3d4622dcb83626uL, builder64c524Arg0FoldTable, 8, tables,
        )
        word.toULong() * 0x430cd374007356b5uL + folded * 0xc0efe7af00000000uL + 0xe5faf13619f0e974uL
    }
}

private fun builder64c524WorkspaceParams(scalar: ULong, firstX2Word: ULong, tables: Libre3FirstPairTables): Libre3WorkspaceRoundParams {
    val seedA = scalar * 0x37225d56e2d37ae5uL + 0x3d01a097518f54bcuL
    val seedB = scalar * 0xd03e88ab453ae68buL + 0x5ca5b123c7ddda97uL
    var mixed = seedA * firstX2Word + seedB
    var folded = mixed * 0x4355499b9de8f281uL + 0xd616e418c4bc0066uL
    folded = fold63c278(folded, builder64c524WorkspaceFold1Table, 7, tables)
    mixed = mixed * 0x65dd922c973ea261uL + folded * 0xb2b8c001f0000000uL + 0x457dafb763ad58f5uL
    return Libre3WorkspaceRoundParams(
        mixed * 0x9fdf9fbdfb76ed77uL + 0x14646b0029e6e968uL,
        mixed * 0x55fb9010c9586c69uL + 0x63292bd5dae78b98uL,
    )
}

private fun builder64c524RewriteSecondWord(first: ULong, second: ULong, tables: Libre3FirstPairTables): ULong {
    var folded = first * 0xe191957128574e8duL + 0xca735f7e01db7229uL
    folded = fold63c278(folded, builder64c524RewriteFold1Table, 7, tables)
    var mixed = folded * 0xc0759fa984b4b32buL + 0x4e00c393f4ad1417uL
    var folded2 = mixed * 0x2ca78080bf929d71uL + 0x489c8bdec6559298uL
    folded2 = fold63c278(folded2, builder64c524RewriteFold2Table, 9, tables)
    mixed = mixed * 0x21a1db1ac0ca1e41uL + folded2 * 0x3659a2f000000000uL
    return mixed * 0x1685e929cba4a88fuL + second + 0x56570c70d17acce1uL
}

private fun builder64c524FinalFirstFold(value: ULong, tables: Libre3FirstPairTables): Libre3FinalFoldParts {
    val product = value * 0x2d5e3aab4210238buL
    var side = value.toUInt() * 0x6773f057u
    var folded = foldTableU64Word63c278(builder64c524FinalFoldTable + (product and 0x0fuL).toInt() * 8, tables) +
        ((product + 0x04d30efa28ce4180uL) shr 4)
    folded = fold63c278(folded, builder64c524FinalFoldTable, 6, tables)
    side += folded.toUInt() * 0xb0000000u
    val nextBase = folded
    folded = fold63c278(folded, builder64c524FinalFoldTable, 1, tables)
    return Libre3FinalFoldParts(nextBase, side + 0xc5960ad5u, folded)
}

internal fun builder64c524WorkspaceAfterUpdate(arg0U64Words: ULongArray, scalar: ULong, x2Workspace: ByteArray): ByteArray {
    requireWorkspace(x2Workspace, builder64c524WorkspaceBytes, "64c524 x2 work area")
    val tables = Libre3FirstPairTables.get()
    return workspaceAfterUpdate(
        arg0U64Words, scalar, x2Workspace, builder64c524WorkspaceWords,
        { s, w -> builder64c524WorkspaceParams(s, w, tables) },
        { a, b -> builder64c524RewriteSecondWord(a, b, tables) },
    )
}

internal fun builder64c524FinalU32Words(x2Workspace: ByteArray): UIntArray {
    requireWorkspace(x2Workspace, builder64c524WorkspaceBytes, "64c524 x2 work area")
    val tables = Libre3FirstPairTables.get()
    val x2Words = ULongArray(builder64c524WorkspaceWords) { readUInt64LE(x2Workspace, it * 8) }
    var carry = 0xa231ae9017976cb8uL
    val out = UIntArray(builder63c278VectorWords)

    for (index in 0 until builder63c278VectorWords) {
        val tailWord = x2Words[builder63c278VectorWords + index]
        val foldedInput = carry * 0xcd03684f066c56f1uL + tailWord * 0x5691aa6f378a40d3uL + 0x1c0f700d822da380uL
        val firstFold = builder64c524FinalFirstFold(foldedInput, tables)
        firstFold.folded = fold63c278(firstFold.folded, builder64c524FinalFoldTable, 8, tables)
        val nextCarry = firstFold.nextBase * 0xe9b2139140497c53uL + firstFold.folded * 0xfb683ad000000000uL
        val tableOffset = (index * 4) and 0x1c
        out[index] = u32TableAffine63c278(
            firstFold.side, builder64c524FinalOutMulTable + tableOffset, builder64c524FinalOutAddTable + tableOffset, tables,
        )
        carry = nextCarry + 0xee51a1bedb406a0duL
    }
    return out
}

internal fun builder64c524OutputWords(arg0: ByteArray, scalar: ULong, x2Workspace: ByteArray): UIntArray =
    builder64c524FinalU32Words(
        builder64c524WorkspaceAfterUpdate(builder64c524Arg0U64Words(arg0), scalar, x2Workspace)
    )

// ---------------------------------------------------------------- 64cd40

internal fun builder64cd40Arg0U64Words(arg0: ByteArray): ULongArray {
    requireArg(arg0, "64cd40 arg0")
    val tables = Libre3FirstPairTables.get()
    return ULongArray(builder63c278VectorWords) { index ->
        val affine = u32Affine63c278(readUInt32LE(arg0, index * 4), index, 0x123e08, 0x123428, tables)
        val word = affine * 0x8c2231afu + 0x6d97daf4u
        var folded = word.toULong() * 0xeb7e2c45742037f1uL + 0x3363481bcd8bcd54uL
        folded = fold63c278(folded, 0x3010f0, 8, tables)
        word.toULong() * 0x92d397b84a615e45uL + folded * 0x73db406b00000000uL + 0xbdc443456d026c77uL
    }
}

private fun builder64cd40WorkspaceParams(scalar: ULong, firstX2Word: ULong, tables: Libre3FirstPairTables): Libre3WorkspaceRoundParams {
    val seedA = scalar * 0x697d0ecbc60d5d0fuL + 0x937857c2eed8d2b4uL
    val seedB = scalar * 0xbc235eb940a876dduL + 0x18b363a938b968b2uL
    var mixed = seedA * firstX2Word + seedB
    var folded = mixed * 0x10adb81e27dd69a7uL + 0xe7f726c1fe72a787uL
    folded = fold63c278(folded, 0x301170, 7, tables)
    mixed = mixed * 0x411310f58c3cbf15uL + folded * 0xc72468f1d0000000uL + 0x9d21e3104874d274uL
    return Libre3WorkspaceRoundParams(
        mixed * 0x0b7281fc87cf2277uL + 0x719b343dac285e92uL,
        mixed * 0x71031cfa7b36346euL + 0x0c7018d77bac9e24uL,
    )
}

private fun builder64cd40RewriteSecondWord(first: ULong, second: ULong, tables: Libre3FirstPairTables): ULong {
    var folded = first * 0x4fec946356180ba9uL + 0xcd7ac1129eab2dd8uL
    folded = fold63c278(folded, 0x3011f0, 7, tables)
    var mixed = folded * 0x1eb04e8030fffbd7uL + 0xaee6470479c51db3uL
    var folded2 = mixed * 0x8d796f74dc90608duL + 0x9ac49f51ec349615uL
    folded2 = fold63c278(folded2, 0x301270, 9, tables)
    mixed = mixed * 0xf9e1d6a0ce988cf5uL + folded2 * 0x1cc37f7000000000uL
    return mixed * 0x87f365d52d3aa373uL + second + 0x41b4561d4c674238uL
}

private fun builder64cd40FinalFirstFold(value: ULong, tables: Libre3FirstPairTables): Libre3FinalFoldParts {
    val product = value * 0x947905173900b973uL
    var side = value.toUInt() * 0x8f376f21u
    var folded = foldTableU64Word63c278(0x3012f0 + (product and 0x0fuL).toInt() * 8, tables) +
        ((product + 0x0c4fdbc2f625a640uL) shr 4)
    repeat(6) {
        folded = foldTableU64Word63c278(0x3012f0 + (folded and 0x0fuL).toInt() * 8, tables) + (folded shr 4)
    }
    side += folded.toUInt() * 0x50000000u
    val nextBase = folded
    folded = foldTableU64Word63c278(0x3012f0 + (folded and 0x0fuL).toInt() * 8, tables) + (folded shr 4)
    return Libre3FinalFoldParts(nextBase, side + 0x06ae2bd7u, folded)
}

internal fun builder64cd40WorkspaceAfterUpdate(arg0U64Words: ULongArray, scalar: ULong, x2Workspace: ByteArray): ByteArray {
    requireWorkspace(x2Workspace, builder64cd40WorkspaceBytes, "64cd40 x2 work area")
    val tables = Libre3FirstPairTables.get()
    return workspaceAfterUpdate(
        arg0U64Words, scalar, x2Workspace, builder64cd40WorkspaceWords,
        { s, w -> builder64cd40WorkspaceParams(s, w, tables) },
        { a, b -> builder64cd40RewriteSecondWord(a, b, tables) },
    )
}

internal fun builder64cd40FinalU32Words(x2Workspace: ByteArray): UIntArray {
    requireWorkspace(x2Workspace, builder64cd40WorkspaceBytes, "64cd40 x2 work area")
    val tables = Libre3FirstPairTables.get()
    val x2Words = ULongArray(builder64cd40WorkspaceWords) { readUInt64LE(x2Workspace, it * 8) }
    var carry = 0x0b784750d9181757uL
    val out = UIntArray(builder63c278VectorWords)

    for (index in 0 until builder63c278VectorWords) {
        val tailWord = x2Words[builder63c278VectorWords + index]
        val mixed = carry * 0xcaaf4f9d292a519duL + tailWord * 0x28d4341977190ea5uL
        val parts = builder64cd40FinalFirstFold(mixed + 0x593214d4b8068287uL, tables)
        repeat(8) {
            parts.folded = foldTableU64Word63c278(0x3012f0 + (parts.folded and 0x0fuL).toInt() * 8, tables) +
                (parts.folded shr 4)
        }
        val nextCarry = parts.nextBase * 0xda8179ca5c614737uL + parts.folded * 0x39eb8c9000000000uL
        val tableOffset = (index * 4) and 0x1c
        out[index] = u32TableAffine63c278(parts.side, 0x11c3a8 + tableOffset, 0x11dce8 + tableOffset, tables)
        carry = nextCarry + 0xd2a88419cf931098uL
    }
    return out
}

internal fun builder64cd40OutputWords(arg0: ByteArray, scalar: ULong, x2Workspace: ByteArray): UIntArray =
    builder64cd40FinalU32Words(
        builder64cd40WorkspaceAfterUpdate(builder64cd40Arg0U64Words(arg0), scalar, x2Workspace)
    )
