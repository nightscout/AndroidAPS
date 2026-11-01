package app.aaps.plugins.libre3.crypto.firstpair

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The published vectors of the three `64cd40` call states of the caller.
 *
 * Copied from LibreCRKit `Tests/LibreCRKitTests/FirstPairSourceSliceTests.swift` at pin `a86b92f`.
 * Rows 0, 17 and 58 are checked, so that the loop slot arithmetic is covered at both ends and in
 * the middle.
 */
class Libre3FirstPairCallerRowsTest {

    /** One published row: the index and the eight values it pins. */
    private class ExpectedRow(
        val index: Int,
        val workspaceHash: String,
        val workspaceHead: String,
        val workspaceTail: String,
        val stackHash: String,
        val stackHead: String,
        val stackTail: String,
        val outputHash: String,
        val outputHead: String,
        val outputTail: String,
    )

    private fun bytePattern(count: Int, multiplier: Int, addend: Int): ByteArray =
        ByteArray(count) { ((it * multiplier + addend) and 0xff).toByte() }

    private val context = builder6388f0CallerContextFromBundle()
    private val result = builder6473d0Outputs(
        bytePattern(88, 15, 2), bytePattern(88, 13, 9), bytePattern(88, 21, 7),
        context, bytePattern(88, 13, 5), bytePattern(88, 17, 11),
    )
    private val preimages = Builder6473d0OutputPreimages(result.out4, result.out3, result.out2, result.out1, result.out0)
    private val stack20 = builder6473d0MinimalStack20FromPreimages(preimages)
    private val postVectors = builder6473d0PostVectors(result)

    private fun head8(bytes: ByteArray) = Vectors.hex(bytes.copyOfRange(0, 8))
    private fun tail8(bytes: ByteArray) = Vectors.hex(bytes.copyOfRange(bytes.size - 8, bytes.size))

    private fun checkCall(call: Builder6388f0Caller64Call, row: ExpectedRow) {
        assertThat(call.scalar).isEqualTo(0x68404ef676a9b7d3uL)
        assertThat(call.arg0.size).isEqualTo(88)
        assertThat(Vectors.sha256(call.arg0))
            .isEqualTo("496aa2bee379c421196b33f0e1ea8ff833a919340d09d4dd9c360e8322c9d362")

        assertThat(call.x2Workspace.size).isEqualTo(352)
        assertThat(Vectors.sha256(call.x2Workspace)).isEqualTo(row.workspaceHash)
        assertThat(head8(call.x2Workspace)).isEqualTo(row.workspaceHead)
        assertThat(tail8(call.x2Workspace)).isEqualTo(row.workspaceTail)

        assertThat(call.stackWindow.size).isEqualTo(0xb50)
        assertThat(Vectors.sha256(call.stackWindow)).isEqualTo(row.stackHash)
        assertThat(head8(call.stackWindow)).isEqualTo(row.stackHead)
        assertThat(tail8(call.stackWindow)).isEqualTo(row.stackTail)

        assertThat(call.output.size).isEqualTo(88)
        assertThat(Vectors.sha256(call.output)).isEqualTo(row.outputHash)
        assertThat(head8(call.output)).isEqualTo(row.outputHead)
        assertThat(tail8(call.output)).isEqualTo(row.outputTail)
    }

    @Test
    fun `the first 64cd40 call state matches the published vectors`() {
        val rows = listOf(
            ExpectedRow(
                0,
                "9b74a2232218a70449b125c5b25a1be077125ce8b113783a46454ed02d816021",
                "9b8cd70e7c008f70", "adfaa15b83fb7c0a",
                "e9a7694cd9ab4b0bae1d8434ff29d370977c0828549628487f9a1395e4469f8e",
                "22604f4f42c846ff", "0000000000000000",
                "a87ab02c52a3f0e4d24c373618e06f5ed46879e1cfd78bad86063abb421dbbc4",
                "a29c55c2cd095992", "21f8df859b8bfaae",
            ),
            ExpectedRow(
                17,
                "80e40a3e574da19ee4e2698456b25369913b97fa6b5a8c5692dc57e96cef45cb",
                "15c9a7f3b301eb2e", "adfaa15b83fb7c0a",
                "4d9957cb000e2390f1f3351a15f22f85d4dde632936f68fc4e91ecafbdf3476e",
                "22604f4f42c846ff", "0000000000000000",
                "8970960806c647297af6a2c2e803fc62cb1a188b546b1234c35b507b3854788c",
                "f17f9f4f39b1673b", "21f8df859b8bfaae",
            ),
            ExpectedRow(
                58,
                "433d81b9f713d296a31dbd232f7456b5545ca0f89640d5c98f257737207dad79",
                "c18fefc075d749c5", "adfaa15b83fb7c0a",
                "ec7eb53f85b5c2ae678ede450ae0d2ab16d1c8b6cdd35de4c6d8db90aefc3a70",
                "22604f4f42c846ff", "0000000000000000",
                "fe323876b4ec44df84474ba3d699aa824c9417b8363ab776f23c0c0def8e6359",
                "dfd4e82cac137e2b", "21f8df859b8bfaae",
            ),
        )
        for (row in rows) {
            val call = builder6388f0Call64Call(
                builder6388f0First64cd40CallState(context, stack20, postVectors, row.index)
            )
            checkCall(call, row)
            assertThat(Vectors.sha256(call.x3Preimage))
                .isEqualTo("10eef285deef7a4b7c82b22aa53589b7833df29de3814649c772bbd5c832f365")
        }
    }

