package app.aaps.cgm.dexcomg7.protocol

/** Unsigned byte at [index]. */
internal fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xff

/** Unsigned 16-bit little endian value starting at [index]. */
internal fun ByteArray.u16le(index: Int): Int = u8(index) or (u8(index + 1) shl 8)

/** Unsigned 24-bit little endian value starting at [index]. */
internal fun ByteArray.u24le(index: Int): Long = (u8(index).toLong()) or (u8(index + 1).toLong() shl 8) or (u8(index + 2).toLong() shl 16)

/** Unsigned 32-bit little endian value starting at [index]. */
internal fun ByteArray.u32le(index: Int): Long = u24le(index) or (u8(index + 3).toLong() shl 24)

internal fun Int.u16leBytes(): ByteArray = byteArrayOf(this.toByte(), (this shr 8).toByte())

internal fun Long.u32leBytes(): ByteArray =
    byteArrayOf(this.toByte(), (this shr 8).toByte(), (this shr 16).toByte(), (this shr 24).toByte())

internal fun Int.u32beBytes(): ByteArray =
    byteArrayOf((this shr 24).toByte(), (this shr 16).toByte(), (this shr 8).toByte(), this.toByte())

/** Lower case hex, no separators. Used for logs and for storing keys in preferences. */
fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

/** Parses hex (upper or lower case). Returns null for an odd length or a non-hex character. */
fun String.hexToBytesOrNull(): ByteArray? {
    if (length % 2 != 0) return null
    val out = ByteArray(length / 2)
    for (i in out.indices) {
        val high = Character.digit(this[i * 2], 16)
        val low = Character.digit(this[i * 2 + 1], 16)
        if (high < 0 || low < 0) return null
        out[i] = ((high shl 4) or low).toByte()
    }
    return out
}

internal fun String.hexToBytes(): ByteArray = requireNotNull(hexToBytesOrNull()) { "Not hex: $this" }
