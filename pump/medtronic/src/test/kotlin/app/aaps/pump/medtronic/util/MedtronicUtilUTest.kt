package app.aaps.pump.medtronic.util

import app.aaps.pump.medtronic.MedtronicTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for MedtronicUtil companion/static methods
 */
class MedtronicUtilUTest : MedtronicTestBase() {

    @Test
    fun `test getIntervalFromMinutes with even hours`() {
        assertThat(MedtronicUtil.getIntervalFromMinutes(0)).isEqualTo(0)
        assertThat(MedtronicUtil.getIntervalFromMinutes(30)).isEqualTo(1)
        assertThat(MedtronicUtil.getIntervalFromMinutes(60)).isEqualTo(2)
        assertThat(MedtronicUtil.getIntervalFromMinutes(120)).isEqualTo(4)
        assertThat(MedtronicUtil.getIntervalFromMinutes(180)).isEqualTo(6)
    }

    @Test
    fun `test getIntervalFromMinutes with half hours`() {
        assertThat(MedtronicUtil.getIntervalFromMinutes(90)).isEqualTo(3)  // 1.5 hours
        assertThat(MedtronicUtil.getIntervalFromMinutes(150)).isEqualTo(5) // 2.5 hours
        assertThat(MedtronicUtil.getIntervalFromMinutes(210)).isEqualTo(7) // 3.5 hours
    }

    @Test
    fun `test getIntervalFromMinutes with odd minutes rounds down`() {
        assertThat(MedtronicUtil.getIntervalFromMinutes(45)).isEqualTo(1)  // 45 / 30 = 1.5 -> 1
        assertThat(MedtronicUtil.getIntervalFromMinutes(75)).isEqualTo(2)  // 75 / 30 = 2.5 -> 2
        assertThat(MedtronicUtil.getIntervalFromMinutes(100)).isEqualTo(3) // 100 / 30 = 3.33 -> 3
    }

    @Test
    fun `test makeUnsignedShort with positive values`() {
        // Test 0x1234 (hex) = 4660 (decimal)
        assertThat(MedtronicUtil.makeUnsignedShort(0x12, 0x34)).isEqualTo(0x1234)

        // Test 0x00FF = 255
        assertThat(MedtronicUtil.makeUnsignedShort(0x00, 0xFF)).isEqualTo(255)

        // Test 0xFF00 = 65280
        assertThat(MedtronicUtil.makeUnsignedShort(0xFF, 0x00)).isEqualTo(0xFF00)
    }

    @Test
    fun `test makeUnsignedShort with signed byte values`() {
        // Test handling of negative byte values (when cast to int)
        // 0x80 = -128 as signed byte, but should be treated as 128 unsigned
        assertThat(MedtronicUtil.makeUnsignedShort(0x80, 0x00)).isEqualTo(0x8000)

        // 0xFF = -1 as signed byte, but should be treated as 255 unsigned
        assertThat(MedtronicUtil.makeUnsignedShort(0xFF, 0xFF)).isEqualTo(0xFFFF)
    }

    @Test
    fun `test makeUnsignedShort with zero`() {
        assertThat(MedtronicUtil.makeUnsignedShort(0x00, 0x00)).isEqualTo(0)
    }

    @Test
    fun `test getByteArrayFromUnsignedShort with small value not fixed size`() {
        // Value 50 (0x32) fits in one byte, returnFixedSize=false
        val result = MedtronicUtil.getByteArrayFromUnsignedShort(50, false)

        assertThat(result).hasLength(1)
        assertThat(result[0]).isEqualTo(50.toByte())
    }

    @Test
    fun `test getByteArrayFromUnsignedShort with small value fixed size`() {
        // Value 50 (0x32) with returnFixedSize=true should be [0x00, 0x32]
        val result = MedtronicUtil.getByteArrayFromUnsignedShort(50, true)

        assertThat(result).hasLength(2)
        assertThat(result[0]).isEqualTo(0x00.toByte())
        assertThat(result[1]).isEqualTo(50.toByte())
    }

    @Test
    fun `test getByteArrayFromUnsignedShort with large value`() {
        // Value 300 (0x012C) requires 2 bytes
        val result = MedtronicUtil.getByteArrayFromUnsignedShort(300, false)

        assertThat(result).hasLength(2)
        assertThat(result[0]).isEqualTo(0x01.toByte())
        assertThat(result[1]).isEqualTo(0x2C.toByte())
    }

    @Test
    fun `test getByteArrayFromUnsignedShort with max value`() {
        // Value 65535 (0xFFFF)
        val result = MedtronicUtil.getByteArrayFromUnsignedShort(65535, true)

        assertThat(result).hasLength(2)
        assertThat(result[0]).isEqualTo(0xFF.toByte())
        assertThat(result[1]).isEqualTo(0xFF.toByte())
    }

    @Test
    fun `test getByteArrayFromUnsignedShort with zero`() {
        val resultNotFixed = MedtronicUtil.getByteArrayFromUnsignedShort(0, false)
        val resultFixed = MedtronicUtil.getByteArrayFromUnsignedShort(0, true)

        assertThat(resultNotFixed).hasLength(1)
        assertThat(resultNotFixed[0]).isEqualTo(0.toByte())

        assertThat(resultFixed).hasLength(2)
        assertThat(resultFixed[0]).isEqualTo(0.toByte())
        assertThat(resultFixed[1]).isEqualTo(0.toByte())
    }

