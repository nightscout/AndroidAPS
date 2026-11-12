package app.aaps.plugins.libre3.crypto.firstpair

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The published vectors of the low seed path and of the `633fa8` tail.
 *
 * Copied from LibreCRKit `Tests/LibreCRKitTests/FirstPairSourceSliceTests.swift` at pin `a86b92f`.
 */
class Libre3FirstPairLowSeedTest {

    private val entrySource = Vectors.pattern(0x214, 5, 1)

    @Test
    fun `the low seed rounds, the tail pair and the loop match the published vectors`() {
        val seeds = builder6388f0LowSeedCF0SeedsFromEntrySource(entrySource)
        assertThat(seeds.phase1.size).isEqualTo(0x10a)
        assertThat(seeds.phase2.size).isEqualTo(0x10a)
        assertThat(seeds.phase3.size).isEqualTo(0x10a)
        assertThat(Vectors.sha256(seeds.phase1))
            .isEqualTo("9b2d39eb30062613f7ccf5520345c80937d149f06c42c3816f9547388745df3b")
        assertThat(Vectors.sha256(seeds.phase2))
            .isEqualTo("a34a6cc968db094daaecfd303bbecdda322cd0e01c606c1705422f800dbf02fd")
        assertThat(Vectors.sha256(seeds.phase3))
            .isEqualTo("c60ed79f4e6da3aa3768e10d95a2c5837708652bb7e2c06c1b91b09adcb2d451")
        assertThat(Vectors.hex(seeds.phase3.copyOfRange(seeds.phase3.size - 16, seeds.phase3.size)))
            .isEqualTo("04020200010104050701060202000004")

        val pair = builder6388f0LowSeedTailPairFromEntrySource(entrySource)
        assertThat(Vectors.sha256(pair.left))
            .isEqualTo("2c31a9b72d1587155839611a00ebb6756ae34b459dbcbb10b7977ec6f2f85fa8")
        assertThat(Vectors.sha256(pair.right))
            .isEqualTo("2da6677e738231f8eeeec117db576aaffd4c1c495efd72bf847bf15813ed1d4c")

        val tailStage = builder6388f0LowSeedTailStageFromPair(pair)
        assertThat(Vectors.sha256(tailStage))
            .isEqualTo("05755c6dd9bc68d980beeef36392143524e6bbfcb20b270893509d57ebbc83a3")

        val prelude = builder6388f0LowSeedPreludeSourceFromTailStage(tailStage)
        assertThat(Vectors.sha256(prelude))
            .isEqualTo("e07c11f4368e33eb9812c3d31c186b76741a5b63eb7e77c1fd79a80dd680aaf2")

        val seedBlocks = builder6388f0LowSeedBlocksFromPreludeSource(prelude)
        assertThat(seedBlocks.size).isEqualTo(20 * 16)
        assertThat(Vectors.sha256(seedBlocks))
            .isEqualTo("77da5cce8122c3f8309100320249ddfc7d87e8a407e1699eb0d96032a3eb1283")

        val loop = builder6388f0LowSeedLoopFromBlocks(seedBlocks)
        assertThat(Vectors.hex(loop.final6377f0)).isEqualTo("010103020202020202040402040402030502")
        assertThat(loop.scheduleWords.copyOfRange(0, 4).toList())
            .isEqualTo(listOf(0x27985d74u, 0x602c800bu, 0xb5823fb5u, 0x3b970a6fu))
        assertThat(loop.scheduleWords.copyOfRange(16, 20).toList())
            .isEqualTo(listOf(0x1185db13u, 0x397e64c3u, 0xec257cd4u, 0x995e53ccu))

        val preimages = builder6388f0Row0LowSeedPreimagesFromEntrySource(entrySource)
        assertThat(Vectors.sha256(preimages.out4))
            .isEqualTo("e70d3f912b290b5bd31c6dd27e8816448c16863247354286fc66957bdf2a8e27")
        assertThat(Vectors.sha256(preimages.out3))
            .isEqualTo("feb5a841e9f99f5c149350296ffb74725c839af719015cfebfc2e1c01714acbc")
        assertThat(Vectors.sha256(preimages.out2))
            .isEqualTo("236c8c5040f999f86bfa6bdfd7f9e8e3ee79ce19a6568cf75fd6ef58880bced2")
    }

    @Test
    fun `the static scalar window matches the published vector`() {
        val boundary = builder633fa8StaticTailBoundaryFromEntrySource(entrySource)
        assertThat(Vectors.sha256(boundary.preludeSource))
            .isEqualTo("e07c11f4368e33eb9812c3d31c186b76741a5b63eb7e77c1fd79a80dd680aaf2")
        assertThat(Vectors.sha256(Vectors.packUInt32LE(boundary.words3ab0)))
            .isEqualTo("9bb588ed741963c1ed0b32efab701fbd87819dfe65f9e0192e8830e8a7a7574d")
        assertThat(Vectors.sha256(Vectors.packUInt32LE(boundary.words3120)))
            .isEqualTo("fe4e9fc8207e0cc3276f2cb073a8050bbaa842cd2b114165630eb8214fb30b01")
        assertThat(Vectors.sha256(Vectors.packUInt32LE(boundary.words2dfc)))
            .isEqualTo("2acd8bebf1f8746c4d0c264f28cd42010725f116a4587b702b29b75b8fbb2052")
        assertThat(boundary.seed3110).isEqualTo(0xb6ccf02833a9825euL)

        assertThat(Vectors.hex(builder633fa8StaticScalarWindowFromEntrySource(entrySource))).isEqualTo(
            "f38d95844ac5834265c854266814ed9e67ce508eea912fc81a9b2d28db0ddd5e" +
                "0000000000000000000000000000000000000000000000000000000000000000000000000000"
        )
    }

