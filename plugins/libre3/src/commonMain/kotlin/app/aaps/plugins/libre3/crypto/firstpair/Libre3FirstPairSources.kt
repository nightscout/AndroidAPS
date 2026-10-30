package app.aaps.plugins.libre3.crypto.firstpair

/*
 * The fixed byte sources and word tables of the first pairing scheme.
 *
 * Ported from LibreCRKit `Crypto/FirstPairSourceSlice.swift` at pin `a86b92f`, keeping the Swift
 * names so the two files can be read side by side. See `Libre3FirstPairConstants.kt` for why.
 *
 * The byte arrays here are shared and are never written to. Kotlin has no read only byte array, so
 * that is a rule this package keeps rather than a thing the type says: nothing may write into one
 * of these, because every pairing on the phone would see the change.
 */

internal val pre63c278Arg0Source: ByteArray = bytesOf(
    0x21, 0xed, 0x7e, 0x8f, 0xc9, 0x86, 0x29, 0x76,
    0xac, 0x50, 0xb4, 0xcb, 0x1e, 0x31, 0xa9, 0x1f,
    0x30, 0xfa, 0x05, 0xc7, 0x06, 0x82, 0xac, 0x26,
    0xbc, 0x7d, 0xb7, 0x62, 0x19, 0xfd, 0x1d, 0x35,
    0x21, 0xed, 0x7e, 0x8f, 0xb9, 0x8b, 0xbe, 0x51,
    0xa3, 0x76, 0x9d, 0xa0, 0xc5, 0x08, 0x6c, 0x23,
    0x30, 0xfa, 0x05, 0xc7, 0x06, 0x82, 0xac, 0x26,
    0xbc, 0x7d, 0xb7, 0xd1, 0x19, 0xfd, 0x1d, 0x35,
    0x46, 0x68, 0x3b, 0x2a, 0x18, 0xd7, 0xe2, 0xe2,
    0xa3, 0x76, 0x9d, 0xa0, 0xc5, 0x08, 0x6c, 0x23,
    0x30, 0xfa, 0x05, 0xc7, 0x06, 0x82, 0xac, 0x26,
)

internal val streamStart642f60X2Source: ByteArray = bytesOf(
    0x4c, 0x2d, 0xf3, 0x05, 0xdd, 0xb7, 0x0c, 0x76,
    0xe8, 0x2a, 0xd4, 0x04, 0x3b, 0xe2, 0xee, 0xa5,
    0x81, 0xab, 0x69, 0xf3, 0x7c, 0xaa, 0x49, 0xf5,
    0xfa, 0x7d, 0x43, 0x81, 0x2f, 0x10, 0x25, 0x05,
    0xd3, 0x4e, 0x67, 0xbe, 0x8d, 0x2e, 0x98, 0xd3,
    0xe8, 0x2a, 0xe3, 0x78, 0x3b, 0x32, 0xb0, 0x16,
    0x81, 0xab, 0x69, 0x5c, 0xe3, 0x6c, 0x62, 0xec,
    0xa5, 0x62, 0x1f, 0xaf, 0xcf, 0x68, 0x46, 0x16,
    0xc5, 0x19, 0x8a, 0x79, 0x48, 0xa0, 0x3a, 0xd3,
    0xe8, 0x2a, 0xe3, 0x78, 0x3b, 0x32, 0xb0, 0x16,
    0x81, 0xab, 0x69, 0x5c, 0xe3, 0x6c, 0x62, 0xec,
)

internal val bundled6388f0LowSeedEntrySource: ByteArray = hexBytes(
    "0500000701060202030607010603030501000503000607040400050105030105" +
    "0507020005000507050206000104000603060102060003070003020006010703" +
    "0303040500040303060500050705000306000704060701060200020002040701" +
    "0604030706020405010303030204040400040004070107020706040301070407" +
    "0207060403050302020201070700020001050603000500050607000505000707" +
    "0105070407010205010301020001040707060604050700010502000201020203" +
    "0702020502070700030707070002000401030303010204000702000106020703" +
    "0304000606040205040003050305030706020500030305030002040101030204" +
    "0305060407010400000204000307040401010706010607040205000503060000" +
    "0704040002050105000707030504030502060405050503010103030000040305" +
    "0007070207070301020204030701020706010305050506000401050700030607" +
    "0704050403060601050204020405030302060701040002030507000604020502" +
    "0705000306000000010303070402030204040303060507020500040603000607" +
    "0007000104000102050202060700020101050005050302050300030503000006" +
    "0404050005030107050505070203040604050402070007010106020401010005" +
    "0003070206060006000401020006010303020403020101010606050607030505" +
    "0403070707070507020300040707000304020001"
)

