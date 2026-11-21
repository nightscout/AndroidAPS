package app.aaps.pump.aaruy.emulator

/**
 * Little endian bytes, read and written with the names of `java.nio.ByteBuffer`, which the emulator used before it moved
 * to shared code. Reading or writing past the end throws, as the buffer did.
 */
internal class EmulatorBytes(private val bytes: ByteArray) {

    private var position = 0

    fun put(value: Byte): EmulatorBytes = apply { bytes[position++] = value }

    fun put(values: ByteArray): EmulatorBytes = apply {
        values.copyInto(bytes, position)
        position += values.size
    }

    fun putShort(value: Short): EmulatorBytes = apply {
        put(value.toByte())
        put((value.toInt() shr 8).toByte())
    }

    fun putInt(value: Int): EmulatorBytes = apply {
        putShort(value.toShort())
        putShort((value shr 16).toShort())
    }

    fun position(newPosition: Int): EmulatorBytes = apply { position = newPosition }

    fun get(): Byte = bytes[position++]

    fun get(index: Int): Byte = bytes[index]

    /** The next 16 bits, and moves on. */
    val short: Short
        get() = getShort(position).also { position += 2 }

    /** The next 32 bits, and moves on. */
    val int: Int
        get() = getInt(position).also { position += 4 }

    fun getShort(index: Int): Short = ((bytes[index].toInt() and 0xFF) or ((bytes[index + 1].toInt() and 0xFF) shl 8)).toShort()

    fun getInt(index: Int): Int = (getShort(index).toInt() and 0xFFFF) or (getShort(index + 2).toInt() shl 16)

    fun array(): ByteArray = bytes
}
