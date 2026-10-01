package app.aaps.pump.eopatch.core.ble

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BytesConverterTest {

    @Test
    fun `toInt from byte array should combine 4 bytes big-endian`() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        assertThat(BytesConverter.toInt(bytes)).isEqualTo(0x01020304)
    }

    @Test
    fun `toInt from byte array should handle negative bytes`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        assertThat(BytesConverter.toInt(bytes)).isEqualTo(-1)
    }

    @Test
    fun `toInt from byte array should return -1 for short array`() {
        assertThat(BytesConverter.toInt(byteArrayOf(0x01, 0x02))).isEqualTo(-1)
        assertThat(BytesConverter.toInt(null)).isEqualTo(-1)
    }

    @Test
    fun `toInt with offset should read from correct position`() {
        val bytes = byteArrayOf(0x00, 0x00, 0x01, 0x02, 0x03, 0x04)
        assertThat(BytesConverter.toInt(bytes, 2)).isEqualTo(0x01020304)
    }

    @Test
    fun `toInt with offset should return -1 for insufficient length`() {
        val bytes = byteArrayOf(0x01, 0x02)
        assertThat(BytesConverter.toInt(bytes, 1)).isEqualTo(-1)
    }

    @Test
    fun `toUInt with offset should treat first byte as unsigned`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x01)
        assertThat(BytesConverter.toUInt(bytes, 0)).isEqualTo(0xFF000001.toInt())
    }

    @Test
    fun `toInt from two bytes should combine signed`() {
        assertThat(BytesConverter.toInt(0x01.toByte(), 0x02.toByte())).isEqualTo(0x0102)
        assertThat(BytesConverter.toInt(0xFF.toByte(), 0x00.toByte())).isEqualTo(-256) // signed
    }

    @Test
    fun `toUInt from two bytes should combine unsigned`() {
        assertThat(BytesConverter.toUInt(0x01.toByte(), 0x02.toByte())).isEqualTo(0x0102)
        assertThat(BytesConverter.toUInt(0xFF.toByte(), 0x00.toByte())).isEqualTo(0xFF00)
    }

    @Test
    fun `toInt from single byte should return signed value`() {
        assertThat(BytesConverter.toInt(0x7F.toByte())).isEqualTo(127)
        assertThat(BytesConverter.toInt(0x80.toByte())).isEqualTo(-128)
    }

    @Test
    fun `toUInt from single byte should return unsigned value`() {
        assertThat(BytesConverter.toUInt(0xFF.toByte())).isEqualTo(255)
        assertThat(BytesConverter.toUInt(0x00.toByte())).isEqualTo(0)
        assertThat(BytesConverter.toUInt(0x80.toByte())).isEqualTo(128)
    }
}
