package app.aaps.plugins.libre3.crypto.firstpair

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The published vectors of the `642f60` arguments, the `6421c0` builder and the point multiply.
 *
 * Copied from LibreCRKit `Tests/LibreCRKitTests/FirstPairSourceSliceTests.swift` at pin `a86b92f`.
 */
class Libre3FirstPairHighSeedTest {

    private fun bytePattern(count: Int, multiplier: Int, addend: Int): ByteArray =
        ByteArray(count) { ((it * multiplier + addend) and 0xff).toByte() }

    @Test
    fun `the three arguments built from three 64cd40 answers match the published vector`() {
        val next = builder6388f0Next642f60InputsFrom64cd40Outputs(
            bytePattern(88, 3, 1), bytePattern(88, 5, 2), bytePattern(88, 7, 4),
        )
        assertThat(Vectors.hex(next.x0)).isEqualTo(
            "727ff3b03b7f9b9445dc32088470dba2e6560584f7811dda9c792f21b509e1d2" +
                "92391698dbe3f37f255093af64049e2b463bc0751707c7423cc615e155ef3aa8" +
                "b2f3387f7b484c6b05c4f356449860b4a61f7b67378c0d92"
        )
        assertThat(Vectors.hex(next.x1)).isEqualTo(
            "82da21c2812f75174d7cb165a116dbf009e53aea970434b39f33459e272f861b" +
                "e27b90bce1deb6322d5fcb2a41d541dfa991d42677480a7cbfe36d5d8767d5e1" +
                "424e8159410f29ef0dd7bbe7e1623750495d780257d74f2d"
        )
        assertThat(Vectors.hex(next.x2)).isEqualTo(
            "24966d1c50f77db7f8f3f44c7c6f1c9008083749fa291b844bf1adcccc9903b8" +
                "445a06e7b06968ce9823d9b09c5ab1b3687801c69a9ee22fab48de1eac457f9" +
                "56487f0801049ff2d38d61817bc4546d7c8e8cb423a13aadb"
        )
    }

    @Test
    fun `the stream start arguments match the published vector and can be undone`() {
        val out0Seed = bytePattern(88, 9, 5)
        val out1Seed = bytePattern(88, 11, 7)
        val start = builder6388f0StreamStart642f60Inputs(out0Seed, out1Seed)

        assertThat(Vectors.hex(start.x0)).isEqualTo(
            "6a98ace04c88d51d6ab1d75da53dff508ffdea49d14182592c7500a439baad09" +
                "8a47a5a0ecaafcc40aeef4178563c64f2f9e8ba1f142af1f8c2af649193b49f6" +
                "aaf69d608ccd236caa2a12d265898d4ecf3e2cf91144d37d"
        )
        assertThat(Vectors.hex(start.x1)).isEqualTo(
            "b5912a7855f4aa29d3882cbc13838a3ddb7e261648807def1c24491cda8c00ef" +
                "959d913335cd3f2ef30e15433318e7babb6b628ea8c7820d7ca628d67a01846" +
                "c75a9f8ee15a6d44513dae60c53ca4cfd9bdda4ac08d0b22e"
        )
        assertThat(start.x2).isEqualTo(streamStart642f60X2Source)
        assertThat(Vectors.sha256(start.x2))
            .isEqualTo("64eec98b6cf193a8c6f413af4eb1ed6bb4d4f06cb6c343284c46c9ce85ebde6f")

        assertThat(builder6388f0RecoverStreamStartOut0SeedFrom642f60X0(start.x0)).isEqualTo(out0Seed)
        assertThat(builder6388f0RecoverStreamStartOut1SeedFrom642f60X1(start.x1)).isEqualTo(out1Seed)
    }

