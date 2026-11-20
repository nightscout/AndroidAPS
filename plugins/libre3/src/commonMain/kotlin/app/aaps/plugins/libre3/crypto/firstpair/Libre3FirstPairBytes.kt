package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException

/*
 * The small byte helpers of the first pairing scheme.
 *
 * Ported from the private helpers at the end of LibreCRKit `Crypto/FirstPairSourceSlice.swift`
 * at pin `a86b92f`.
 *
 * The Swift works on `[UInt8]`. Kotlin has no unsigned byte array in its normal library, so this
 * port keeps `ByteArray` and reads every byte through [u8], which masks the sign away. Words are
 * read into `UInt` and `ULong`, whose arithmetic wraps exactly like the Swift `&*` and `&+`.
 */

/** One byte of the array, as a number from 0 to 255. */
internal fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

/** Four bytes, low byte first. */
internal fun readUInt32LE(bytes: ByteArray, offset: Int): UInt =
    bytes.u8(offset).toUInt() or
        (bytes.u8(offset + 1).toUInt() shl 8) or
        (bytes.u8(offset + 2).toUInt() shl 16) or
        (bytes.u8(offset + 3).toUInt() shl 24)

/** Eight bytes, low byte first. */
internal fun readUInt64LE(bytes: ByteArray, offset: Int): ULong {
    var value = 0uL
    for (index in 0 until 8) {
        value = value or (bytes.u8(offset + index).toULong() shl (index * 8))
    }
    return value
}

/** Writes four bytes, low byte first. */
internal fun writeUInt32LE(value: UInt, into: ByteArray, at: Int) {
    for (index in 0 until 4) {
        into[at + index] = ((value shr (index * 8)) and 0xFFu).toByte()
    }
}

/** Writes eight bytes, low byte first. */
internal fun writeUInt64LE(value: ULong, into: ByteArray, at: Int) {
    for (index in 0 until 8) {
        into[at + index] = ((value shr (index * 8)) and 0xFFuL).toByte()
    }
}

/** The four bytes of a word, low byte first. */
internal fun u32LEBytes(value: UInt): ByteArray = ByteArray(4) { ((value shr (it * 8)) and 0xFFu).toByte() }

/** The eight bytes of a long word, low byte first. */
internal fun u64LEBytes(value: ULong): ByteArray = ByteArray(8) { ((value shr (it * 8)) and 0xFFuL).toByte() }

/** Copies [with] into [target] at [at], like the Swift `replace`. */
internal fun replace(target: ByteArray, at: Int, with: ByteArray) {
    with.copyInto(target, at)
}

/** A piece of an array, with a clear message instead of an index error. */
internal fun checkedSlice(bytes: ByteArray, offset: Int, count: Int, name: String): ByteArray {
    if (offset < 0 || count < 0 || offset + count > bytes.size) {
        throw Libre3CryptoException("the first pairing table $name has nothing at offset $offset")
    }
    return bytes.copyOfRange(offset, offset + count)
}

/** Refuses a source that is shorter than the step needs. */
internal fun requireSize(bytes: ByteArray, count: Int, label: String) {
    if (bytes.size < count) {
        throw Libre3CryptoException("the first pairing source $label must be at least $count bytes, not ${bytes.size}")
    }
}

/** Bytes written as text, two letters per byte. */
internal fun hexBytes(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "a hex text must have an even number of letters" }
    return ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

/** Bytes written as numbers, so that a table copied from the Swift stays readable. */
internal fun bytesOf(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
