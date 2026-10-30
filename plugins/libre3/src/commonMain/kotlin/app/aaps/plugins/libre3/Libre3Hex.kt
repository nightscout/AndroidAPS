package app.aaps.plugins.libre3

/**
 * One byte as two lower case hex digits, the same text `"%02x".format(b)` gave.
 *
 * `String.format` is JVM only, which is the only reason this exists. The driver writes these into
 * its log lines and into the pairing trace, so the text has to stay byte for byte the same.
 */
internal fun Byte.libre3HexLower(): String {
    val v = toInt() and 0xFF
    return "${LOWER_DIGITS[v shr 4]}${LOWER_DIGITS[v and 0x0F]}"
}

/** One byte as two upper case hex digits, the same text `"%02X".format(b)` gave. */
internal fun Byte.libre3HexUpper(): String {
    val v = toInt() and 0xFF
    return "${UPPER_DIGITS[v shr 4]}${UPPER_DIGITS[v and 0x0F]}"
}

/** Every byte as lower case hex, with nothing between them. */
internal fun ByteArray.libre3HexLower(): String {
    val out = StringBuilder(size * 2)
    for (b in this) out.append(b.libre3HexLower())
    return out.toString()
}

private const val LOWER_DIGITS = "0123456789abcdef"

private const val UPPER_DIGITS = "0123456789ABCDEF"

/**
 * The bytes read as US-ASCII text, the same answer `String(bytes, Charsets.US_ASCII)` gave.
 *
 * A charset name is JVM only, which is the only reason this exists. ASCII is one byte per
 * character, and a byte with its top bit set is not ASCII at all, so it becomes the replacement
 * character exactly as the JVM decoder made it. A sensor serial number is plain ASCII, so in
 * practice this branch never runs; it is here so a damaged frame cannot turn into plausible text.
 */
internal fun ByteArray.libre3AsciiToString(): String {
    val out = StringBuilder(size)
    for (b in this) {
        val v = b.toInt() and 0xFF
        out.append(if (v < 0x80) v.toChar() else '�')
    }
    return out.toString()
}
