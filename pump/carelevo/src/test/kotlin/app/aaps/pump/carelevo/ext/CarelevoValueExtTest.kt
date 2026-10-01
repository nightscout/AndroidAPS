package app.aaps.pump.carelevo.ext

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure-logic tests for the wire byte helpers in `CarelevoValueExt.kt`
 * (`convertBytesToHex`, `convertHexToByteArray`, `checkSum`, `checkSumV2`).
 */
internal class CarelevoValueExtTest {

    // ---------- convertBytesToHex ----------

    @Test
    fun `convertBytesToHex formats a single low byte with leading zero`() {
        assertThat(byteArrayOf(0x00).convertBytesToHex()).isEqualTo("0x00")
        assertThat(byteArrayOf(0x0a).convertBytesToHex()).isEqualTo("0x0a")
    }

    @Test
    fun `convertBytesToHex formats two-digit hex without extra padding`() {
        // 0x1B = 27 -> "1b"
        assertThat(byteArrayOf(0x1B).convertBytesToHex()).isEqualTo("0x1b")
    }

    @Test
    fun `convertBytesToHex concatenates every byte with a 0x prefix each`() {
        assertThat(byteArrayOf(0x1B, 0x64).convertBytesToHex()).isEqualTo("0x1b0x64")
    }

    @Test
    fun `convertBytesToHex renders negative bytes as their unsigned two's complement`() {
        assertThat(byteArrayOf(0xFF.toByte()).convertBytesToHex()).isEqualTo("0xff")
        assertThat(byteArrayOf(0x80.toByte()).convertBytesToHex()).isEqualTo("0x80")
    }

    @Test
    fun `convertBytesToHex of an empty array is the empty string`() {
        assertThat(byteArrayOf().convertBytesToHex()).isEqualTo("")
    }

    // ---------- convertHexToByteArray ----------

    @Test
    fun `convertHexToByteArray parses a plain even-length hex string`() {
        assertThat("1B64".convertHexToByteArray().toList())
            .containsExactly(0x1B.toByte(), 0x64.toByte()).inOrder()
    }

    @Test
    fun `convertHexToByteArray strips every 0x prefix`() {
        assertThat("0x1B0x64".convertHexToByteArray().toList())
            .containsExactly(0x1B.toByte(), 0x64.toByte()).inOrder()
    }

    @Test
    fun `convertHexToByteArray strips whitespace`() {
        assertThat("0x1b 0x64".convertHexToByteArray().toList())
            .containsExactly(0x1B.toByte(), 0x64.toByte()).inOrder()
    }

    @Test
    fun `convertHexToByteArray is case-insensitive`() {
        assertThat("1b64".convertHexToByteArray().toList())
            .containsExactly(0x1B.toByte(), 0x64.toByte()).inOrder()
    }

    @Test
    fun `convertHexToByteArray parses a high byte to its signed value`() {
        // BigInteger("FF",16)=255 -> toByte() = -1
        assertThat("ff".convertHexToByteArray().toList())
            .containsExactly((-1).toByte()).inOrder()
    }

    @Test
    fun `convertHexToByteArray of empty string is empty`() {
        assertThat("".convertHexToByteArray()).isEmpty()
    }

    @Test
    fun `convertHexToByteArray of an odd-length string yields empty`() {
        // (length % 2 == 0) gate is false for 3 chars -> nothing decoded.
        assertThat("ABC".convertHexToByteArray()).isEmpty()
    }

    @Test
    fun `convertHexToByteArray of invalid hex swallows the exception and yields empty`() {
        // BigInteger("ZZ",16) throws; it is caught and the accumulator stays empty.
        assertThat("ZZ".convertHexToByteArray()).isEmpty()
    }

    @Test
    fun `convertHexToByteArray decodes bytes up to the first invalid pair`() {
        // "1B" decodes, then "ZZ" throws and aborts the loop, keeping what was collected.
        assertThat("1BZZ".convertHexToByteArray().toList())
            .containsExactly(0x1B.toByte()).inOrder()
    }

    @Test
    fun `bytes-to-hex-to-bytes round trips`() {
        val original = byteArrayOf(0x1B, 0x64, 0x00, 0xFF.toByte())
        val roundTripped = original.convertBytesToHex().convertHexToByteArray()
        assertThat(roundTripped.toList()).containsExactly(*original.toTypedArray()).inOrder()
    }

    // ---------- checkSum ----------

    @Test
    fun `checkSum returns true when the running xor matches the expected result`() {
        // 0 ^ 1 ^ 2 ^ 4 = 7
        assertThat(byteArrayOf(0x01, 0x02, 0x04).checkSum(key = 0, result = 7)).isTrue()
    }

    @Test
    fun `checkSum returns false on a mismatch`() {
        assertThat(byteArrayOf(0x01, 0x02, 0x04).checkSum(key = 0, result = 8)).isFalse()
    }

    @Test
    fun `checkSum folds in a non-zero key`() {
        // 0x10 ^ 0x01 = 0x11
        assertThat(byteArrayOf(0x01).checkSum(key = 0x10, result = 0x11)).isTrue()
    }

    @Test
    fun `checkSum of an empty array equals the key byte`() {
        assertThat(byteArrayOf().checkSum(key = 5, result = 5)).isTrue()
        assertThat(byteArrayOf().checkSum(key = 5, result = 6)).isFalse()
    }

    @Test
    fun `checkSum compares only the low byte of result`() {
        // 263 & 0xFF = 7, so the high bits of result are ignored.
        assertThat(byteArrayOf(0x01, 0x02, 0x04).checkSum(key = 0, result = 263)).isTrue()
    }

    // ---------- checkSumV2 ----------

    @Test
    fun `checkSumV2 returns the xor accumulation`() {
        assertThat(byteArrayOf(0x01, 0x02, 0x04).checkSumV2(key = 0)).isEqualTo(7.toByte())
    }

    @Test
    fun `checkSumV2 of an empty array equals the key byte`() {
        assertThat(byteArrayOf().checkSumV2(key = 5)).isEqualTo(5.toByte())
    }

    @Test
    fun `checkSumV2 folds in a non-zero key`() {
        assertThat(byteArrayOf(0x01).checkSumV2(key = 0x10)).isEqualTo(0x11.toByte())
    }

    @Test
    fun `checkSumV2 of a high byte with zero key is that byte`() {
        assertThat(byteArrayOf(0xFF.toByte()).checkSumV2(key = 0)).isEqualTo(0xFF.toByte())
    }

    @Test
    fun `checkSumV2 result satisfies checkSum for the same key`() {
        val data = byteArrayOf(0x1B, 0x64, 0x74)
        val computed = data.checkSumV2(key = 0)
        assertThat(data.checkSum(key = 0, result = computed.toInt())).isTrue()
    }
}
