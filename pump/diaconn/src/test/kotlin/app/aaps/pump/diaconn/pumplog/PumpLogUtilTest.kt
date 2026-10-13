package app.aaps.pump.diaconn.pumplog

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PumpLogUtilTest {

    @Test
    fun getTypeShouldExtractUpperTwoBits() {
        // Type is stored in upper 2 bits (bits 6-7)
        // Format: TTXXXXXX where T=type, X=kind

        // Type 0 (binary: 00xxxxxx)
        assertThat(PumpLogUtil.getType(0b00111111.toByte())).isEqualTo(0.toByte())

        // Type 1 (binary: 01xxxxxx)
        assertThat(PumpLogUtil.getType(0b01000000.toByte())).isEqualTo(1.toByte())
        assertThat(PumpLogUtil.getType(0b01111111.toByte())).isEqualTo(1.toByte())

        // Type 2 (binary: 10xxxxxx)
        assertThat(PumpLogUtil.getType(0b10000000.toByte())).isEqualTo(2.toByte())
        assertThat(PumpLogUtil.getType(0b10111111.toByte())).isEqualTo(2.toByte())

        // Type 3 (binary: 11xxxxxx)
        assertThat(PumpLogUtil.getType(0b11000000.toByte())).isEqualTo(3.toByte())
        assertThat(PumpLogUtil.getType(0b11111111.toByte())).isEqualTo(3.toByte())
    }

    @Test
    fun getKindShouldExtractLowerSixBits() {
        // Kind is stored in lower 6 bits (bits 0-5)
        // Format: XXKKKKKK where X=type, K=kind

        // Kind 0 (binary: xx000000)
        assertThat(PumpLogUtil.getKind(0b00000000.toByte())).isEqualTo(0.toByte())
        assertThat(PumpLogUtil.getKind(0b11000000.toByte())).isEqualTo(0.toByte())

        // Kind 1 (binary: xx000001)
        assertThat(PumpLogUtil.getKind(0b00000001.toByte())).isEqualTo(1.toByte())
        assertThat(PumpLogUtil.getKind(0b11000001.toByte())).isEqualTo(1.toByte())

        // Kind 63 (binary: xx111111) - maximum 6-bit value
        assertThat(PumpLogUtil.getKind(0b00111111.toByte())).isEqualTo(63.toByte())
        assertThat(PumpLogUtil.getKind(0b11111111.toByte())).isEqualTo(63.toByte())

        // Example: Type 2, Kind 8 (binary: 10001000)
        val combined = 0b10001000.toByte()
        assertThat(PumpLogUtil.getType(combined)).isEqualTo(2.toByte())
        assertThat(PumpLogUtil.getKind(combined)).isEqualTo(8.toByte())
    }

    @Test
    fun hexStringToByteArrayShouldConvertValidHexString() {
        // Simple single byte
        assertThat(PumpLogUtil.hexStringToByteArray("00")).isEqualTo(byteArrayOf(0x00))
        assertThat(PumpLogUtil.hexStringToByteArray("FF")).isEqualTo(byteArrayOf(0xFF.toByte()))

        // Multiple bytes
        assertThat(PumpLogUtil.hexStringToByteArray("0102")).isEqualTo(byteArrayOf(0x01, 0x02))
        assertThat(PumpLogUtil.hexStringToByteArray("AABBCC")).isEqualTo(
            byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        )

        // Mixed case
        assertThat(PumpLogUtil.hexStringToByteArray("aAbBcC")).isEqualTo(
            byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        )

        // Real packet example (4 bytes timestamp + 1 byte type/kind)
        assertThat(PumpLogUtil.hexStringToByteArray("23C1AB6408")).isEqualTo(
            byteArrayOf(0x23, 0xC1.toByte(), 0xAB.toByte(), 0x64, 0x08)
        )
    }

    @Test
    fun hexStringToByteArrayShouldHandleEmptyString() {
        assertThat(PumpLogUtil.hexStringToByteArray("")).isEqualTo(byteArrayOf())
    }

    @Test
    fun isPumpVersionGeShouldCompareMajorVersions() {
        // Major version greater
        assertThat(PumpLogUtil.isPumpVersionGe("3.0", 2, 63)).isTrue()
        assertThat(PumpLogUtil.isPumpVersionGe("3.50", 2, 63)).isTrue()

        // Major version less
        assertThat(PumpLogUtil.isPumpVersionGe("1.99", 2, 0)).isFalse()
        assertThat(PumpLogUtil.isPumpVersionGe("1.0", 2, 0)).isFalse()
    }

    @Test
    fun isPumpVersionGeShouldCompareMinorVersionsWhenMajorEqual() {
        // Minor version greater
        assertThat(PumpLogUtil.isPumpVersionGe("2.64", 2, 63)).isTrue()
        assertThat(PumpLogUtil.isPumpVersionGe("2.100", 2, 63)).isTrue()

        // Minor version equal
        assertThat(PumpLogUtil.isPumpVersionGe("2.63", 2, 63)).isTrue()

        // Minor version less
        assertThat(PumpLogUtil.isPumpVersionGe("2.62", 2, 63)).isFalse()
        assertThat(PumpLogUtil.isPumpVersionGe("2.0", 2, 63)).isFalse()
    }

    @Test
    fun isPumpVersionGeShouldHandleVersionsWithNonDigits() {
        // Version strings might have extra characters
        assertThat(PumpLogUtil.isPumpVersionGe("v2.63", 2, 63)).isTrue()
        assertThat(PumpLogUtil.isPumpVersionGe("2.63.1", 2, 63)).isTrue()
        assertThat(PumpLogUtil.isPumpVersionGe("version 3.50", 2, 63)).isTrue()
    }

    @Test
    fun getTypeShouldHandleFromHexString() {
        // Test the overload that takes a hex string
        // Byte at position 4 contains type/kind: 0x08 = type 0, kind 8
        assertThat(PumpLogUtil.getType("23C1AB6408")).isEqualTo(0.toByte())

        // 0x48 = 0b01001000 = type 1, kind 8
        assertThat(PumpLogUtil.getType("23C1AB6448")).isEqualTo(1.toByte())

        // 0x88 = 0b10001000 = type 2, kind 8
        assertThat(PumpLogUtil.getType("23C1AB6488")).isEqualTo(2.toByte())

        // 0xC8 = 0b11001000 = type 3, kind 8
        assertThat(PumpLogUtil.getType("23C1AB64C8")).isEqualTo(3.toByte())
    }

    @Test
    fun getKindShouldHandleFromHexString() {
        // Test the overload that takes a hex string
        // Byte at position 4 contains type/kind

        // 0x08 = 0b00001000 = type 0, kind 8
        assertThat(PumpLogUtil.getKind("23C1AB6408")).isEqualTo(8.toByte())

        // 0x0A = 0b00001010 = type 0, kind 10
        assertThat(PumpLogUtil.getKind("23C1AB640A")).isEqualTo(10.toByte())

        // 0x3F = 0b00111111 = type 0, kind 63 (max kind value)
        assertThat(PumpLogUtil.getKind("23C1AB643F")).isEqualTo(63.toByte())
    }

    @Test
    fun getDttmShouldConvertTimestampFromByteBuffer() {
        // Test timestamp conversion from ByteBuffer
        val buffer = java.nio.ByteBuffer.wrap(byteArrayOf(0x23, 0xC1.toByte(), 0xAB.toByte(), 0x64))
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)

        val result = PumpLogUtil.getDttm(buffer)

        // Should return a formatted date string in "yyyy-MM-dd HH:mm:ss" format
        assertThat(result).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")
    }

    @Test
    fun getDttmShouldConvertTimestampFromHexString() {
        // Test timestamp conversion from hex string
        // First 4 bytes are timestamp in little-endian format
        val result = PumpLogUtil.getDttm("23C1AB6408")

        // Should return a formatted date string in "yyyy-MM-dd HH:mm:ss" format
        assertThat(result).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")

        // The same timestamp should produce the same result
        val buffer = java.nio.ByteBuffer.wrap(byteArrayOf(0x23, 0xC1.toByte(), 0xAB.toByte(), 0x64))
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val bufferResult = PumpLogUtil.getDttm(buffer)

        assertThat(result).isEqualTo(bufferResult)
    }
}