    @Test
    fun `test createByteArray with varargs`() {
        val result = MedtronicUtil.createByteArray(0x01, 0x02, 0x03, 0x04)

        assertThat(result).hasLength(4)
        assertThat(result[0]).isEqualTo(0x01.toByte())
        assertThat(result[1]).isEqualTo(0x02.toByte())
        assertThat(result[2]).isEqualTo(0x03.toByte())
        assertThat(result[3]).isEqualTo(0x04.toByte())
    }

    @Test
    fun `test createByteArray with single byte`() {
        val result = MedtronicUtil.createByteArray(0x42)

        assertThat(result).hasLength(1)
        assertThat(result[0]).isEqualTo(0x42.toByte())
    }

    @Test
    fun `test createByteArray with list`() {
        val list = listOf<Byte>(0x01, 0x02, 0x03)
        val result = MedtronicUtil.createByteArray(list)

        assertThat(result).hasLength(3)
        assertThat(result[0]).isEqualTo(0x01.toByte())
        assertThat(result[1]).isEqualTo(0x02.toByte())
        assertThat(result[2]).isEqualTo(0x03.toByte())
    }

    @Test
    fun `test createByteArray with empty list`() {
        val list = listOf<Byte>()
        val result = MedtronicUtil.createByteArray(list)

        assertThat(result).hasLength(0)
    }

    @Test
    fun `test getBasalStrokes for small amount not fixed size`() {
        // 1.0 U * 40 strokes/U = 40 strokes = 0x28
        val result = MedtronicUtil.getBasalStrokes(1.0, false)

        assertThat(result).hasLength(1)
        assertThat(result[0]).isEqualTo(0x28.toByte())
    }

    @Test
    fun `test getBasalStrokes for small amount fixed size`() {
        // 1.0 U * 40 strokes/U = 40 strokes = 0x0028
        val result = MedtronicUtil.getBasalStrokes(1.0, true)

        assertThat(result).hasLength(2)
        assertThat(result[0]).isEqualTo(0x00.toByte())
        assertThat(result[1]).isEqualTo(0x28.toByte())
    }

    @Test
    fun `test getBasalStrokes for larger amount`() {
        // 10.5 U * 40 strokes/U / 4 (scrollRate) * 4 = 420 strokes = 0x01A4
        val result = MedtronicUtil.getBasalStrokes(10.5, true)

        assertThat(result).hasLength(2)
        // 420 = 0x01A4
        assertThat(MedtronicUtil.makeUnsignedShort(result[0].toInt(), result[1].toInt())).isEqualTo(420)
    }

    @Test
    fun `test getBasalStrokes for medium amount`() {
        // 5.0 U with 40 strokes/U, scrollRate=2 for amounts > 1
        // 5.0 * 40 / 2 * 2 = 200 strokes = 0x00C8
        val result = MedtronicUtil.getBasalStrokes(5.0, true)

        assertThat(result).hasLength(2)
        assertThat(MedtronicUtil.makeUnsignedShort(result[0].toInt(), result[1].toInt())).isEqualTo(200)
    }

    @Test
    fun `test getBasalStrokes for very small amount`() {
        // 0.025 U * 40 strokes/U = 1 stroke
        val result = MedtronicUtil.getBasalStrokes(0.025, true)

        assertThat(result).hasLength(2)
        assertThat(result[0]).isEqualTo(0x00.toByte())
        assertThat(result[1]).isEqualTo(0x01.toByte())
    }

    @Test
    fun `test isSame with identical values`() {
        assertThat(MedtronicUtil.isSame(1.0, 1.0)).isTrue()
        assertThat(MedtronicUtil.isSame(0.0, 0.0)).isTrue()
        assertThat(MedtronicUtil.isSame(5.25, 5.25)).isTrue()
    }

    @Test
    fun `test isSame with very close values within epsilon`() {
        // Difference of 0.0000001 should be considered same
        assertThat(MedtronicUtil.isSame(1.0, 1.0000001)).isTrue()
        assertThat(MedtronicUtil.isSame(5.123456, 5.1234561)).isTrue()
    }

    @Test
    fun `test isSame with different values`() {
        assertThat(MedtronicUtil.isSame(1.0, 2.0)).isFalse()
        assertThat(MedtronicUtil.isSame(0.0, 0.001)).isFalse()
        assertThat(MedtronicUtil.isSame(5.25, 5.26)).isFalse()
    }

    @Test
    fun `test isSame with null values`() {
        assertThat(MedtronicUtil.isSame(null, 1.0)).isFalse()
        assertThat(MedtronicUtil.isSame(1.0, null)).isFalse()
        assertThat(MedtronicUtil.isSame(null, null)).isFalse()
    }

    @Test
    fun `test isSame with negative values`() {
        assertThat(MedtronicUtil.isSame(-1.0, -1.0)).isTrue()
        assertThat(MedtronicUtil.isSame(-5.5, -5.5)).isTrue()
        assertThat(MedtronicUtil.isSame(-1.0, -2.0)).isFalse()
    }

    @Test
    fun `test isSame with mixed sign values`() {
        assertThat(MedtronicUtil.isSame(1.0, -1.0)).isFalse()
        assertThat(MedtronicUtil.isSame(-1.0, 1.0)).isFalse()
    }
}
