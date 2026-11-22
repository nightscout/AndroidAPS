package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3Phase5KeySchedule
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The published vector of the whole first pairing source: eleven seeds in, the sixty six byte
 * source and the sixteen byte Phase 5 key out.
 *
 * Copied from LibreCRKit `Tests/LibreCRKitTests/FirstPairSourceSliceTests.swift` at pin `a86b92f`.
 *
 * This one runs all 118 rows of the caller, so it is the slowest test of the module and also the
 * one that proves the port end to end.
 */
class Libre3FirstPairSourceTest {

    private fun bytePattern(count: Int, multiplier: Int, addend: Int): ByteArray =
        ByteArray(count) { ((it * multiplier + addend) and 0xff).toByte() }

    private val seeds = Builder6388f0FirstPairStreamSeeds(
        nullScalarWindow = bytePattern(70, 29, 1),
        staticScalarWindow = bytePattern(70, 31, 2),
        nullEntropy11A = bytePattern(0x11a, 37, 3),
        nullAttempts = 2,
        row0Out4 = bytePattern(88, 3, 1),
        row0Out3 = bytePattern(88, 5, 2),
        row0Out2 = bytePattern(88, 7, 3),
        row0Out1 = bytePattern(88, 17, 11),
        row0Out0 = bytePattern(88, 13, 5),
        row59Out1 = bytePattern(88, 23, 3),
        row59Out0 = bytePattern(88, 19, 7),
    )

    @Test
    fun `the two stream starts match the published vectors`() {
        val starts = builder6388f0FirstPair642f60StreamStarts(seeds)
        assertThat(Vectors.sha256(starts.row0.x0))
            .isEqualTo("ff807599b1ce0b18fbafc1f5ef3b1af310536e6a355f84b6fe6e5e7e70cb5d07")
        assertThat(Vectors.sha256(starts.row59.x0))
            .isEqualTo("a2105eb9e12ffa599c55ff1714223addc7fb0fcfa9fb7b6ed4bec1acbcf1c31c")
    }

    @Test
    fun `the whole first pairing source and its key match the published vectors`() {
        val source = deriveFrom6388f0FirstPairStreamSeeds(seeds)
        assertThat(Vectors.hex(source)).isEqualTo(
            "040400010402030107000002030007050505030706070204050000060303020307" +
                "020600070302040604000305030603020102020003010306050605060207060303"
        )
        assertThat(Vectors.hex(Libre3Phase5KeySchedule.deriveRawKey(source)))
            .isEqualTo("515ca99cb8c0deaf1208df352078064d")
    }
}
