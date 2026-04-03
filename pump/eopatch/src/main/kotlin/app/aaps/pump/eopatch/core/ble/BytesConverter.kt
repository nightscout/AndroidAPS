package app.aaps.pump.eopatch.core.ble

object BytesConverter {

    private const val FF = 0xFF

    fun toInt(bytes: ByteArray?): Int {
        if (bytes == null || bytes.size < Int.SIZE_BYTES) return -1
        return (bytes[0].toInt() shl 24) or
            ((bytes[1].toInt() and FF) shl 16) or
            ((bytes[2].toInt() and FF) shl 8) or
            (bytes[3].toInt() and FF)
    }

    fun toInt(bytes: ByteArray?, start: Int): Int {
        if (bytes == null || bytes.size < start + Int.SIZE_BYTES) return -1
        return (bytes[start].toInt() shl 24) or
            ((bytes[start + 1].toInt() and FF) shl 16) or
            ((bytes[start + 2].toInt() and FF) shl 8) or
            (bytes[start + 3].toInt() and FF)
    }

    fun toUInt(bytes: ByteArray?, start: Int): Int {
        if (bytes == null || bytes.size < start + Int.SIZE_BYTES) return -1
        return ((bytes[start].toInt() and FF) shl 24) or
            ((bytes[start + 1].toInt() and FF) shl 16) or
            ((bytes[start + 2].toInt() and FF) shl 8) or
            (bytes[start + 3].toInt() and FF)
    }

    fun toInt(b0: Byte, b1: Byte): Int =
        (b0.toInt() shl 8) or (b1.toInt() and FF)

    fun toUInt(b0: Byte, b1: Byte): Int =
        ((b0.toInt() and FF) shl 8) or (b1.toInt() and FF)

    fun toInt(b: Byte): Int = b.toInt()

    fun toUInt(b: Byte): Int = b.toInt() and FF
}
