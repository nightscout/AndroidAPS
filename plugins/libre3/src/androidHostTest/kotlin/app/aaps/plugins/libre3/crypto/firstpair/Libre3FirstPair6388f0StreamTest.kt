package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3Phase5KeySchedule
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The published vectors of the `6388f0` stream, one layer at a time.
 *
 * Copied from LibreCRKit `Tests/LibreCRKitTests/FirstPairSourceSliceTests.swift` at pin `a86b92f`.
 */
class Libre3FirstPair6388f0StreamTest {

    private val internalBlocks = Vectors.pattern(2 * 66, 5, 1)
    private val prefinal = Vectors.pattern(2 * 66, 3, 2)

    @Test
    fun `the four tail layers match the published vectors`() {
        val workspace = Vectors.pattern(266, 7, 4)
        val stageA = Vectors.pattern(282, 5, 2)
        val stageB = Vectors.pattern(282, 3, 6)

        val finalRaw = builder6388f0FinalRawBlocks(internalBlocks)
        assertThat(finalRaw.size).isEqualTo(132)
        assertThat(Vectors.sha256(finalRaw))
            .isEqualTo("56ddfdbf0fcc1b60f339a70d9cba7e8262d02c9e7bed64970126f795fb635576")

        val prefinalInternal = builder6388f0PrefinalLen32InternalBlocks(prefinal)
        assertThat(prefinalInternal.size).isEqualTo(132)
        assertThat(Vectors.sha256(prefinalInternal))
            .isEqualTo("b6af68238f927cb2f27d318fa8fb6e0c77494d16555c4dd64a06d1376544995a")

        val workspacePrefinal = builder6388f0Len32PrefinalSourcesFromWorkspace(workspace)
        assertThat(workspacePrefinal.size).isEqualTo(132)
        assertThat(Vectors.sha256(workspacePrefinal))
            .isEqualTo("b7fed8b0449eb2368269402165ea6bf581f68580bdb3a1719b0ecf14ac2f9172")

        val stagePrefinal = builder6388f0Len32PrefinalSourcesFromStageInputs(stageA, stageB)
        assertThat(stagePrefinal.size).isEqualTo(132)
        assertThat(Vectors.sha256(stagePrefinal))
            .isEqualTo("cc33aa4c896081feb2a44b378afd6b9830ca655c174f6d507c9716e8a68a7830")
    }

    @Test
    fun `the four stream entry points match the published slices`() {
        assertThat(Vectors.hex(deriveFrom6388f0InternalStreams(internalBlocks, prefinal, offset = 0, length = 16))).isEqualTo(
            "040400040702000206000504060306040706020100040303030205020506030603" +
                "020207050704040602060006000600020002020305050000020401070006030103"
        )

        assertThat(Vectors.hex(deriveFrom6388f0PrefinalLen32Streams(prefinal, internalBlocks, offset = 0, length = 16))).isEqualTo(
            "040400000703050704050203030503020706030400060402020001060302050104" +
                "070104060000030706020104020007060300040207050502050502000405050700"
        )

        val workspaceA = Vectors.pattern(266, 7, 4)
        val workspaceB = Vectors.pattern(266, 7, 5)
        assertThat(Vectors.hex(deriveFrom6388f0WorkspaceLen32Streams(workspaceA, workspaceB, offset = 0, length = 16))).isEqualTo(
            "040407040606030500050503030005050606010206050607040407000306050202" +
                "070100040200040407070303020600010302040007020501000306000406020107"
        )

        val first = Libre3StageInputs(Vectors.pattern(282, 5, 2), Vectors.pattern(282, 3, 6))
        val second = Libre3StageInputs(Vectors.pattern(282, 3, 1), Vectors.pattern(282, 5, 4))
        assertThat(Vectors.hex(deriveFrom6388f0StageLen32Streams(first, second, offset = 0, length = 16))).isEqualTo(
            "040400030102000103000703030201010707070106000506020306040203060202" +
                "070203000305040200010504020501070103070706000700040501060003060005"
        )
    }

