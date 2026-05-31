package app.aaps.pump.insight.utils

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ByteBufTest : TestBase() {

    @Test
    fun testLengthOnCreation() {
        val sut = ByteBuf(15)
        assertThat(sut.filledSize).isEqualTo(0) // not filled yet
        assertThat(sut.bytes.size).isEqualTo(0)
    }

    @Test
    fun testPutGetBytes() {
        val sut = ByteBuf(15)
        val array1 = byteArrayOf(10, 20, -10, 15, -128, 127, 27)
        sut.putBytes(array1)
        assertThat(sut.bytes).isEqualTo(array1)
        assertThat(sut.bytes.size).isEqualTo(array1.size)
        assertThat(sut.readByte()).isEqualTo(10.toByte())
        assertThat(sut.readByte()).isEqualTo(20.toByte())
        assertThat(sut.bytes).isEqualTo(byteArrayOf(-10, 15, -128, 127, 27))
        sut.shift(4)
        assertThat(sut.bytes).isEqualTo(byteArrayOf(27))
        sut.putBytes(5.toByte(), 6)
        assertThat(sut.bytes).isEqualTo(byteArrayOf(27, 5, 5, 5, 5, 5, 5))
        sut.putByte(25.toByte())
        sut.putByte(254.toByte())
        assertThat(sut.bytes).isEqualTo(byteArrayOf(27, 5, 5, 5, 5, 5, 5, 25, -2))
        assertThat(sut.readBytes(4)).isEqualTo(byteArrayOf(27, 5, 5, 5))
        assertThat(sut.getBytes(4)).isEqualTo(byteArrayOf(5, 5, 5, 25))
    }

    @Test
    fun testPutReadByteLE() {
        val sut = ByteBuf(15)
        val array1 = byteArrayOf(10, 20, -10, 15, -128, 127, 27)
        sut.putBytesLE(array1)
        assertThat(sut.bytes).isEqualTo(byteArrayOf(27, 127, -128, 15, -10, 20, 10))
        assertThat(sut.readBytesLE(array1.size)).isEqualTo(array1)
        assertThat(sut.bytes.size).isEqualTo(0)
    }

    @Test
    fun testPutReadUInt() {
        val sut = ByteBuf(15)
        sut.putUInt8(240)
        sut.putUInt8(-227)
        sut.putUInt8(-90)
        assertThat(sut.bytes).isEqualTo(byteArrayOf(-16, 29, -90))
        assertThat(sut.readUInt8()).isEqualTo(240.toShort())
        assertThat(sut.readUInt8()).isEqualTo(29.toShort())
        assertThat(sut.readUInt8()).isEqualTo(166.toShort())
        sut.putUInt16LE(25237)
        sut.putUInt16LE(-13695)
        assertThat(sut.bytes).isEqualTo(byteArrayOf(-107, 98, -127, -54))
        assertThat(sut.getUInt16LE(0)).isEqualTo(25237)
        assertThat(sut.getUInt16LE(2)).isEqualTo(51841)
        assertThat(sut.readUInt16LE()).isEqualTo(25237)
        assertThat(sut.readUInt16LE()).isEqualTo(51841)
    }

    @Test @Throws(Exception::class)
    fun testPutReadDecimal() {
        val sut = ByteBuf(15)
        sut.putUInt16Decimal(245.9275)
        sut.putUInt16Decimal(-115.249)
        assertThat(sut.bytes).isEqualTo(byteArrayOf(17, 96, -5, -46))
        assertThat(sut.readUInt16Decimal()).isWithin(0.001).of(245.93)
        assertThat(sut.readUInt16Decimal()).isWithin(0.001).of(540.11)
        sut.putBytes(byteArrayOf(-27, 32, 124, -113, 20, 34, -105, 47))
        assertThat(sut.readUInt32Decimal100()).isWithin(0.001).of(24072767.73)
        assertThat(sut.readUInt32Decimal1000()).isWithin(0.001).of(798433.812)
    }

    @Test
    fun testPutReadShort() {
        val sut = ByteBuf(15)
        sut.putShort(240)
        sut.putShort(-32207)
        assertThat(sut.bytes).isEqualTo(byteArrayOf(0, -16, -126, 49))
        assertThat(sut.readShort()).isEqualTo(240.toShort())
        assertThat(sut.readShort()).isEqualTo(33329.toShort())
    }

    @Test
    fun testPutReadUInt32LE() {
        val sut = ByteBuf(15)
        sut.putUInt32LE(164532015)
        assertThat(sut.bytes).isEqualTo(byteArrayOf(47, -113, -50, 9))
        assertThat(sut.readUInt32LE()).isEqualTo(164532015)
    }

    @Test
    fun testPutReadUTF16ASCII() {
        val sut = ByteBuf(68)
        val txt = "*Gs :&{]8/"
        sut.putBytes(byteArrayOf(42, 71, 115, 32, 58, 38, 123, 93, 56, 47, 0))
        assertThat(sut.readASCII(txt.length)).isEqualTo(txt)
        sut.putUTF16(txt, txt.length)
        assertThat(sut.bytes).isEqualTo(byteArrayOf(42, 0, 71, 0, 115, 0, 32, 0, 58, 0, 38, 0, 123, 0, 93, 0, 56, 0, 47, 0, 0, 0))
        sut.clear()
        sut.putUTF16("Profil 1", 8)
        assertThat(sut.readUTF16(16)).isEqualTo("Profil 1")

    }

}