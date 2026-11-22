package app.aaps.plugins.libre3.crypto.firstpair

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The published vector of the whole ten round `6473d0` builder.
 *
 * Copied from LibreCRKit `Tests/LibreCRKitTests/FirstPairSourceSliceTests.swift` at pin `a86b92f`.
 */
class Libre3FirstPair6473d0Test {

    private fun bytePattern(count: Int, multiplier: Int, addend: Int): ByteArray =
        ByteArray(count) { ((it * multiplier + addend) and 0xff).toByte() }

    @Test
    fun `the whole 6473d0 builder matches the published vector`() {
        val in2 = bytePattern(88, 21, 7)
        val in0 = bytePattern(88, 15, 2)
        val in1 = bytePattern(88, 13, 9)
        val out0Seed = bytePattern(88, 13, 5)
        val out1Seed = bytePattern(88, 17, 11)

        val result = builder6473d0OutputsFromBundledContext(in0, in1, in2, out0Seed, out1Seed)

        val vectors = listOf(
            Triple("in0_after", result.in0After, "9692c8145a24dcadc1fd23963c583512c8aebf55dc7c68ad677cb8f53f2117ea"),
            Triple("in1_after", result.in1After, "a4a1bb98f66e3d53a51c810379507e4a1f856bf51be0d007e10d5b3afc90252b"),
            Triple("in2_after", result.in2After, "8eb586c217d306dbde11f9301ab67d009e8dba5414bcebe90944e8542082edee"),
            Triple("out0", result.out0, "76cebb860262dd83aa186fc63ea614b3af5633e56600dda4d4da79ba840366bd"),
            Triple("out1", result.out1, "c49ad60aa507e639c71430a12067b0eb5d75737460bd9997b020b5760197ceb8"),
            Triple("out2", result.out2, "c5e3ec0675df26d11bd8390e34135652ad6b530fb8e003151b44cf1dfce6e169"),
            Triple("out3", result.out3, "d1486d791a35e129933d31bab4e814a0cdcd3db8c3b4895950882cba18791c90"),
            Triple("out4", result.out4, "3d1a32df33f5ce078ed6cfa67972c041d5aff9606ff86c381b5d257fa4bb3517"),
        )
        var combined = ByteArray(0)
        for ((name, value, expected) in vectors) {
            assertThat(value.size).isEqualTo(88)
            assertThat(Vectors.sha256(value)).isEqualTo(expected)
            assertThat(name).isNotEmpty()
            combined += value
        }
        assertThat(Vectors.sha256(combined))
            .isEqualTo("62d20b19dfc648c822a404a8672031efe193c1da60496b82a337458c1c1d2a5c")

        val explicit = builder6473d0Outputs(
            in0, in1, in2, builder6388f0SharedContextFromBundle(), out0Seed, out1Seed,
        )
        assertThat(explicit.out4).isEqualTo(result.out4)
        assertThat(explicit.out2).isEqualTo(result.out2)
    }
}
