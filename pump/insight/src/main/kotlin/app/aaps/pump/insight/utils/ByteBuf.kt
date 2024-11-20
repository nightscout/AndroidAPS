package app.aaps.pump.insight.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import kotlin.experimental.and

class ByteBuf(length: Int) {

    private val _bytes: ByteArray = ByteArray(length)
    var filledSize = 0
        private set
    val bytes: ByteArray
        get() {
            val bytes = ByteArray(filledSize)
            System.arraycopy(_bytes, 0, bytes, 0, filledSize)
            return bytes
        }

    fun shift(offset: Int) {
        System.arraycopy(_bytes, offset, _bytes, 0, _bytes.size - offset)
        filledSize -= offset
    }

    // public byte getByte(int position) { return _bytes[position]; }
    private val byte: Byte
        get() = _bytes[0]

    fun readByte(): Byte {
        val b = byte
        shift(1)
        return b
    }

    fun putByte(b: Byte) {
        _bytes[filledSize] = b
        filledSize += 1
    }

    fun putBytes(b: Byte, count: Int) {
        for (i in 0 until count) _bytes[filledSize++] = b
    }

    fun getBytes(position: Int, length: Int): ByteArray {
        val copy = ByteArray(length)
        System.arraycopy(_bytes, position, copy, 0, length)
        return copy
    }

    fun getBytes(length: Int): ByteArray {
        return getBytes(0, length)
    }

    fun readBytes(length: Int): ByteArray {
        val copy = getBytes(length)
        shift(length)
        return copy
    }

    fun readBytes(): ByteArray {
        return readBytes(filledSize)
    }

    @JvmOverloads fun putBytes(bytes: ByteArray, length: Int = bytes.size) {
        System.arraycopy(bytes, 0, _bytes, filledSize, length)
        filledSize += length
    }

    private fun getBytesLE(position: Int, length: Int): ByteArray {
        val copy = ByteArray(length)
        for (i in 0 until length) copy[i] = _bytes[length - 1 - i + position]
        return copy
    }

    // private byte[] getBytesLE(int length) { return getBytesLE(0, length); }
    fun readBytesLE(length: Int): ByteArray {
        val copy = getBytesLE(0, length)
        shift(length)
        return copy
    }

    private fun putBytesLE(bytes: ByteArray, length: Int) {
        for (i in 0 until length) _bytes[filledSize + length - 1 - i] = bytes[i]
        filledSize += length
    }

    fun putBytesLE(bytes: ByteArray) {
        putBytesLE(bytes, bytes.size)
    }

    fun putByteBuf(byteBuf: ByteBuf) {
        putBytes(byteBuf.bytes, byteBuf.filledSize)
    }

    private fun getUInt8(position: Int): Short {
        return _bytes[position].toShort() and 0xFF
    }

    // private short getUInt8() { return getUInt8(0); }
    fun readUInt8(): Short {
        val value = getUInt8(0)
        shift(1)
        return value
    }

    fun putUInt8(value: Short) {
        putByte((value and 0xFF).toByte())
    }

    fun getUInt16LE(position: Int): Int {
        var p = position
        return (_bytes[p++].toShort() and 0xFF) +
            (_bytes[p].toShort() and 0xFF) * 256 // Convert to short and replace "or" by "+" and "shl 8" by "* 256" to fix convertion compared to java
    }

    //private int getUInt16LE() { return getUInt16LE(0); }
    fun readUInt16LE(): Int {
        val i = getUInt16LE(0)
        shift(2)
        return i
    }

    fun putUInt16LE(i: Int) {
        putByte((i and 0xFF).toByte())
        putByte((i shr 8 and 0xFF).toByte())
    }

    private fun getUInt16Decimal(position: Int): Double {
        return BigDecimal(getUInt16LE(position))
            .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
            .toDouble()
    }

    //private double getUInt16Decimal() { return getUInt16Decimal(0); }
    fun readUInt16Decimal(): Double {
        val d = getUInt16Decimal(0)
        shift(2)
        return d
    }

    fun putUInt16Decimal(d: Double) {
        putUInt16LE(
            BigDecimal(d)
                .multiply(BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP)
                .toInt()
        )
    }

    private fun getUInt32Decimal100(position: Int): Double {
        return BigDecimal(getUInt32LE(position))
            .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
            .toDouble()
    }

