package app.aaps.plugins.libre3.crypto.firstpair

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The published vectors of the `64c524` and `64cd40` work area engines.
 *
 * Copied from LibreCRKit `Tests/LibreCRKitTests/FirstPairSourceSliceTests.swift` at pin `a86b92f`.
 * The third engine, `64bd0c`, is proven by the `642f60` vectors that lean on it.
 */
class Libre3FirstPairWorkspacesTest {

    private fun bytePattern(count: Int, multiplier: Int, addend: Int): ByteArray =
        ByteArray(count) { ((it * multiplier + addend) and 0xff).toByte() }

    private val arg0 = bytePattern(88, 7, 3)
    private val scalar = 0x0123456789abcdefuL
    private val x2Workspace = bytePattern(352, 5, 11)

    @Test
    fun `the 64c524 engine matches the published vector`() {
        val arg0Words = builder64c524Arg0U64Words(arg0)
        assertThat(Vectors.sha256(Vectors.packUInt64LE(arg0Words)))
            .isEqualTo("b4c289307b76fd400a7ebb93cfd81f6df15931c9553af9a9c8bc1a9f047ca741")
        assertThat(arg0Words.copyOfRange(0, 4).toList()).isEqualTo(
            listOf(0x3ff86d27c281a51buL, 0x82c9235432f698dauL, 0xf846c40785c0883cuL, 0x9f193ff19acdd538uL)
        )

        val updated = builder64c524WorkspaceAfterUpdate(arg0Words, scalar, x2Workspace)
        assertThat(updated.size).isEqualTo(44 * 8)
        assertThat(Vectors.sha256(updated))
            .isEqualTo("0dce7bc80d9fd277b44333a61aa8e14a608c6205b63cee6b9ea498a4407533ee")
        assertThat(Vectors.hex(updated.copyOfRange(0, 32)))
            .isEqualTo("af4609c269497b9cc32a915a34c3bbd1c87d116319769c5fab21064d51fe471b")
        assertThat(Vectors.hex(updated.copyOfRange(updated.size - 32, updated.size)))
            .isEqualTo("88227680af94c55adff3deccb1f86d6c81b410c13274fcc1c3c8cdd2d7dce1e6")

        val output = builder64c524FinalU32Words(updated)
        assertThat(Vectors.sha256(Vectors.packUInt32LE(output)))
            .isEqualTo("59eebec8dcac326baa8e1be4844f38155835ea1734a2985fd12249e77de2d6b3")
        assertThat(output.copyOfRange(0, 4).toList())
            .isEqualTo(listOf(0x2c9a5e95u, 0x39ffcae7u, 0xdb27a8feu, 0x35947d74u))
        assertThat(output.copyOfRange(18, 22).toList())
            .isEqualTo(listOf(0x35c32db9u, 0x322abd54u, 0x38781571u, 0xeb184e5fu))
        assertThat(builder64c524OutputWords(arg0, scalar, x2Workspace).toList()).isEqualTo(output.toList())
    }

    @Test
    fun `the 64cd40 engine matches the published vector`() {
        val arg0Words = builder64cd40Arg0U64Words(arg0)
        assertThat(Vectors.sha256(Vectors.packUInt64LE(arg0Words)))
            .isEqualTo("a44f4e91a177dc3a5cb312ef4f9719cdc71ddecceaddc3ec4e2edf789628e5d1")
        assertThat(arg0Words.copyOfRange(0, 4).toList()).isEqualTo(
            listOf(0x10ddde967190d344uL, 0xebd463e50c8fe285uL, 0x1a0e6a0158814d23uL, 0x0e751bdbe34a0c27uL)
        )

        val updated = builder64cd40WorkspaceAfterUpdate(arg0Words, scalar, x2Workspace)
        assertThat(updated.size).isEqualTo(44 * 8)
        assertThat(Vectors.sha256(updated))
            .isEqualTo("4f72ca5ec1a21eaefb25bc5e1952d71e22482eff8776ab4d7d7b097dd898f698")
        assertThat(Vectors.hex(updated.copyOfRange(0, 32)))
            .isEqualTo("d7b094c172d1d6f663803ebc41632b3a20173805dd60fba63f6cdf91fc96abe6")
        assertThat(Vectors.hex(updated.copyOfRange(updated.size - 32, updated.size)))
            .isEqualTo("428e3ebc4f8e7959b5f33857d937a682a920452868aad593c3c8cdd2d7dce1e6")

        val output = builder64cd40FinalU32Words(updated)
        assertThat(Vectors.sha256(Vectors.packUInt32LE(output)))
            .isEqualTo("d9242621c538e422518e9e87083886228bb6f88827716cf209de9e42fb4a336d")
        assertThat(output.toList()).isEqualTo(
            listOf(
                0x00cec4dfu, 0x26e4cde3u, 0xcaeeb424u, 0xe561e5c9u, 0xe47fcf9fu,
                0x0946526du, 0xed187991u, 0xb16fcb0du, 0x3165ae59u, 0xedbc680cu,
                0x5d8e3672u, 0x6f6ecb46u, 0x73c1ecadu, 0xdb28c019u, 0x4d2396d0u,
                0xb9045673u, 0x6e108816u, 0x491e7a22u, 0x6ca69691u, 0xa935ba59u,
                0x67d4d8c8u, 0x511b912cu,
            )
        )
        assertThat(builder64cd40OutputWords(arg0, scalar, x2Workspace).toList()).isEqualTo(output.toList())
    }
}
