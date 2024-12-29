package app.aaps.pump.insight.utils

import app.aaps.shared.tests.TestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.Test

class ByteBufTest : TestBase() {

    @Test
    fun testLengthOnCreation() {
        val sut = ByteBuf(15)
        assertEquals(0, sut.filledSize) // not filled yet
        assertEquals(0, sut.bytes.size)
    }

    @Test
    fun testPutGetBytes() {
        val sut = ByteBuf(15)
        val array1 = byteArrayOf(10, 20, -10, 15, -128, 127, 27)
        sut.putBytes(array1)
        assertTrue(sut.bytes.contentEquals(array1))
        assertEquals(array1.size, sut.bytes.size)
        assertEquals(10.toByte(), sut.readByte())
        assertEquals(20.toByte(), sut.readByte())
        assertTrue(sut.bytes.contentEquals(byteArrayOf(-10, 15, -128, 127, 27)))
        sut.shift(4)
        assertTrue(sut.bytes.contentEquals(byteArrayOf(27)))
        sut.putBytes(5.toByte(), 6)
        assertTrue(sut.bytes.contentEquals(byteArrayOf(27, 5, 5, 5, 5, 5, 5)))
        sut.putByte(25.toByte())
        sut.putByte(254.toByte())
        assertTrue(sut.bytes.contentEquals(byteArrayOf(27, 5, 5, 5, 5, 5, 5, 25, -2)))
        assertTrue(sut.readBytes(4).contentEquals(byteArrayOf(27, 5, 5, 5)))
        assertTrue(sut.getBytes(4).contentEquals(byteArrayOf(5, 5, 5, 25)))
    }

    @Test
    fun testPutReadByteLE() {
        val sut = ByteBuf(15)
        val array1 = byteArrayOf(10, 20, -10, 15, -128, 127, 27)
        sut.putBytesLE(array1)
        assertTrue(sut.bytes.contentEquals(byteArrayOf(27, 127, -128, 15, -10, 20, 10)))
        assertTrue(sut.readBytesLE(array1.size).contentEquals(array1))
        assertEquals(0, sut.bytes.size)
    }

    @Test
    fun testPutReadUInt() {
        val sut = ByteBuf(15)
        sut.putUInt8(240)
        sut.putUInt8(-227)
        sut.putUInt8(-90)
        assertTrue(sut.bytes.contentEquals(byteArrayOf(-16, 29, -90)))
        assertEquals(240.toShort(), sut.readUInt8())
        assertEquals(29.toShort(), sut.readUInt8())
        assertEquals(166.toShort(), sut.readUInt8())
        sut.putUInt16LE(25237)
        sut.putUInt16LE(-13695)
        assertTrue(sut.bytes.contentEquals(byteArrayOf(-107, 98, -127, -54)))
        assertEquals(25237, sut.getUInt16LE(0))
        assertEquals(51841, sut.getUInt16LE(2))
        assertEquals(25237, sut.readUInt16LE())
        assertEquals(51841, sut.readUInt16LE())
    }

    @Test @Throws(Exception::class)
    fun testPutReadDecimal() {
        val sut = ByteBuf(15)
        sut.putUInt16Decimal(245.9275)
        sut.putUInt16Decimal(-115.249)
        assertTrue(sut.bytes.contentEquals(byteArrayOf(17, 96, -5, -46)))
        assertEquals(245.93, sut.readUInt16Decimal(), 0.001)
        assertEquals(540.11, sut.readUInt16Decimal(), 0.001)
        sut.putBytes(byteArrayOf(-27, 32, 124, -113, 20, 34, -105, 47))
        assertEquals(24072767.73, sut.readUInt32Decimal100(), 0.001)
        assertEquals(798433.812, sut.readUInt32Decimal1000(), 0.001)
    }

    @Test
    fun testPutReadShort() {
        val sut = ByteBuf(15)
        sut.putShort(240)
        sut.putShort(-32207)
        assertTrue(sut.bytes.contentEquals(byteArrayOf(0, -16, -126, 49)))
        assertEquals(240.toShort(), sut.readShort())
        assertEquals(33329.toShort(), sut.readShort())
    }

    @Test
    fun testPutReadUInt32LE() {
        val sut = ByteBuf(15)
        sut.putUInt32LE(164532015)
        assertTrue(sut.bytes.contentEquals(byteArrayOf(47, -113, -50, 9)))
        assertEquals(164532015, sut.readUInt32LE())
    }

    @Test
    fun testPutReadUTF16ASCII() {
        val sut = ByteBuf(68)
        val txt = "*Gs :&{]8/"
        sut.putBytes(byteArrayOf(42, 71, 115, 32, 58, 38, 123, 93, 56, 47, 0))
        assertEquals(txt, sut.readASCII(txt.length))
        sut.putUTF16(txt, txt.length)
        assertTrue(sut.bytes.contentEquals(byteArrayOf(42, 0, 71, 0, 115, 0, 32, 0, 58, 0, 38, 0, 123, 0, 93, 0, 56, 0, 47, 0, 0, 0)))
        sut.clear()
        sut.putUTF16("Profil 1", 8)
        assertEquals("Profil 1", sut.readUTF16(16))

    }

}