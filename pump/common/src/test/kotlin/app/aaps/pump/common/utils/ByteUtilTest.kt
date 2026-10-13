package app.aaps.pump.common.utils

import app.aaps.core.utils.pump.ByteUtil
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class ByteUtilTest {

    @Test fun asUINT8() {
        assertThat(ByteUtil.asUINT8(-1)).isEqualTo(255)
    }

    @Test
    fun getBytesFromInt16() {
        assertThat(ByteUtil.getBytesFromInt16(0x0102)[0]).isEqualTo(1)
        assertThat(ByteUtil.getBytesFromInt16(0x0102)[1]).isEqualTo(2)
        assertThat(ByteUtil.getBytesFromInt16(0x0102)).hasLength(2)
    }

    @Test
    fun getBytesFromInt() {
        assertThat(ByteUtil.getBytesFromInt(0x01020304)[0]).isEqualTo(1)
        assertThat(ByteUtil.getBytesFromInt(0x01020304)[1]).isEqualTo(2)
        assertThat(ByteUtil.getBytesFromInt(0x01020304)[2]).isEqualTo(3)
        assertThat(ByteUtil.getBytesFromInt(0x01020304)[3]).isEqualTo(4)
        assertThat(ByteUtil.getBytesFromInt(0x0102)).hasLength(4)
    }

    @Test
    fun concat() {
        assertThat(ByteUtil.concat(byteArrayOf(1), null)).hasLength(1)
        assertThat(ByteUtil.concat(byteArrayOf(1), byteArrayOf(2))).hasLength(2)
        assertThat(ByteUtil.concat(byteArrayOf(1), byteArrayOf(2))[1]).isEqualTo(2)

        assertThat(ByteUtil.concat(byteArrayOf(1), 2.toByte())).hasLength(2)
        assertThat(ByteUtil.concat(byteArrayOf(1), 2.toByte())[1]).isEqualTo(2)
    }

    @Test
    fun substring() {
        assertThat(ByteUtil.substring(byteArrayOf(0x01, 0x02, 0x03), 1, 2)).hasLength(2)
        assertThat(ByteUtil.substring(byteArrayOf(0x01, 0x02, 0x03), 1, 2)[1]).isEqualTo(3)

        assertThat(ByteUtil.substring(arrayListOf(0x01.toByte(), 0x02.toByte(), 0x03.toByte()), 1, 2)).hasLength(2)
        assertThat(ByteUtil.substring(arrayListOf(0x01.toByte(), 0x02.toByte(), 0x03.toByte()), 1, 2)[1]).isEqualTo(3)

        assertThat(ByteUtil.substring(byteArrayOf(0x01, 0x02, 0x03), 1)).hasLength(2)
        assertThat(ByteUtil.substring(byteArrayOf(0x01, 0x02, 0x03), 1)[1]).isEqualTo(3)
    }

    @Test
    fun shortHexString() {
        assertThat(ByteUtil.shortHexString(null as ByteArray?)).isEqualTo("")
        assertThat(ByteUtil.shortHexString(byteArrayOf())).isEqualTo("")
        assertThat(ByteUtil.shortHexString(byteArrayOf(0x01, 0x02, 0x03))).isEqualTo("01 02 03")
        assertThat(ByteUtil.shortHexString(arrayListOf(0x01.toByte(), 0x02.toByte(), 0x03.toByte()))).isEqualTo("01 02 03")

        assertThat(ByteUtil.shortHexString(0x01.toByte())).isEqualTo("01")
    }

    @Test
    fun shortHexStringWithoutSpaces() {
        assertThat(ByteUtil.shortHexStringWithoutSpaces(null as ByteArray?)).isEqualTo("")
        assertThat(ByteUtil.shortHexStringWithoutSpaces(byteArrayOf())).isEqualTo("")
        assertThat(ByteUtil.shortHexStringWithoutSpaces(byteArrayOf(0x01, 0x02, 0x03))).isEqualTo("010203")
    }

    @Test
    fun fromHexString() {
        assertThat(ByteUtil.fromHexString("1")).isEqualTo(null)
        assertThat(ByteUtil.fromHexString("GA")).isEqualTo(null)
        assertThat(ByteUtil.fromHexString("AG")).isEqualTo(null)
        assertThat(ByteUtil.fromHexString("0102")?.get(0)).isEqualTo(1)
        assertThat(ByteUtil.fromHexString("0102")?.get(1)).isEqualTo(2)
    }

    @Test
    fun getListFromByteArray() {
        assertThat(ByteUtil.getListFromByteArray(byteArrayOf(0x01, 0x02, 0x03))).hasSize(3)
    }

    @Test
    fun compare() {
        assertThat(ByteUtil.compare(byteArrayOf(0x01, 0x02), byteArrayOf(0x01))).isEqualTo(1)
        assertThat(ByteUtil.compare(byteArrayOf(0x01), byteArrayOf(0x01, 0x02))).isEqualTo(-1)
        assertThat(ByteUtil.compare(byteArrayOf(0x01, 0x02), byteArrayOf(0x01, 0x02))).isEqualTo(0)
        assertThat(ByteUtil.compare(byteArrayOf(0x01, 0x02), byteArrayOf(0x02, 0x01))).isEqualTo(-1)
        assertThat(ByteUtil.compare(byteArrayOf(0x02, 0x01), byteArrayOf(0x01, 0x02))).isEqualTo(1)
    }

    @Suppress("RemoveRedundantCallsOfConversionMethods")
    @Test
    fun toInt() {
        assertThat(ByteUtil.toInt(0x01.toInt(), 0x02.toInt(), 0x03.toInt(), 0x04.toInt(), ByteUtil.BitConversion.LITTLE_ENDIAN)).isEqualTo(0x04030201)
        assertThat(ByteUtil.toInt(0x01.toInt(), 0x02.toInt(), 0x03.toInt(), null, ByteUtil.BitConversion.LITTLE_ENDIAN)).isEqualTo(0x030201)
        assertThat(ByteUtil.toInt(0x01.toInt(), 0x02.toInt(), null, null, ByteUtil.BitConversion.LITTLE_ENDIAN)).isEqualTo(0x0201)
        assertThat(ByteUtil.toInt(0x01.toInt(), null, null, null, ByteUtil.BitConversion.LITTLE_ENDIAN)).isEqualTo(0x01)
        assertThat(ByteUtil.toInt(0x01.toInt(), 0x02.toInt(), 0x03.toInt(), 0x04.toInt(), ByteUtil.BitConversion.BIG_ENDIAN)).isEqualTo(0x01020304)
        assertThat(ByteUtil.toInt(0x01.toInt(), 0x02.toInt(), 0x03.toInt(), null, ByteUtil.BitConversion.BIG_ENDIAN)).isEqualTo(0x010203)
        assertThat(ByteUtil.toInt(0x01.toInt(), 0x02.toInt(), null, null, ByteUtil.BitConversion.BIG_ENDIAN)).isEqualTo(0x0102)
        assertThat(ByteUtil.toInt(0x01.toInt(), null, null, null, ByteUtil.BitConversion.BIG_ENDIAN)).isEqualTo(0x01)

        assertThat(ByteUtil.toInt(0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), ByteUtil.BitConversion.LITTLE_ENDIAN)).isEqualTo(0x04030201)
        assertThat(ByteUtil.toInt(0x01.toByte(), 0x02.toByte(), 0x03.toByte(), null, ByteUtil.BitConversion.LITTLE_ENDIAN)).isEqualTo(0x030201)
        assertThat(ByteUtil.toInt(0x01.toByte(), 0x02.toByte(), null, null, ByteUtil.BitConversion.LITTLE_ENDIAN)).isEqualTo(0x0201)
        assertThat(ByteUtil.toInt(0x01.toByte(), null, null, null, ByteUtil.BitConversion.LITTLE_ENDIAN)).isEqualTo(0x01)
        assertThat(ByteUtil.toInt(0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), ByteUtil.BitConversion.BIG_ENDIAN)).isEqualTo(0x01020304)
        assertThat(ByteUtil.toInt(0x01.toByte(), 0x02.toByte(), 0x03.toByte(), null, ByteUtil.BitConversion.BIG_ENDIAN)).isEqualTo(0x010203)
        assertThat(ByteUtil.toInt(0x01.toByte(), 0x02.toByte(), null, null, ByteUtil.BitConversion.BIG_ENDIAN)).isEqualTo(0x0102)
        assertThat(ByteUtil.toInt(0x01.toByte(), null, null, null, ByteUtil.BitConversion.BIG_ENDIAN)).isEqualTo(0x01)

        assertThat(ByteUtil.toInt(0x01.toInt(), 0x02.toInt())).isEqualTo(0x0102)
        assertThat(ByteUtil.toInt(0x01.toInt(), 0x02.toInt(), ByteUtil.BitConversion.BIG_ENDIAN)).isEqualTo(0x0102)
        assertThat(ByteUtil.toInt(0x01.toByte(), 0x02.toByte())).isEqualTo(0x0102)
        assertThat(ByteUtil.toInt(0x01.toByte(), 0x02.toByte(), 0x03.toByte())).isEqualTo(0x010203)
        assertThat(ByteUtil.toInt(0x01.toInt(), 0x02.toInt(), 0x03.toInt())).isEqualTo(0x010203)
    }

    @Test
    fun makeUnsignedShort() {
        assertThat(ByteUtil.makeUnsignedShort(0xF1, 0xF2)).isEqualTo(61938)
    }

    @Test
    fun getCorrectHexValue() {
        assertThat(ByteUtil.getCorrectHexValue(0x1.toByte())).isEqualTo("01")
        assertThat(ByteUtil.getCorrectHexValue(0xF1.toByte())).isEqualTo("f1")
        assertThat(ByteUtil.getCorrectHexValue(0xABF1.toByte())).isEqualTo("f1")
    }

    @Test
    fun getHex() {
        assertThat(ByteUtil.getHex(null as ByteArray?)).isEqualTo(null)
        assertThat(ByteUtil.getHex(byteArrayOf(0xF1.toByte(), 0xDB.toByte()))).isEqualTo("F1 DB")
        assertThat(ByteUtil.getHex(byteArrayOf(0xF1.toByte(), 0xDB.toByte(), 0xAB.toByte()), 2)).isEqualTo("F1 DB")

        assertThat(ByteUtil.getHex(arrayListOf(0xF1.toByte(), 0xDB.toByte(), 0xAB.toByte()))).isEqualTo("F1 DB AB")

        assertThat(ByteUtil.getHex((-1).toByte())).isEqualTo("-1")
        assertThat(ByteUtil.getHex((11).toByte())).isEqualTo("0x0B")
        assertThat(ByteUtil.getHex((0xFA).toByte())).isEqualTo("0xFA")
    }

    @Test
    fun createByteArrayFromCompactString() {
        assertThat(ByteUtil.createByteArrayFromCompactString("C800A0")[0]).isEqualTo(-56)
        assertThat(ByteUtil.createByteArrayFromCompactString("C800A0")[2]).isEqualTo(-96)
    }

    @Test
    fun createByteArrayFromString() {
        assertThat(ByteUtil.createByteArrayFromString("C8 00 A0")[0]).isEqualTo(-56)
        assertThat(ByteUtil.createByteArrayFromString("C8 00 A0")[2]).isEqualTo(-96)
    }

    @Test
    fun createByteArrayFromHexString() {
        assertThat(ByteUtil.createByteArrayFromHexString("0xC800A0")[0]).isEqualTo(-56)
        assertThat(ByteUtil.createByteArrayFromHexString("0xC800A0")[2]).isEqualTo(-96)
    }

    @Test
    fun testCreateByteArrayFromCompactString() {
        assertThat(ByteUtil.createByteArrayFromCompactString("FFC800A0", 2, 6)[0]).isEqualTo(-56)
        assertThat(ByteUtil.createByteArrayFromCompactString("FFC800A0", 2, 6)[2]).isEqualTo(-96)
    }
}