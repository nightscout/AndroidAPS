package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException
import app.aaps.plugins.libre3.crypto.Libre3Phase5KeySchedule
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The published vectors of the last layer: closing a context, the `df80` round function, and the
 * four ways a slice is read out of a finished context.
 *
 * Copied from LibreCRKit `Tests/LibreCRKitTests/FirstPairSourceSliceTests.swift` at pin `a86b92f`.
 *
 * Every function here is also walked by the end to end vector in `Libre3FirstPairSourceTest`, but
 * only these vectors say **where** a wrong bit came from. They are what turns a failing pairing
 * into a failing line.
 */
class Libre3FirstPairFinalizerTest {

    private val df80Blocks = Vectors.pattern(4 * 66, 5, 3)
    private val df80State = Vectors.pattern(8 * 18, 7, 2)

    @Test
    fun `closing a context matches both published vectors`() {
        val vectors = listOf(
            Triple(132uL, 2u, Triple(
                "010602060707010605040204060404050206040406050406020404040402020402" +
                    "020204040506060504020604060604040502040504020602040505050604050502",
                "0a47106ad8b1d372b9f821d1ed1c421c9a33ac9c6f91e350c8bd89e4d1497143",
                "040407050303060105040303000500050302040700030303070502040102030405" +
                    "000604000506020200030003060500040200000306060700070205070206030600",
            )),
            Triple(128uL, 3u, Triple(
                "010606060505040605040204060404050206040406050406020404040402020402" +
                    "020204040506060504020604060604040502040504020602040505050604050506",
                "17967df31a85c7937a2c2c0da54297a4f45387ede33b3c8aa10d8f584443c20f",
                "040402060207050701070101000501060401010007010403050107000401060205" +
                    "070107070404010306000003050006020503030402070706000100010206040004",
            )),
        )
        for ((contextLength, blockIndex, expected) in vectors) {
            val (finalLen, contextHash, source) = expected
            val context = Vectors.make679f48Context(contextLength, blockIndex)
            assertThat(Vectors.hex(final679f48LengthBlock(contextLength.toInt()))).isEqualTo(finalLen)
            assertThat(Vectors.sha256(finalize679f48ToSecondDF80(context))).isEqualTo(contextHash)
            assertThat(Vectors.hex(deriveFrom679f48Context(context, offset = 0, length = 16))).isEqualTo(source)
        }
    }

    @Test
    fun `a context with a block index that cannot be is refused`() {
        assertThrows<Libre3CryptoException> {
            finalize679f48ToSecondDF80(Vectors.make679f48Context(132uL, 5u))
        }
    }

    @Test
    fun `the df80 round function matches the published vector`() {
        val transformed = df80Transform(df80State, df80Blocks)

        assertThat(transformed.size).isEqualTo(144)
        assertThat(Vectors.sha256(transformed))
            .isEqualTo("83d6b1d8af5c9ae3696aa44b9f62680633b0a996598b934781aa571ba0bbe58d")
        assertThat(Vectors.hex(transformed.copyOfRange(0, 72))).isEqualTo(
            "060505060603010607050500030303010703040102050702030300020103040406" +
                "060304030403060005010706050001060203000404030301030000040706010105" +
                "000102030607"
        )
        assertThat(Vectors.hex(transformed.copyOfRange(transformed.size - 72, transformed.size))).isEqualTo(
            "040707050405010406070505010204020502060200000307060605040407020503" +
                "070404070704010106060002050303040503010604070007070402050504070703" +
                "040704070607"
        )

        val workspace = df80InitialWorkspace(df80Blocks)
        val schedule = df80ExpandedSchedule(workspace)
        assertThat(df80CompressState(df80State, schedule)).isEqualTo(transformed)
    }

    @Test
    fun `the df80 steps refuse inputs of the wrong size`() {
        assertThrows<Libre3CryptoException> { df80CompressState(ByteArray(143), ByteArray(0x480)) }
        assertThrows<Libre3CryptoException> { df80CompressState(ByteArray(144), ByteArray(0x47f)) }
        assertThrows<Libre3CryptoException> { df80ExpandedSchedule(ByteArray(287)) }
        assertThrows<Libre3CryptoException> { df80InitialWorkspace(ByteArray(263)) }
    }

    @Test
    fun `the df80 schedule and work area match the published vectors`() {
        val workspace = df80InitialWorkspace(df80Blocks)
        assertThat(Vectors.hex(workspace)).isEqualTo(
            "020606050700020404010501070401010504060201020704010204010501060601" +
                "010305020601020201030306000307070005050200040200040203020404020407" +
                "030401070102020600020104040506050500010603040207060207040106030303" +
                "050500020506040501020605070606040204030206040403040502060206060301" +
                "070106000103000603050002020606040301060100020107060205070303060204" +
                "060300050606000107050405070702020604060702060001070305060105070704" +
                "000402050404060100040705010205010207020605010505000002070601050207" +
                "060001060203000502070503040305020107060504020603000302000407030707" +
                "010507060106030301040304000002060007020004030006"
        )

        val schedule = df80ExpandedSchedule(workspace)
        assertThat(schedule.size).isEqualTo(0x480)
        assertThat(Vectors.sha256(schedule))
            .isEqualTo("19a0a495eb712175fc15dda37e6a5719940376609560f2ca26e3586abde2db77")
        assertThat(Vectors.hex(schedule.copyOfRange(0x120, 0x120 + 72))).isEqualTo(
            "020607070005060306030005020607060101020000050407040500050007030003" +
                "000706030505050407040304000200040402050302010205040206000104010503" +
                "050402060306"
        )
        assertThat(Vectors.hex(schedule.copyOfRange(schedule.size - 72, schedule.size))).isEqualTo(
            "030505060104000601010201060203060004070203010405040204000207040103" +
                "060500020604000107000107030305000001000005010004010706020701070400" +
                "050103070204"
        )
    }