    @Test
    fun `the tail long words match the published vector`() {
        val words3ab0 = uintArrayOf(
            0x561f0a13u, 0x2703b81fu, 0xc60ebb71u, 0x13ae9923u, 0x6151794du,
            0xcbd488b3u, 0x105a57bau, 0xbe270b51u, 0x35178421u, 0x9c1e6b02u,
            0x8131d744u, 0x995e53ccu, 0xe98d93e2u, 0xbcf84415u, 0xbfccce8eu,
            0x6c32338cu, 0xd608b5a1u, 0xe7c2db10u, 0x8131d744u, 0x995e53ccu,
        )
        val qwords = builder633fa8TailQwordsFromSources(
            words3ab0, builder633fa8InvariantWords3120, builder633fa8InvariantWords2dfc, 0xb6ccf02833a9825euL,
        )
        assertThat(qwords.toList()).isEqualTo(
            listOf(
                0x278653e978fb8d86uL, 0x01531105e76d5345uL, 0x6ca239d879644a5cuL, 0xa06b5f9758fb4bd5uL,
                0xd4aba6030256919auL, 0x701b8d245771a9c8uL, 0x25f9e61e7612a2cbuL, 0x42af4c71aeed4949uL,
                0xf69e5c8932e52f6cuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL,
                0x7785655189e16a0fuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL,
                0x7785655189e16a0fuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL,
            )
        )
        assertThat(Vectors.sha256(Vectors.packUInt64LE(qwords)))
            .isEqualTo("8718a3b565f0e38d8631d894877d72c491cfaa21abccc8958829a7b0ca97b15d")

        val e10Words = builder633fa8E10WordsFromTailQwords(qwords)
        assertThat(Vectors.hex(builder633fa8ScalarWindowFromE10Words(e10Words))).isEqualTo(
            "4532bea83bfdabcf74fdaeeb0319a83c051a31e40a620e3bd0db1cd993ed8522" +
                "0000000000000000000000000000000000000000000000000000000000000000000000000000"
        )
    }

    @Test
    fun `the E10 words match the published vector`() {
        val tailQwords = ulongArrayOf(
            0x278653e978fb8d86uL, 0x01531105e76d5345uL, 0x6ca239d879644a5cuL, 0xa06b5f9758fb4bd5uL,
            0xd4aba6030256919auL, 0x701b8d245771a9c8uL, 0x25f9e61e7612a2cbuL, 0x42af4c71aeed4949uL,
            0xf69e5c8932e52f6cuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL,
            0x7785655189e16a0fuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL,
            0x7785655189e16a0fuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL,
        )
        val words = builder633fa8E10WordsFromTailQwords(tailQwords)
        assertThat(words.toList()).isEqualTo(
            listOf(
                0x5a1e4b39u, 0x5e9483afu, 0xcf48138fu, 0x9e28b8cdu, 0x55b48903u,
                0xdefd3261u, 0x2c462f90u, 0x5d22446du, 0x5170b893u, 0xdcd2fa37u,
                0xfaacce40u, 0x997a6babu, 0x7781207bu, 0x182c4538u, 0x5475ee9au,
                0xf1fd3b9cu, 0x8281f8c2u, 0x0ba21025u, 0xfaacce40u, 0x997a6babu,
            )
        )
        assertThat(Vectors.sha256(Vectors.packUInt32LE(words)))
            .isEqualTo("4f7646b6cb17189560193adc7b951d47443edf292ea8213d2481cd8c89ba79a9")
    }

    @Test
    fun `the scalar window packing matches the published vector`() {
        val e10Words = uintArrayOf(
            0xf15eecb3u, 0x6c31d20du, 0x7a812282u, 0x88c66764u, 0xc7daeb98u,
            0xcb55b447u, 0x7dc4c98au, 0xe8533b12u, 0x3976a2b8u, 0x39a2c9bdu,
            0xa7ca28eau, 0x6e74c495u, 0x06708db4u, 0x5a2caf42u, 0xedb8643du,
            0xd19d3544u, 0x8281f8c2u, 0x0ba21025u, 0xfaacce40u, 0x997a6babu,
        )
        val scalar = builder633fa8ScalarWindowFromE10Words(e10Words)
        assertThat(scalar.size).isEqualTo(70)
        assertThat(Vectors.hex(scalar)).isEqualTo(
            "f38d95844ac5834265c854266814d19822125ef87edcfcab64db2fd1a3b4b0e7a" +
                "d6a1fa15f51ce7eea7853023be2e9ecb5a99876f7a8a0e00000000000000000000000000000"
        )
        assertThat(Vectors.sha256(scalar))
            .isEqualTo("af6aea9e701fb090af64b2446d8ccdef01327837f264bbe65b20db784345fa16")
    }
}
