package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3Phase5KeySchedule
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The published vectors of the `63c278` schedule builder, one layer at a time.
 *
 * Copied from LibreCRKit `Tests/LibreCRKitTests/FirstPairSourceSliceTests.swift` at pin `a86b92f`.
 */
class Libre3FirstPair63c278Test {

    private fun bytePattern(count: Int, multiplier: Int, addend: Int): ByteArray =
        ByteArray(count) { ((it * multiplier + addend) and 0xff).toByte() }

    @Test
    fun `the mixers, the tails, the accumulator and the bridge match the published vectors`() {
        val arg0 = bytePattern(88, 7, 3)
        val arg1 = bytePattern(88, 5, 11)
        val arg2 = bytePattern(88, 3, 17)
        val scalar = 0x0123456789abcdefuL

        val initial = builder63c278InitialVectors(arg0, arg1)
        assertThat(initial.vec44.size).isEqualTo(44)
        assertThat(initial.x0Vec22.size).isEqualTo(22)
        assertThat(Vectors.sha256(Vectors.packUInt64LE(initial.vec44)))
            .isEqualTo("7510279962382f9fbfd7acbc437c419e0efe3aa928c3b86a09fa3235880799d1")
        assertThat(Vectors.sha256(Vectors.packUInt64LE(initial.x0Vec22)))
            .isEqualTo("cd3066baa97e86c4cd0882325622da57d6283f598225b6c7b19f3e066e18a9d3")

        val second = builder63c278SecondInitialVectors(arg0, arg2)
        assertThat(Vectors.sha256(Vectors.packUInt64LE(second.vec44)))
            .isEqualTo("571998e49bf13bc6a2e9d7df592371186beacebb27c8fd7d498924f41591f53e")
        assertThat(Vectors.sha256(Vectors.packUInt64LE(second.x0Vec22)))
            .isEqualTo("dbbd9840317de13798bd1cf50ef6fec8ad0e26638b894a62b1cfd14aee785fda")

        val mixed1 = builder63c278ScalarMixVector(initial.vec44, initial.x0Vec22, scalar)
        val mixed2 = builder63c278ScalarMix2Vector(second.vec44, second.x0Vec22, scalar)
        assertThat(Vectors.sha256(Vectors.packUInt64LE(mixed1)))
            .isEqualTo("4692583a47aa6989ac7d4fab5d20c97ee27970af79f4590824a8268dbf1b27dd")
        assertThat(Vectors.sha256(Vectors.packUInt64LE(mixed2)))
            .isEqualTo("5b7a4c6be8ac3f3c33331e866588d75f17b62dcdf9b1bfab22846801cd262dca")

        val tail1 = builder63c278Tail1U32Words(mixed1)
        val tail2 = builder63c278Tail2U32Words(mixed2)
        assertThat(Vectors.sha256(Vectors.packUInt32LE(tail1)))
            .isEqualTo("27e1f0bcd8f8555166c80cb3ee788ce5c03a1ee08dde0f2aa8e9c3282a72b472")
        assertThat(Vectors.sha256(Vectors.packUInt32LE(tail2)))
            .isEqualTo("9b840d5956cdaae86b1835984c6bdcfa4b44577c1cfeae66455c2c04739d1e4f")
        assertThat(tail1.copyOfRange(0, 4).toList())
            .isEqualTo(listOf(0xc21a61c6u, 0x74c4feafu, 0x58177aecu, 0x7a88bfb1u))
        assertThat(tail2.copyOfRange(0, 4).toList())
            .isEqualTo(listOf(0xc822edf3u, 0x3210de15u, 0x669f83ceu, 0x9d56a88eu))

        val accum = builder63c278AccumulatorStreams(arg2, tail2)
        assertThat(Vectors.sha256(Vectors.packUInt64LE(accum.sp440Cumulative)))
            .isEqualTo("b5ba8730b4f348f2bead511a543354ac3cba1388e04d737af3487124cbc79598")
        assertThat(Vectors.sha256(Vectors.packUInt64LE(accum.sp4f0Words)))
            .isEqualTo("2f26f4d41701f9596582f852831caeede87aa078ff624ab70e59dad3eb170b5d")
        assertThat(Vectors.sha256(Vectors.packUInt64LE(accum.sp5a0Words)))
            .isEqualTo("84932501eaef1bcf7c0b58a51b1bf8653c46ae9527a759b0469ff2653e43db03")
        assertThat(Vectors.sha256(Vectors.packUInt64LE(accum.sp390Cumulative)))
            .isEqualTo("22518465559d66c1e916c293fe4bc2e9380a5018f23d213d7bedfaef9f3f746b")

        val bridgeConv = builder63c278BridgeConvolutionVector(accum)
        val bridgeX0 = builder63c278BridgeX0Vector(arg0)
        val bridgeMix = builder63c278BridgeMixVector(bridgeConv, bridgeX0, scalar)
        val sp128 = builder63c278BridgeSP128Words(bridgeMix)
        assertThat(Vectors.sha256(Vectors.packUInt64LE(bridgeConv)))
            .isEqualTo("9d307ee87af9694b08e35935470c931390def241ff8cf03071b0c97e9288a7e6")
        assertThat(Vectors.sha256(Vectors.packUInt64LE(bridgeX0)))
            .isEqualTo("ba1a0930d3edc74b7b1cbc65257d0d5e0b84935ba8baf2a8d1bffb131618f370")
        assertThat(Vectors.sha256(Vectors.packUInt64LE(bridgeMix)))
            .isEqualTo("51c7581842c0ac902cde4145e6edec72344c6b174e1637b7804a54cf8c639e1c")
        assertThat(Vectors.sha256(Vectors.packUInt32LE(sp128)))
            .isEqualTo("eacc144f8735791a4d91c9c60617e7203b18f9338142c63fd549bc9e19957670")

        val prebranch = builder63c278PrebranchInitialStreams(arg0, tail1, sp128)
        val pre4f0 = builder63c278PrebranchSP4F0Words(arg0)
        val pre230 = builder63c278PrebranchSP230Words(pre4f0)
        val pre5a0 = builder63c278PrebranchSP5A0Words(pre230)
        assertThat(Vectors.sha256(Vectors.packUInt32LE(prebranch.sp390Static)))
            .isEqualTo("bb76f8765891dfcb76e25a5b078bf9a703142137ab005f646526f4654e693626")
        assertThat(Vectors.sha256(Vectors.packUInt32LE(prebranch.sp440Words)))
            .isEqualTo("7eb39fcc20f253b51776dd9ea7a697b92813d2db917ca91ed5c8378b1e7fd37c")
        assertThat(Vectors.sha256(Vectors.packUInt32LE(prebranch.sp6b0Words)))
            .isEqualTo("a1594bb70ed406c9e25a8cd067e68d56e23292b717ec02cd551456e5ed89f6a4")
        assertThat(Vectors.sha256(Vectors.packUInt32LE(prebranch.sp658Words)))
            .isEqualTo("8ac4e5d77a1070b2926a06ef3c8fbb01a0c4618c8237d5073f4198044ce1c02d")
        assertThat(Vectors.sha256(Vectors.packUInt32LE(pre4f0)))
            .isEqualTo("d9523b2165986722e70834869ba3bc017dfe1c0ae5bc42ca299bb7dabd2450c6")
        assertThat(Vectors.sha256(Vectors.packUInt32LE(pre230)))
            .isEqualTo("651bc9459d7ca269bd73de0e77a883f06126649d59e0ed448912291dd626832f")
        assertThat(Vectors.sha256(Vectors.packUInt32LE(pre5a0)))
            .isEqualTo("fe3dad8c38db4b9a4ebf05fa507d885080f4119e6751a0c30a92ef115b676746")
    }

