package app.aaps.plugins.libre3.crypto.firstpair

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The published vectors of the caller context and of the whole `642f60` builder.
 *
 * Copied from LibreCRKit `Tests/LibreCRKitTests/FirstPairSourceSliceTests.swift` at pin `a86b92f`.
 */
class Libre3FirstPair642f60Test {

    private fun bytePattern(count: Int, multiplier: Int, addend: Int): ByteArray =
        ByteArray(count) { ((it * multiplier + addend) and 0xff).toByte() }

    @Test
    fun `the shared context and the two caller loop tables match the published vectors`() {
        val shared = builder6388f0SharedContextFromBundle()
        assertThat(shared.size).isEqualTo(0x520)
        assertThat(Vectors.sha256(shared))
            .isEqualTo("ef3f9995fade12005f0f4410bc1ffa23a03412851e31503042e149da302f2dac")
        assertThat(Vectors.hex(shared.copyOfRange(0, 32)))
            .isEqualTo("21ed7e8fc9862976ac50b4cb1e31a91f30fa05c70682ac26bc7db76219fd1d35")
        assertThat(Vectors.hex(shared.copyOfRange(shared.size - 32, shared.size)))
            .isEqualTo("4a411f4b3cf073011ded82b57188f50f977c1b57e3fbc2051c7577ffbb255fc9")

        val tables = builder6388f0CallerLoopTablesFromBundle()
        assertThat(tables.first.size).isEqualTo(59 * 0x58)
        assertThat(tables.second.size).isEqualTo(59 * 0x58)
        assertThat(Vectors.sha256(tables.first))
            .isEqualTo("08e40f696924cbde7e31db9c9102d071f1d17a0a60a17f58768b01f5ec067d35")
        assertThat(Vectors.hex(tables.first.copyOfRange(0, 32)))
            .isEqualTo("db7c3afca9d52301c0064bb894889a5e8c592cf871412afd4a411f5dad1b1a64")
        assertThat(Vectors.hex(tables.first.copyOfRange(tables.first.size - 32, tables.first.size)))
            .isEqualTo("4a411f4b3cf073011ded82b57188f50f977c1b57e3fbc2051c7577ffbb255fc9")
        assertThat(Vectors.sha256(tables.second))
            .isEqualTo("6471d5ae1bc99ec976683bc2e568af44b58f900cf0242439b9626d69cb54ec65")
        assertThat(Vectors.hex(tables.second.copyOfRange(0, 32)))
            .isEqualTo("bfcf00c8b7ecf353653481454ad35d2054aae79357422e128e2024529ca00ce0")
        assertThat(Vectors.hex(tables.second.copyOfRange(tables.second.size - 32, tables.second.size)))
            .isEqualTo("fccee0a880f009f9e121349c190bb74368363d144a33c2dd9112d90aeb6e5f5d")

        val context = builder6388f0CallerContextFromLoopTables(tables)
        assertThat(context.size).isEqualTo(0x2d58)
        assertThat(Vectors.sha256(context))
            .isEqualTo("f5059c7c440707b8bdc08c309540e629e109e78941406442a7d189f5c23fbe5f")
        assertThat(context.copyOfRange(0x4c8, 0x4c8 + tables.first.size)).isEqualTo(tables.first)
        assertThat(context.copyOfRange(0x1910, 0x1910 + tables.second.size)).isEqualTo(tables.second)
        assertThat(builder6388f0CallerContextFromBundle()).isEqualTo(context)
    }

    @Test
    fun `the whole 642f60 builder matches the published vector`() {
        val x1 = bytePattern(88, 13, 9)
        val x0 = bytePattern(88, 15, 2)
        val x2 = bytePattern(88, 21, 7)

        val result = builder642f60OutputsFromBundledContext(x0, x1, x2)
        assertThat(result.out0.size).isEqualTo(88)
        assertThat(result.out1.size).isEqualTo(88)
        assertThat(result.out2.size).isEqualTo(88)
        assertThat(Vectors.sha256(result.out0))
            .isEqualTo("e4e4bc44d23db2b617f3d9a3f84a9dc1a6767d4d242a4c52b8427c587148a813")
        assertThat(Vectors.sha256(result.out1))
            .isEqualTo("f6e027253992cc3f10bc117332271b9c97a5e6570be183b0808309bcc759bfd2")
        assertThat(Vectors.sha256(result.out2))
            .isEqualTo("9c0ec2ac6f581933c3e457e0c4267507a575e1280caf5cbe09b962f265307e92")
        assertThat(Vectors.sha256(result.out0 + result.out1 + result.out2))
            .isEqualTo("7b97c74090a4e4e2c720abf39d86a1343ba5e1961f206e227b3e06a531bc51ff")

        val explicit = builder642f60Outputs(x0, x1, x2, builder6388f0SharedContextFromBundle())
        assertThat(explicit.out0).isEqualTo(result.out0)
        assertThat(explicit.out1).isEqualTo(result.out1)
        assertThat(explicit.out2).isEqualTo(result.out2)
    }
}