    @Test
    fun `the pack layer and the lane layer match the published vectors`() {
        val lanes0 = Libre3LaneBlocks(Vectors.pattern(20 * 16, 3, 1), Vectors.pattern(20 * 16, 5, 2))
        val lanes1 = Libre3LaneBlocks(Vectors.pattern(20 * 16, 7, 4), Vectors.pattern(20 * 16, 3, 6))

        val pack0 = builder6388f0PackOutputsFromLaneBlocks(lanes0)
        assertThat(Vectors.sha256(pack0.stageBPackHead16))
            .isEqualTo("cbb73fff67fc8d84576195e087bdadabd862476bea09fd3603ec19f75f703f28")
        assertThat(Vectors.sha256(pack0.stageBPackBody16))
            .isEqualTo("1ed65e8008b64c579a8dc5ef91bbb8026a7bd10180c16fd9022395cc6f21e583")
        assertThat(Vectors.sha256(pack0.stageAPackHead16))
            .isEqualTo("0419abe716a28762aae3d3abdfcbd20069e74db5f4cc62060cbf32f11ed3c2ea")
        assertThat(Vectors.sha256(pack0.stageAPackBody16))
            .isEqualTo("ec559fc571319e7d69e31ba7761b9035072bcddc75324c3de9f78b7eca830560")

        val stage0 = builder6388f0Len32StageInputsFromPackOutputs(pack0)
        assertThat(Vectors.sha256(stage0.stageASource))
            .isEqualTo("fbece039249b3700c3908af39c16cfdffda2a8e94765ce1243280ede14a0db22")
        assertThat(Vectors.sha256(stage0.stageBSource))
            .isEqualTo("55622eae1f29c6264a33d62e2b41fb3875832683281b456831037d8a4e01d4f6")

        val pack1 = builder6388f0PackOutputsFromLaneBlocks(lanes1)
        val sourceFromPack = deriveFrom6388f0PackLen32Streams(pack0, pack1, offset = 0, length = 16)
        assertThat(Vectors.hex(sourceFromPack)).isEqualTo(
            "040400060501060401060002050500050600010504000007060300050100000407" +
                "060406030405050001050500010002050100020501010304040405040706050200"
        )

        val sourceFromLanes = deriveFrom6388f0LaneLen32Streams(lanes0, lanes1, offset = 0, length = 16)
        assertThat(sourceFromLanes).isEqualTo(sourceFromPack)
    }

    @Test
    fun `the schedule layer gives the published slice and the published key`() {
        val schedule0 = uintArrayOf(
            0x11223344u, 0x12243648u, 0x1326394cu, 0x14283c50u, 0x152a3f54u,
            0x162c4258u, 0x172e455cu, 0x18304860u, 0x19324b64u, 0x1a344e68u,
            0x1b36516cu, 0x1c385470u, 0x1d3a5774u, 0x1e3c5a78u, 0x1f3e5d7cu,
            0x20406080u, 0x21426384u, 0x22446688u, 0x2346698cu, 0x24486c90u,
        )
        val schedule1 = uintArrayOf(
            0x89abcdefu, 0x8aaccef0u, 0x8badcff1u, 0x8caed0f2u, 0x8dafd1f3u,
            0x8eb0d2f4u, 0x8fb1d3f5u, 0x90b2d4f6u, 0x91b3d5f7u, 0x92b4d6f8u,
            0x93b5d7f9u, 0x94b6d8fau, 0x95b7d9fbu, 0x96b8dafcu, 0x97b9dbfdu,
            0x98badcfeu, 0x99bbddffu, 0x9abcdf00u, 0x9bbde001u, 0x9cbee102u,
        )

        val lanes0 = builder6388f0LaneBlocksFromScheduleWords(schedule0)
        assertThat(Vectors.sha256(lanes0.primaryLaneBlocks))
            .isEqualTo("1b70254a30185288de09f9a35ec6b1293474b349a720f89794631dd5cd43c2f8")
        assertThat(Vectors.sha256(lanes0.secondaryLaneBlocks))
            .isEqualTo("d718360163754dcd6fc33adfe4d184ce65e7f9f6e9bd32b4fcb677b425845bc1")

        val lanes1 = builder6388f0LaneBlocksFromScheduleWords(schedule1)
        assertThat(Vectors.sha256(lanes1.primaryLaneBlocks))
            .isEqualTo("2ebd449c1b18906b11e48c56f3bdef9cb98072e5be686a4b89cb02b2dab45d04")
        assertThat(Vectors.sha256(lanes1.secondaryLaneBlocks))
            .isEqualTo("cbc6175ef451f45406d268a601058f257271e2ddcd12d6a773c0b51f48c8f2f0")

        val source = deriveFrom6388f0ScheduleLen32Streams(schedule0, schedule1, offset = 0, length = 16)
        assertThat(Vectors.hex(source)).isEqualTo(
            "040401070203010704050106010001020204070005060100030704020306000701" +
                "070104060605050201000402010102050201000404040305070103060002030602"
        )

        // The key that this source makes, through the schedule that is already ported and proven.
        assertThat(Vectors.hex(Libre3Phase5KeySchedule.deriveRawKey(source)))
            .isEqualTo("e407917d692fd119fbf18baf60644ded")
    }
}