    @Test
    fun `the second 64cd40 call state matches the published vectors`() {
        val rows = listOf(
            ExpectedRow(
                0,
                "fdf23daa0954614bc792d8a4dfa7bdb95f110bb155ee30e63a3079c2c636d4db",
                "88d330fdf438d13f", "adfaa15b83fb7c0a",
                "3a3bd479b489dae29e94ac6fe23fb61edf0444b8489d330b0f86a30530cdc2de",
                "22604f4f42c846ff", "0000000000000000",
                "a88449fc45dda85642e0fb0945bdb49cc80f61fb70f2a2973239c4dc66234551",
                "543d01d17c9b3d9d", "21f8df859b8bfaae",
            ),
            ExpectedRow(
                17,
                "674d446fa8d4488a5733b4abdea474ae081c4bd0066feff1870a3207d06e935e",
                "a95d0d81704cb873", "adfaa15b83fb7c0a",
                "889cd7b12cfc4a4dd772e404acdedc3bf64ea0b7f75e38c179381bfa60e47793",
                "22604f4f42c846ff", "0000000000000000",
                "25556b8bcbaede26866b77bd708a51231b62ba928254d3ebc71e8034c580337f",
                "47d5daecac25c281", "21f8df859b8bfaae",
            ),
            ExpectedRow(
                58,
                "3bccb00b36a989df5282f89f0d9cad1874be43fc7ade4b7f14c64d16f80696e2",
                "77572d1aa71cdc21", "adfaa15b83fb7c0a",
                "fce4abbb0537b096ee71bcd98efb5f056fead875cf1fd53762d5608671e69580",
                "22604f4f42c846ff", "0000000000000000",
                "ccdf21b6f656f68f98024e8c8e049460de41630149212b23b38aeec2e6a30235",
                "02802ed57a5241ee", "21f8df859b8bfaae",
            ),
        )
        for (row in rows) {
            val firstCall = builder6388f0Call64Call(
                builder6388f0First64cd40CallState(context, stack20, postVectors, row.index)
            )
            val call = builder6388f0Call64Call(
                builder6388f0Second64cd40CallState(context, stack20, postVectors, firstCall.output, row.index)
            )
            checkCall(call, row)
            assertThat(call.x3Preimage).isEqualTo(firstCall.output)
        }
    }

    @Test
    fun `the third 64cd40 call state matches the published vectors`() {
        val rows = listOf(
            ExpectedRow(
                0,
                "27fe07cba0b4e7f62d5da4f07f91f5d1887d8f2038431d7f5d14e6d6c38683eb",
                "cc1a18d569c77ddd", "adfaa15b83fb7c0a",
                "a9194cbdf9a9f7e76911b05d2641ecddcb6a76e1721f0f1c37cac228a7b7995e",
                "22604f4f42c846ff", "0000000000000000",
                "a1c8d6d83a91ce22e8025eb97c641fa5396cdb629e7105d3b646767e1c2d6a29",
                "13e228e615652c1f", "21f8df859b8bfaae",
            ),
            ExpectedRow(
                17,
                "611f5018d6ecd757ec6298405c6da354f4c757fa882244c1dc8e05cf0adadf75",
                "11af0e262ddece94", "adfaa15b83fb7c0a",
                "c3d48c9a66812e39a6cffe630fdabaea3a4f449953f3a14fdb30b9222bc34524",
                "22604f4f42c846ff", "0000000000000000",
                "4e61a8e846d745236315e1d28fc341466130e735de858695281b7bd40b10f2e2",
                "49821358928f5609", "21f8df859b8bfaae",
            ),
            ExpectedRow(
                58,
                "c2ca68df25da9dab6dc07a22501cb037543119fee471ffcf4a139e28240a498b",
                "d7661ccde60dfbe2", "adfaa15b83fb7c0a",
                "1a99dbd410c58bc5b0fea71ec0a21804bf5151de2b77ee22ca393cd8355531c2",
                "22604f4f42c846ff", "0000000000000000",
                "6348e4b69ed966be0a14615776c7ad56f938543f74f0fea19539ebdd7b3a1449",
                "0bfcade05c9c24dd", "21f8df859b8bfaae",
            ),
        )
        for (row in rows) {
            val firstCall = builder6388f0Call64Call(
                builder6388f0First64cd40CallState(context, stack20, postVectors, row.index)
            )
            val secondCall = builder6388f0Call64Call(
                builder6388f0Second64cd40CallState(context, stack20, postVectors, firstCall.output, row.index)
            )
            val call = builder6388f0Call64Call(
                builder6388f0Third64cd40CallState(context, stack20, postVectors, secondCall.output, row.index)
            )
            checkCall(call, row)
            assertThat(call.x3Preimage).isEqualTo(secondCall.output)
        }
    }
}