internal val builder633fa8NullEntryBitsChecksSource: ByteArray = hexBytes(
    "3674f8f8a81c394e2bca21f938be42b1adbc94923891e2d38ee57c2d131dcebb" +
    "6eed185b2fe5d82f9543c721bdf818eb782dd2545d9b6429daaa6d5b725db614" +
    "4b8b6d5dca64a99a7565cb64a9baa66599b5688b34dd9aaadc9a354d53a2cd8a" +
    "756bca955b56b42bca12e1343551a11412fbcb2ecd59982c841bdca6eeda33bd" +
    "5e2cf8e2f1b468845576104cfaf7f8ceecfa7a15262ed5f6fa9bd9d442e12e97" +
    "ec15c1cb4c3ec1ec2881104cfaf7f8ceecfa7a15262ed5f6fa9b8bb0d0b3c47a" +
    "1c6cf95016b01676804ff8491d5e0e08e8c3b0504bc066ef57b66fbe719164d2" +
    "19086a9310bf190e20a7c27976c5579249c17bedcf2166ef57b6453b9c865799" +
    "a2246a9310bf190e20a7"
)

internal val process2P5PublicFixedPointBE: ByteArray = hexBytes(
    "04" +
    "a9bf2be2fd3d90f6467b8ca074710db3804eb0cfcc952a86d23289695d435ee0" +
    "9523a7d0e8aa2c53c6f7a49e9b6bd0db7a2d1035cd61876f37e43a74a1b65237"
)

internal val builder633fa8InvariantWords2dfc: UIntArray = uintArrayOf(
    0x9bed19fdu, 0xc70a4d0fu, 0x8257d22bu, 0xe2fafcb3u, 0x02c77d20u,
    0xb5ed0efau, 0x878c1b06u, 0x4bd92d7du, 0x21c6944fu, 0xd3ec5d2fu,
    0x876fda86u, 0x37f3e22au, 0x3cfcd7ceu, 0xabdc16ebu, 0x84ad2f7du,
    0x4bd92d7du, 0xf647adceu, 0xaa7b701eu, 0x876fda86u, 0x37f3e22au,
)

internal val builder633fa8InvariantWords3120: UIntArray = uintArrayOf(
    0xb33842d7u, 0x7b6ba784u, 0xa2f90f36u, 0xde5e2ad7u, 0x3c3537a9u,
    0x81d564f6u, 0x339ab4a2u, 0x999de03bu, 0x56c13b42u, 0xff14a487u,
    0x5a31640cu, 0xc3f85236u, 0x3c1dc79eu, 0x58a8d4a6u, 0x541cb00eu,
    0x63323fcdu, 0x1aa54a16u, 0x01f1b661u, 0x5a31640cu, 0xc3f85236u,
)

internal val process2P5PublicBSourceStaticWords: UIntArray = uintArrayOf(
    0xa99f067du, 0xb7043f80u, 0x2b6ee291u, 0xa4732ba2u, 0x6d3a9d91u,
    0x4fd9d579u, 0x319597e5u, 0xfce96d28u, 0x48b26f75u, 0x05c01679u,
    0x5080bac6u, 0x2e25e6a6u, 0xbfbafcdfu, 0x8e127707u, 0x000d0fb3u,
    0x4ac77820u, 0x7923dadfu, 0xe4ae8f3au, 0x5080bac6u, 0x2e25e6a6u,
)