    @Test
    fun `the whole schedule builder matches the two captured vectors`() {
        val arg0 = pre63c278Arg0Source
        val arg1First = hexBytes(
            "870f4045410102fa6ae48ed935d4528112946ec5085053dcda8e537f1f02ea84" +
                "ddb295ec23ff6a8c58b97fed9a2736abd68482c3517b7ad27fbe711c7fb9f" +
                "32a00c1356f38b3025e143d56dd9017a173d68482c3517b7ad2"
        )
        val arg2First = hexBytes(
            "d9f6a9980cacf13569f2843968ae9cdc112262a50b5bdb6a435931a1d02896c3" +
                "ab70c95476b7ea17fb1fadf32aeabc0881ab695ce36c62eca5621fafcf684" +
                "616c5198a7948a03ad3e82ae3783b32b01681ab695ce36c62ec"
        )
        val arg1Second = hexBytes(
            "2576fe36ecd8e7be514212a7129bcb32c361f0d230d0612528124ca25fd446a2" +
                "5979de6adb6a70c2b534e301985718a0d68482c3517b7ad27fbe711c7fb9f" +
                "32a00c1356f38b3025e143d56dd9017a173d68482c3517b7ad2"
        )
        val arg2Second = hexBytes(
            "37b2af34160b02ddf8e45aef8f22c626ed2984e27b754dc75c89f9a58cb0b0a8" +
                "eea62a650526362ea30760fa9319f39781ab695ce36c62eca5621fafcf684" +
                "616c5198a7948a03ad3e82ae3783b32b01681ab695ce36c62ec"
        )

        val firstSchedule = builder63c278ScheduleWords(arg0, arg1First, arg2First, pre63c278Scalar)
        assertThat(firstSchedule.toList()).isEqualTo(
            listOf(
                0x8c15c5dau, 0x34dd429du, 0x955af9feu, 0x6897e537u, 0x1bad4a31u,
                0xb3206998u, 0x3bda123du, 0x3fdb46c5u, 0xd42db9fdu, 0x29dc0f3au,
                0x3b95a64cu, 0xcce6d138u, 0x70227a65u, 0x87ca2121u, 0xefb07a8fu,
                0xc4749659u, 0x1cd92603u, 0xe0ab3767u, 0x3b95a64cu, 0xcce6d138u,
            )
        )
        assertThat(Vectors.sha256(Vectors.packUInt32LE(firstSchedule)))
            .isEqualTo("bca47c5f0b63efce696822be0e0b00455d7f4d592cf55332429588fcba3e285b")

        val secondSchedule = builder63c278ScheduleWords(arg0, arg1Second, arg2Second, pre63c278Scalar)
        assertThat(secondSchedule.toList()).isEqualTo(
            listOf(
                0x04961c3du, 0x1f110752u, 0x271f9e47u, 0x551739bcu, 0x828a0f59u,
                0xd01fa5beu, 0x6703b5b7u, 0x22e03d75u, 0x9cbed758u, 0x7f4e06d1u,
                0x3b95a64cu, 0xcce6d138u, 0x70227a65u, 0x87ca2121u, 0xefb07a8fu,
                0xc4749659u, 0x1cd92603u, 0xe0ab3767u, 0x3b95a64cu, 0xcce6d138u,
            )
        )
        assertThat(Vectors.sha256(Vectors.packUInt32LE(secondSchedule)))
            .isEqualTo("8b6ad5e9244eb599dbfdaabee633b41bd6230643ed86ec7c90e9d3335c621a3c")

        val source = deriveFrom63c278ScheduleInputs(
            arg0, arg1First, arg2First, arg1Second, arg2Second, pre63c278Scalar, offset = 0, length = 16,
        )
        val defaultSource = deriveFromPre63c278ScheduleInputs(
            arg1First, arg2First, arg1Second, arg2Second, offset = 0, length = 16,
        )
        assertThat(defaultSource).isEqualTo(source)
        assertThat(Vectors.hex(source)).isEqualTo(
            "040402020404000202060205040102060705010600010704020506070300050007" +
                "070004010407010502000304070207010604030305070405040204060700000702"
        )
        assertThat(Vectors.hex(Libre3Phase5KeySchedule.deriveRawKey(source)))
            .isEqualTo("8df19b56ae4a0d4044a5c0d5fc86a34e")
    }
}