    @Test
    fun `the length block matches all four published vectors`() {
        val vectors = listOf(
            0 to "010606060404040505040204060404050206040406050406020404040402020402" +
                "020204040506060504020604060604040502040504020602040505050604050506",
            4 to "010602060101040505040204060404050206040406050406020404040402020402" +
                "020204040506060504020604060604040502040504020602040505050604050502",
            68 to "010602060701070707010204060404050206040406050406020404040402020402" +
                "020204040506060504020604060604040502040504020602040505050604050502",
            132 to "010602060707010605040204060404050206040406050406020404040402020402" +
                "020204040506060504020604060604040502040504020602040505050604050502",
        )
        for ((contextLength, expected) in vectors) {
            assertThat(Vectors.hex(final679f48LengthBlock(contextLength))).isEqualTo(expected)
        }
    }

    @Test
    fun `reading a slice out of encoded blocks matches both published offsets`() {
        val encoded = Vectors.pattern(66 * 2, 3, 1)
        assertThat(Vectors.hex(derive64de54Slice(encoded, offset = 0, length = 16))).isEqualTo(
            "040404000403000004010706050306060105010204060302010504030702000005" +
                "040100010000060707050301030202050401000403030601050100040200020405"
        )
        assertThat(Vectors.hex(derive64de54Slice(encoded, offset = 16, length = 16))).isEqualTo(
            "040407000000070705020503020004070506070000030105040307060003070705" +
                "030702030207040100070200030105070205030003010407060403000307040103"
        )
        assertThrows<Libre3CryptoException> { derive64de54Slice(ByteArray(65)) }
    }

    @Test
    fun `the 67cc18 sources give the published slices and keys`() {
        val source = Vectors.pattern(66 * 2, 5, 2)
        assertThat(Vectors.hex(deriveFrom67cc18Sources(source, offset = 0, length = 16))).isEqualTo(
            "040400010204020403070505010102040500070506070505040300070500020600" +
                "000406070603060706070607020602010602010202010400040206060406000602"
        )
        assertThat(Vectors.hex(deriveFrom67cc18Sources(source, offset = 16, length = 16))).isEqualTo(
            "040404030306020107070006010600010003020602000507000601000701020004" +
                "000004010506000003060407010603030005020605010101070405020206020702"
        )
        assertThat(Vectors.hex(Libre3Phase5KeySchedule.deriveRawKey(deriveFrom67cc18Sources(source, offset = 0))))
            .isEqualTo("1fc9367dbfe4d23015419023b8ff18b6")
        assertThat(Vectors.hex(Libre3Phase5KeySchedule.deriveRawKey(deriveFrom67cc18Sources(source, offset = 16))))
            .isEqualTo("4b92eac60192ed83e6666a2810a936a6")
    }

    @Test
    fun `the 67a960 inputs give the published slices and keys`() {
        val src1 = Vectors.pattern(130, 3, 4)
        val src2 = Vectors.pattern(130, 5, 1)
        val vectors = listOf(
            Triple(
                0,
                "040400040302000203010504050606010602060206010706040604020201000605" +
                    "060300050706070506050406030505060205040406070504060105050706010702",
                "1a36bec545101e734f469c930b565b59",
            ),
            Triple(
                16,
                "040400000700070601050100070301030104070402060100060100050502030707" +
                    "030004040000020104020600040306040607040304000206050404040402060606",
                "61efbe0d4f32b0c424a29ff609c73a18",
            ),
        )
        for ((offset, expectedSource, expectedKey) in vectors) {
            val source = deriveFrom67a960Inputs(src1, src2, offset, length = 16)
            assertThat(Vectors.hex(source)).isEqualTo(expectedSource)
            assertThat(Vectors.hex(Libre3Phase5KeySchedule.deriveRawKey(deriveFrom67a960Inputs(src1, src2, offset))))
                .isEqualTo(expectedKey)
        }
    }

    @Test
    fun `a finished context gives the published slices and keys`() {
        val context = Vectors.pattern(0x20c, 7, 3)
        val vectors = listOf(
            Triple(
                0,
                "040405070607060600000304050601020107020701070601000201030506040705" +
                    "060707060203060200050700050006050303040107020404040607010306000605",
                "1e6348e3a52751cbac7cc95200f39d9e",
            ),
            Triple(
                16,
                "040405010207070104030605020701010403030404070701070005030507010101" +
                    "070705070202030500000005010301060606010703040400070207020302020707",
                "b2f4925e0545eb07acd86a4c00beee05",
            ),
        )
        for ((offset, expectedSource, expectedKey) in vectors) {
            assertThat(Vectors.hex(deriveFromFinalized679f48Context(context, offset, length = 16)))
                .isEqualTo(expectedSource)
            assertThat(
                Vectors.hex(Libre3Phase5KeySchedule.deriveRawKey(deriveFromFinalized679f48Context(context, offset)))
            ).isEqualTo(expectedKey)
        }
    }
}