/** Sizes that the Swift writes as a product of two other numbers. */
internal const val df80StateSize = 8 * df80WordSize
internal const val builder6388f0LaneBlocksSize = builder6388f0LaneBlockCount * builder6388f0LaneBlockSize
internal const val builder63c278VectorBytes = builder63c278VectorWords * 4
internal const val builder64cd40WorkspaceBytes = builder64cd40WorkspaceWords * 8
internal const val builder64bd0cWorkspaceBytes = builder64bd0cWorkspaceWords * 8
internal const val builder64c524WorkspaceBytes = builder64c524WorkspaceWords * 8
internal const val builder6388f0CallerLoopTableBytes = builder6388f0CallerLoopTableRows * builder6388f0CallerLoopRowBytes
internal const val builder6388f0CallerLoopInterleavedRowBytes = builder6388f0CallerLoopRowBytes * 2
internal const val builder6388f0CallerLoopInterleavedLength = builder6388f0CallerLoopTableRows * builder6388f0CallerLoopInterleavedRowBytes
internal const val builder6388f0CallerContextLength = builder6388f0CallerLoopTable2ContextOffset + builder6388f0CallerLoopTableBytes
internal const val builder6388f0FirstPairStreamRows = builder6388f0CallerLoopTableRows * 2
internal const val builder6388f0LowSeedSeedBlocksBytes = 20 * 0x10
internal const val builder633fa8NullSeedBlocksBytes = 20 * builder633fa8NullSeedBlockBytes

/** The shifts the low seed path walks through. */
internal val builder6388f0LowSeedE10SourceShifts: IntArray = intArrayOf(4, 8, 16, 32, 64, 128, 256)

/** One step of the first setting up of the `679f48` context: where to read, and where to write. */
internal class ReducerSpec(val magic: ULong, val srcOffset: Int, val dstOffset: Int)

internal val aa8cInitialReducerSpecs: List<ReducerSpec> = listOf(
    ReducerSpec(0x400120000030e8uL, 0x114, 0x1ec),
    ReducerSpec(0x40012000000006uL, 0x126, 0x1f0),
    ReducerSpec(0x400120000022d5uL, 0x138, 0x1f4),
    ReducerSpec(0x40012000001859uL, 0x14a, 0x1f8),
    ReducerSpec(0x40012000005e7auL, 0x15c, 0x1fc),
    ReducerSpec(0x40012000004661uL, 0x16e, 0x200),
    ReducerSpec(0x4001200000178fuL, 0x180, 0x204),
    ReducerSpec(0x40012000002c8fuL, 0x192, 0x208),
)

internal val eb94UpdateMagics: ULongArray = ulongArrayOf(
    0x12000004154uL,
    0x1200000392cuL,
    0x120000036dbuL,
    0x12000000f83uL,
    0x120000000f9uL,
    0x12000000a72uL,
    0x120000018d7uL,
    0x1200000191duL,
)

/** The numbers the first setting up of the `process2(5)` public work area uses. */
internal object Process2P5PublicInitWorkspaceConstants {

    const val countMul: ULong = 0x94dfbb91a5378e68uL
    const val countAdd: ULong = 0x4218665245881823uL
    const val productMul: ULong = 0x501edede429b621fuL
    const val bPrefixMul: ULong = 0x6658ca76ca6e396auL
    const val aPrefixMul: ULong = 0x918160dbec5e059cuL
    const val finalMul: ULong = 0xbcb96bc3c168e865uL
    const val finalAdd: ULong = 0x242a710f34e73ceauL
}

/** Where a low word is copied to, and which table it comes from. */
internal class LowCopyOffset(val offset: Int, val tableIndex: Int)

internal val process2P5PublicLowCopyOffsets: List<LowCopyOffset> = listOf(
    LowCopyOffset(0x88, 1), LowCopyOffset(0x90, 0), LowCopyOffset(0x78, 2),
    LowCopyOffset(0x68, 4), LowCopyOffset(0x70, 3), LowCopyOffset(0x58, 6),
    LowCopyOffset(0x60, 5), LowCopyOffset(0x48, 8), LowCopyOffset(0x50, 7),
    LowCopyOffset(0x38, 10), LowCopyOffset(0x40, 9), LowCopyOffset(0x28, 12),
    LowCopyOffset(0x30, 11),
)

/** One of the three rounds of the low seed path. */
internal class LowSeedPhaseSpec(
    val phaseMagic: ULong,
    val auxMagics: ULongArray,
    val e10Magics: ULongArray,
    val e10Markers: IntArray,
    val bd0Magic: ULong,
    val staticOffset: Int,
    val unaryMagic: ULong,
    val f40Magics: ULongArray,
)