    @Test
    fun `the 6421c0 builder, the high seeds and the point multiply match the published vectors`() {
        val output = builder6421c0OutputWords(
            bytePattern(80, 11, 7), bytePattern(88, 13, 3), bytePattern(88, 17, 5), 0x0123456789abcdefuL,
        )
        assertThat(output.toList()).isEqualTo(
            listOf(
                0xdbc1c7c6u, 0x2033fae4u, 0xdbba46f4u, 0x51d8e106u, 0x06acf332u,
                0x8bad4314u, 0xb5c9adb4u, 0x54da2609u, 0x4ea01830u, 0x00da7af7u,
                0x207da04au, 0xbaa6764du, 0x0e8a02aau, 0x41fc4b04u, 0x299ed743u,
                0xa8d7eaf6u, 0x088c1fe0u, 0x83d47285u, 0x9d6a5499u, 0x640e0bb3u,
                0x799af52du, 0xa7308434u,
            )
        )
        assertThat(Vectors.sha256(Vectors.packUInt32LE(output)))
            .isEqualTo("613beae0326a26de5b07c1bca00a356d6d497e222d8cfe34e4cb84ae26be14a8")

        val source70 = bytePattern(70, 19, 9)
        val highX0 = builder6388f0HighSeedX0SourceFrom5bcf98Output(source70)
        assertThat(Vectors.hex(highX0)).isEqualTo(
            "3a2dfcb318b7344cf51e5b96b0815468383cdb6a10f727cbc3e953daf6513562" +
                "261b8e3aeda4df7071901e85677af85bdc709a418e53b9f15fe323f7937078" +
                "c9120920c1c2928a95ed01e2731e03cdea"
        )
        val highOutput = builder6421c0OutputWords(
            highX0, highSeed6421c0X1Source, highSeed6421c0X2Source, highSeed6421c0Scalar,
        )
        assertThat(highOutput.toList()).isEqualTo(
            listOf(
                0x808a1855u, 0x783ef112u, 0x27aa1861u, 0x18f09114u, 0x3d286c05u,
                0x83db42f3u, 0x57a5bb1eu, 0x208b0c9eu, 0x64223ac2u, 0x97cc4564u,
                0x0ef21945u, 0xe627151fu, 0xd8178670u, 0xdba71039u, 0xdcae32d6u,
                0x26e1b50bu, 0x8fb269cbu, 0x6bcc9065u, 0x9d1492afu, 0x94fe8376u,
                0xd8178670u, 0xdba71039u,
            )
        )
        assertThat(Vectors.sha256(Vectors.packUInt32LE(highOutput)))
            .isEqualTo("cbe9227ccdfa92d4e23f2bd4f11e67cc0ef66de6b945f812bd0c2d213b7afd93")

        val highSeeds = builder6388f0HighSeedStreamStartSeedsFrom5bcf98Outputs(source70, bytePattern(70, 23, 4))
        assertThat(highSeeds.out0).isEqualTo(Vectors.packUInt32LE(highOutput))
        assertThat(Vectors.hex(highSeeds.out1)).isEqualTo(
            "8d85f2399a367b70a58ac991bc36c7604a45a128e91d968a99cb7b3dd020a2b5" +
                "f7b82949a78159b9b962810cbd3e57fd708617d83910a7dbd632aedc0bb5e126" +
                "cb69b28f6590cc6baf92149d7683fe94708617d83910a7db"
        )
        assertThat(Vectors.sha256(highSeeds.out0 + highSeeds.out1))
            .isEqualTo("bd9129fe22f4ab7d395e31c3e369cfc6cc62b3109df0b4ec7d82cffa5117d0e2")
    }

    @Test
    fun `the point multiply matches the published vector on the curve generator`() {
        val scalarWindow = hexBytes(
            "3b588dd68f20da5f883993332cabcda6576645712cdd039d0a8195f4b1c0b52e" +
                "0000000000000000000000000000000000000000000000000000000000000000000000000000"
        )
        val generatorPoint = hexBytes(
            "6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296" +
                "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5"
        )
        val outputs = builder5bcf98P256Outputs(scalarWindow, generatorPoint)
        assertThat(Vectors.sha256(outputs.xOutput70))
            .isEqualTo("fb123cffe9d4e8e9e27f9c5251cdcd24a9f513c43d130a07925b8ceee0fe75d0")
        assertThat(Vectors.sha256(outputs.yOutput70))
            .isEqualTo("5f6979616cb8bbeb57dac5b362653508b597e8292b6bc0a3defc0787cc4737ca")
        assertThat(Vectors.hex(outputs.xOutput70)).isEqualTo(
            "a1e69a746868223565f55b036dcb352ac7ad64457d8304d2a015b5ee90942023" +
                "0000000000000000000000000000000000000000000000000000000000000000000000000000"
        )
        assertThat(Vectors.hex(outputs.yOutput70)).isEqualTo(
            "3ac85ab9f4754fade9fb79588ec4d48ef3af4d916151ad0477d595de947261ea" +
                "0000000000000000000000000000000000000000000000000000000000000000000000000000"
        )

        val p256HighSeeds = builder6388f0HighSeedStreamStartSeedsFromScalarP256(scalarWindow, generatorPoint)
        assertThat(Vectors.sha256(p256HighSeeds.out0))
            .isEqualTo("fbc744031431d9fda2ceed80266ee2dfefb9a55e585ea5bbc2666a144379f042")
        assertThat(Vectors.sha256(p256HighSeeds.out1))
            .isEqualTo("f9b223b45fe8ec5687cdcbd18218714f4b261938a4befb37e0ced7b42d012289")
    }
}
