package app.aaps.plugins.libre3.crypto.firstpair

import java.security.MessageDigest

/**
 * Small helpers shared by the first pairing vector tests.
 *
 * The published vectors of LibreCRKit `Tests/LibreCRKitTests/FirstPairSourceSliceTests.swift` are
 * given either as plain bytes or as the SHA-256 of a longer answer. Both forms are kept here so a
 * reviewer can compare the Kotlin test with the Swift test line by line.
 */
internal object Vectors {

    /** The bytes as text, two letters per byte. */
    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    /** The SHA-256 of the bytes, as text. */
    fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    /** The pattern source the published tests build with `(index * a + b) & 7`. */
    fun pattern(count: Int, multiplier: Int, addend: Int): ByteArray =
        ByteArray(count) { ((it * multiplier + addend) and 7).toByte() }

    /** The context the published tests build by hand, with a chosen length and block index. */
    fun make679f48Context(contextLength: ULong, blockIndex: UInt): ByteArray {
        val bytes = pattern(0x20c, 7, 3)
        for (index in 0 until 8) {
            bytes[index] = ((contextLength shr (index * 8)) and 0xffuL).toByte()
        }
        for (index in 0 until 4) {
            bytes[0x110 + index] = ((blockIndex shr (index * 8)) and 0xffu).toByte()
        }
        return bytes
    }

    /** The long words packed low byte first, as the published tests do. */
    fun packUInt64LE(words: ULongArray): ByteArray {
        val out = ByteArray(words.size * 8)
        for ((index, word) in words.withIndex()) {
            writeUInt64LE(word, out, index * 8)
        }
        return out
    }

    /** The words packed low byte first, as the published tests do. */
    fun packUInt32LE(words: UIntArray): ByteArray {
        val out = ByteArray(words.size * 4)
        for ((index, word) in words.withIndex()) {
            writeUInt32LE(word, out, index * 4)
        }
        return out
    }
}