internal val builder6388f0LowSeedPhase1Spec = LowSeedPhaseSpec(
    phaseMagic = 0x10a000002563uL,
    auxMagics = ulongArrayOf(
        0x10a0000076a7uL, 0x10a000002de5uL, 0x10a000007af9uL, 0x10a000007e6buL,
        0x10a0000068a9uL, 0x10a0000062e7uL, 0x10a000007025uL,
    ),
    e10Magics = ulongArrayOf(
        0x10a0000008dcuL, 0x10a00000448cuL, 0x10a000000000uL, 0x10a000007493uL,
        0x10a0000039c1uL, 0x10a000000b56uL, 0x10a000005648uL,
    ),
    e10Markers = intArrayOf(3, 2, 3, 3, 7, 6, 3),
    bd0Magic = builder6388f0LowSeedPrev2BD0Magic,
    staticOffset = builder6388f0LowSeedPrev2Static,
    unaryMagic = 0x000c107000c03253uL,
    f40Magics = ulongArrayOf(
        0x0410006041004174uL, 0x040000a040002bb7uL, 0x03e001203e001e19uL,
        0x03a002203a005ba1uL, 0x0320042032003f90uL, 0x02200820220066f1uL,
        0x0020102002001526uL,
    ),
)

internal val builder6388f0LowSeedPhase2Spec = LowSeedPhaseSpec(
    phaseMagic = 0x10a0000046c4uL,
    auxMagics = ulongArrayOf(
        0x10a0000077b1uL, 0x10a0000038b7uL, 0x10a000001adeuL, 0x10a00000727fuL,
        0x10a0000069b3uL, 0x10a000007c03uL, 0x10a00000201buL,
    ),
    e10Magics = ulongArrayOf(
        0x10a000006f1buL, 0x10a00000553euL, 0x10a000005d93uL, 0x10a000001648uL,
        0x10a000002459uL, 0x10a000002aaduL, 0x10a0000005d0uL,
    ),
    e10Markers = intArrayOf(3, 1, 5, 7, 6, 0, 7),
    bd0Magic = builder6388f0LowSeedPreBD0Magic,
    staticOffset = builder6388f0LowSeedPreStatic,
    unaryMagic = 0x000c107000c01105uL,
    f40Magics = ulongArrayOf(
        0x04100060410052fauL, 0x040000a040001c0fuL, 0x03e001203e0028abuL,
        0x03a002203a007f75uL, 0x0320042032002275uL, 0x0220082022005168uL,
        0x0020102002002799uL,
    ),
)

internal val builder6388f0LowSeedPhase3Spec = LowSeedPhaseSpec(
    phaseMagic = 0x10a0000048fauL,
    auxMagics = ulongArrayOf(
        0x10a0000045bauL, 0x10a000000d8cuL, 0x10a0000019d4uL, 0x10a000003586uL,
        0x10a0000037aduL, 0x10a00000759duL, 0x10a000007389uL,
    ),
    e10Magics = ulongArrayOf(
        0x10a000004382uL, 0x10a00000010auL, 0x10a000005ec1uL, 0x10a000003c3buL,
        0x10a000007153uL, 0x10a000002125uL, 0x10a00000346auL,
    ),
    e10Markers = intArrayOf(3, 3, 0, 3, 1, 0, 5),
    bd0Magic = builder6388f0LowSeedTailBD0Magic,
    staticOffset = builder6388f0LowSeedTailStatic,
    unaryMagic = 0x000c107000c079dauL,
    f40Magics = ulongArrayOf(
        0x0410006041005993uL, 0x040000a040001212uL, 0x03e001203e0006dauL,
        0x03a002203a0060e5uL, 0x0320042032004c30uL, 0x022008202200655fuL,
        0x0020102002005752uL,
    ),
)

/** Where the first setting up of the `679f48` context reads and writes its blocks. */
internal const val init679f48Block66SrcOffset = 0

internal val init679f48Block66DstOffsets: IntArray = intArrayOf(0x08, 0x4a, 0x8c, 0xce)

internal val init679f48Block18Specs: List<ReducerSpec> = listOf(
    ReducerSpec(0x120000058e3uL, 0x42, 0x114),
    ReducerSpec(0x12000004b6buL, 0x54, 0x126),
    ReducerSpec(0x1200000388euL, 0x66, 0x138),
    ReducerSpec(0x12000000662uL, 0x78, 0x14a),
    ReducerSpec(0x12000000c90uL, 0x8a, 0x15c),
    ReducerSpec(0x120000045f6uL, 0x9c, 0x16e),
    ReducerSpec(0x12000000139uL, 0xae, 0x180),
    ReducerSpec(0x12000002cb1uL, 0xc0, 0x192),
)