    //private double getUInt32Decimal100() { return getUInt32Decimal100(0); }
    fun readUInt32Decimal100(): Double {
        val d = getUInt32Decimal100(0)
        shift(4)
        return d
    }

    /*
    public void putUInt32Decimal100(double d) {
        putUInt32LE(new BigDecimal(d)
                .multiply(new BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue());
    }
*/
    private fun getUInt32Decimal1000(position: Int): Double {
        return BigDecimal(getUInt32LE(position))
            .divide(BigDecimal(1000), 3, RoundingMode.HALF_UP)
            .toDouble()
    }

    //private double getUInt32Decimal1000() { return getUInt32Decimal1000(0); }
    fun readUInt32Decimal1000(): Double {
        val d = getUInt32Decimal1000(0)
        shift(4)
        return d
    }

    private fun getShort(position: Int): Short {
        var p = position
        return (
            (_bytes[p++].toLong() and 0xFF) shl 8 or
                (_bytes[p].toLong() and 0xFF)
            ).toShort()  // Convert to Long and keep "or", "and" & "shl" to fix convertion compared to java
    }

    //    public short getShort() { return getShort(0); }
    fun readShort(): Short {
        val s = getShort(0)
        shift(2)
        return s
    }

    fun putShort(s: Short) {
        putByte((s.toInt() shr 8).toByte())
        putByte(s.toByte())
    }

    private fun getUInt32LE(position: Int): Long {
        var p = position
        return _bytes[p++].toLong() and 0xFF or (
            _bytes[p++].toLong() and 0xFF shl 8) or (
            _bytes[p++].toLong() and 0xFF shl 16) or (
            _bytes[p].toLong() and 0xFF shl 24)
    }

    // private long getUInt32LE() { return getUInt32LE(0); }
    fun readUInt32LE(): Long {
        val l = getUInt32LE(0)
        shift(4)
        return l
    }

    fun putUInt32LE(l: Long) {
        putByte((l and 0xFF).toByte())
        putByte((l shr 8 and 0xFF).toByte())
        putByte((l shr 16 and 0xFF).toByte())
        putByte((l shr 24 and 0xFF).toByte())
    }

    private fun getUTF16(position: Int, stringLength: Int): String {
        val string = String(getBytes(position, stringLength * 2 + 2), StandardCharsets.UTF_16LE)
        return string.substring(0, string.indexOf(String(charArrayOf(0.toChar(), 0.toChar()))))
    }

    //private String getUTF16(int stringLength) { return getUTF16(0, stringLength); }
    fun readUTF16(stringLength: Int): String {
        val string = getUTF16(0, stringLength)
        shift(stringLength * 2 + 2)
        return string
    }

    fun putUTF16(string: String, stringLength: Int) {
        putBytes(string.toByteArray(StandardCharsets.UTF_16LE), stringLength * 2)
        putBytes(0.toByte(), 2)
    }

    private fun getASCII(position: Int, stringLength: Int): String {
        val string = String(getBytes(position, stringLength + 1), StandardCharsets.US_ASCII)
        return string.substring(0, string.indexOf(0.toChar()))
    }

    //private String getASCII(int stringLength) { return getASCII(0, stringLength); }
    fun readASCII(stringLength: Int): String {
        val string = getASCII(0, stringLength)
        shift(stringLength + 1)
        return string
    }

    fun putASCII(string: String, stringLength: Int) {
        putBytes(string.toByteArray(StandardCharsets.UTF_16LE), stringLength * 2)
        putBytes(0.toByte(), 1)
    }

    private fun getBoolean(position: Int): Boolean {
        return getUInt16LE(position) == 75
    }

    //public boolean getBoolean() { return getBoolean(0); }
    fun readBoolean(): Boolean {
        val bool = getBoolean(0)
        shift(2)
        return bool
    }

    fun putBoolean(bool: Boolean) {
        putUInt16LE(if (bool) 75 else 180)
    }

    fun clear() {
        shift(filledSize)
    }

    companion object {

        private fun from(bytes: ByteArray, length: Int): ByteBuf {
            val byteBuf = ByteBuf(length)
            byteBuf.putBytes(bytes, length)
            return byteBuf
        }

        fun from(bytes: ByteArray): ByteBuf {
            return from(bytes, bytes.size)
        }
    }
}

