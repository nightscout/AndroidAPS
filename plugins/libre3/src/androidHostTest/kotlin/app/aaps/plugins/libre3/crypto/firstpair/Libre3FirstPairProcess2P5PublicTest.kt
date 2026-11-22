package app.aaps.plugins.libre3.crypto.firstpair

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The two captured traces of the phone's own public point.
 *
 * Copied from LibreCRKit `Tests/LibreCRKitTests/FirstPairSourceSliceTests.swift` at pin `a86b92f`,
 * where they come from real Android traces of the sensor maker's own app. They are the strongest
 * check of this whole port: they join the entropy the phone drew to the exact sixty five bytes
 * that went out on the wire.
 */
class Libre3FirstPairProcess2P5PublicTest {

    @Test
    fun `the public point matches the first captured trace`() {
        val entropy = hexBytes(
            "8987c91f1595e8a060e4cba652368ae8797e9113cfd412bebd0ea1a03783ae59" +
                "ee70d2c947578803b06b275c96632d148b81658bb87a3eabb5755273c40c397" +
                "f7255f3c1d742df608383fbbfff5a9b9fbc11a1ab525382024c85687cf79c2" +
                "a391ca7cc309ff82fe098c2d86e49f8b26364153f0bcb8945c887f5a2a7b5" +
                "4d568daa373a86c85c283fbb6285f35dca2d30263c34ce182c1fc63e6022a" +
                "3c7e6eaebe3a473d3c754bb8f3982172431af66388948aaf5c709f6699b76" +
                "08dcd161811dda99c61b302f46684433e61ef2afa4dd9f8b0f2472f612019" +
                "7cdfc0b940ad5f93ac01fc7497fb355c753df9c65fc68721690c35a09550fb" +
                "3c326e38bcbe37ebb309a680c383967627f58a108e1e94ecd16c5d2bc2f57" +
                "6dabdc7b"
        )
        assertThat(Vectors.hex(builderProcess2P5PublicKey65FromEntropy(entropy))).isEqualTo(
            "04b60e0f455a1f2ebc3a1246d9311a66722f80fbc0cbdc23d18ae5e50693eed2" +
                "b1ea74d24eddcc8dd1957cf621a1f5514fcd7b40ec37f18f8c8060db6f8076b121"
        )
    }

    @Test
    fun `the public point matches the second captured trace`() {
        val entropy = hexBytes(
            "726d47655b9434b44cd08664665dfb86934638911b6ebcc26420fe124ab654fd" +
                "e722e77f43756603943a8ee8196c6d5f83fc9cfe637e309f6f4b3c8fd5f10" +
                "9596f60b9e4899422925b8a0368b143580541bcaac3b4017b82f38d00c14d" +
                "46fbe3197ccfa9af048f6b446973c664901b84d362e95086e235e58517883" +
                "f7b89aef742768adc355131885657b686bdb6bd82feb11591b63f3e9466f0" +
                "e21f20cc58757ac547f57a21ee59b4816779510bd7d911861a116c40332328" +
                "cd4ec68579831e76ede1a5c6776c9d114a2788e8aed94b8f50a051da8cd8b" +
                "dbdf7c77f53ce76ee259d5d568a7b71edd3564f80969a4550a920238d1739" +
                "b34eceeb275c29f8dfb94796005ff15989a177536119388ed70c8fb6fa721" +
                "09635da2741"
        )
        assertThat(Vectors.hex(builderProcess2P5PublicKey65FromEntropy(entropy))).isEqualTo(
            "049cb2d2658568e6685fea83f5051ff703baec07cbca3b10e58600d538b85795db" +
                "5cd35248bd30f1918627a6d4f2f91ce31d21057279fa790b895b15192d040a99"
        )
    }
}
