package app.aaps.plugins.eversense.protocol

/** A growing byte buffer, for building and collecting frames on every platform. */
internal class ByteSink(capacity: Int = 32) {

    private var bytes = ByteArray(maxOf(capacity, 1))
    var size = 0
        private set

    /** Writes the low 8 bits of [octet]. */
    fun write(octet: Int) {
        ensure(1)
        bytes[size++] = octet.toByte()
    }

    fun write(data: ByteArray, offset: Int = 0, length: Int = data.size - offset) {
        ensure(length)
        data.copyInto(bytes, size, offset, offset + length)
        size += length
    }

    fun toByteArray(): ByteArray = bytes.copyOf(size)

    private fun ensure(extra: Int) {
        if (size + extra <= bytes.size) return
        bytes = bytes.copyOf(maxOf(bytes.size * 2, size + extra))
    }
}