/* The numbers and sources of the `6421c0` high seed builder. */

internal const val builder6421c0X0MulTable = 0x113f08
internal const val builder6421c0X0AddTable = 0x113628
internal const val builder6421c0X0FoldTable = 0x2feb18
internal const val builder6421c0X1MulTable = 0x11b288
internal const val builder6421c0X1AddTable = 0x118388
internal const val builder6421c0X1FoldTable = 0x2feb98
internal const val builder6421c0X2MulTable = 0x1183a8
internal const val builder6421c0X2AddTable = 0x11b2a8
internal const val builder6421c0X2FoldTable = 0x2fec18
internal const val builder6421c0WorkspaceFoldTable = 0x2fec98
internal const val builder6421c0RewriteFold1Table = 0x2fed18
internal const val builder6421c0RewriteFold2Table = 0x2fed98
internal const val builder6421c0FinalFoldTable = 0x2fee18
internal const val builder6421c0FinalOutMulTable = 0x115e48
internal const val builder6421c0FinalOutAddTable = 0x115308
internal const val builder6388f0HighSeedX0SourceMulTable = 0x11fd48
internal const val builder6388f0HighSeedX0SourceAddTable = 0x1152e8

internal val highSeed6421c0X2Source: ByteArray = bytesOf(
    0xd6, 0xce, 0x5d, 0x63, 0xde, 0x75, 0xb3, 0x91,
    0x43, 0x98, 0xc9, 0xa1, 0x23, 0x40, 0x76, 0x0f,
    0x3c, 0x69, 0x5a, 0x13, 0x9c, 0xbb, 0xc9, 0x13,
    0x5d, 0x94, 0xf6, 0x57, 0xb7, 0x29, 0x9c, 0xb1,
    0x82, 0x46, 0x31, 0x56, 0x4e, 0x88, 0x5b, 0x47,
    0x9d, 0x21, 0x1c, 0xae, 0xf3, 0x69, 0xd9, 0xea,
    0x19, 0xae, 0x4d, 0x0d, 0xc9, 0x70, 0x20, 0x4b,
    0x5d, 0x94, 0xf6, 0x84, 0xd7, 0xde, 0x58, 0xc2,
    0x35, 0xac, 0xa4, 0x60, 0xfc, 0x3d, 0xd5, 0xb4,
    0xc8, 0x46, 0x76, 0x15, 0xc0, 0xa7, 0xe6, 0xc0,
    0x19, 0xae, 0x4d, 0x0d, 0xc9, 0x70, 0x20, 0x4b,
)

internal val highSeed6421c0X1Source: ByteArray = bytesOf(
    0xf9, 0xb8, 0xa2, 0x3b, 0x79, 0x89, 0x3d, 0xab,
    0x28, 0xf6, 0x8f, 0x89, 0x3a, 0x72, 0x9b, 0xfc,
    0x43, 0x32, 0x3b, 0x85, 0x8f, 0xcb, 0xd6, 0x95,
    0xf4, 0xd2, 0x62, 0x09, 0x77, 0x91, 0x59, 0xaf,
    0xa1, 0x03, 0xdf, 0xee, 0x09, 0x58, 0xb8, 0x3b,
    0xb5, 0x0a, 0x88, 0x9d, 0x20, 0x4a, 0xad, 0xbb,
    0xa4, 0x80, 0x61, 0x06, 0xa7, 0x57, 0x0b, 0xca,
    0xf4, 0x52, 0x42, 0xee, 0x5f, 0xa7, 0xa2, 0x7e,
    0xac, 0x8b, 0xc6, 0xb0, 0x87, 0xa3, 0x03, 0x84,
    0xa3, 0xc2, 0xaf, 0x99, 0x20, 0x4a, 0xad, 0xbb,
    0xa4, 0x80, 0x61, 0x06, 0xa7, 0x57, 0x0b, 0xca,
)

internal const val highSeed6421c0Scalar: ULong = 0x68404ef676a9b7d3uL
