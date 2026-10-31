package app.aaps.plugins.dexcomoneplus.parse

/**
 * The little bit of `java.nio.ByteBuffer` this driver used, written so the packet code can stay in
 * shared code.
 *
 * The Dex family puts every multi byte field on the wire little endian, so these two classes are
 * little endian only. They read and write exactly the bytes `ByteBuffer.order(LITTLE_ENDIAN)` did,
 * and the packet tests in `androidHostTest` are what hold them to that.
 */

/**
 * Reads a packet front to back, like a `ByteBuffer` with its position.
 *
 * Reading past the end throws, the way `ByteBuffer` threw `BufferUnderflowException`, so a caller
 * that wrapped a decode in `try` keeps behaving the same way.
 */
internal class OnePlusByteReader(private val bytes: ByteArray, private var position: Int = 0) {

    /** How many bytes are still unread. */
    val remaining: Int get() = bytes.size - position

    /** The next byte, signed, as `ByteBuffer.get()` returned it. */
    fun byte(): Byte {
        require(remaining >= 1) { "OnePlusByteReader underflow at $position" }
        return bytes[position++]
    }

    /** The next byte as an unsigned 0..255 value. */
    fun u8(): Int = byte().toInt() and 0xff

    /** The next two bytes, little endian, signed, as `ByteBuffer.getShort()` returned them. */
    fun short(): Short {
        require(remaining >= 2) { "OnePlusByteReader underflow at $position" }
        val low = bytes[position].toInt() and 0xff
        val high = bytes[position + 1].toInt() and 0xff
        position += 2
        return ((high shl 8) or low).toShort()
    }

    /** The next four bytes, little endian, signed, as `ByteBuffer.getInt()` returned them. */
    fun int(): Int {
        require(remaining >= 4) { "OnePlusByteReader underflow at $position" }
        val value = intAt(position)
        position += 4
        return value
    }

    /** Four bytes at an absolute offset, little endian, as `ByteBuffer.getInt(index)` did. */
    fun intAt(offset: Int): Int {
        require(offset >= 0 && offset + 4 <= bytes.size) { "OnePlusByteReader offset $offset out of range" }
        return (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
    }

    /** Steps over [count] bytes without reading them. */
    fun skip(count: Int) {
        require(remaining >= count) { "OnePlusByteReader underflow at $position" }
        position += count
    }
}

/**
 * Builds a fixed length packet front to back, like `ByteBuffer.allocate(size)`.
 *
 * The bytes that are never written stay zero, which is what `ByteBuffer.allocate` gave as well,
 * and the two CRC bytes at the end of most requests rely on it.
 */
internal class OnePlusByteWriter(size: Int) {

    private val bytes = ByteArray(size)
    private var position = 0

    fun put(value: Byte) {
        bytes[position++] = value
    }

    /** Two bytes, little endian, as `ByteBuffer.putShort()` wrote them. */
    fun putShort(value: Short) {
        val v = value.toInt()
        bytes[position++] = (v and 0xff).toByte()
        bytes[position++] = ((v shr 8) and 0xff).toByte()
    }

    /** Four bytes, little endian, as `ByteBuffer.putInt()` wrote them. */
    fun putInt(value: Int) {
        bytes[position++] = (value and 0xff).toByte()
        bytes[position++] = ((value shr 8) and 0xff).toByte()
        bytes[position++] = ((value shr 16) and 0xff).toByte()
        bytes[position++] = ((value shr 24) and 0xff).toByte()
    }

    /** The whole buffer, including the bytes that were never written. */
    fun array(): ByteArray = bytes
}

/**
 * Lower case hex of every byte, the same text `"%02x".format(b)` produced per byte.
 *
 * `String.format` is JVM only, which is the only reason this exists.
 */
internal fun ByteArray.onePlusToHex(): String {
    val out = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xff
        out.append(HEX_DIGITS[v shr 4])
        out.append(HEX_DIGITS[v and 0x0f])
    }
    return out.toString()
}

private const val HEX_DIGITS = "0123456789abcdef"
