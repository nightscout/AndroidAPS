@file:Suppress("SpellCheckingInspection")

package info.nightscout.comboctl.base

/**
 * Computes the CRC-16-MCRF4XX checksum out of the given data.
 *
 * This function can be called repeatedly on various buffer views if
 * all of them are to be covered by the same checksum. In that case,
 * simply pass the previously computed checksum as currentChecksum
 * argument to get an updated checksum. Otherwise, just using the
 * default value 0xFFFF (the "initial seed") is enough.
 *
 * @param data Data to compute the checksum out of.
 * @param currentChecksum Current checksum, or 0xFFFF as initial seed.
 * @return The computed checksum.
 */
fun calculateCRC16MCRF4XX(data: List<Byte>, currentChecksum: Int = 0xFFFF): Int {
    // Original implementation from https://gist.github.com/aurelj/270bb8af82f65fa645c1#gistcomment-2884584

    if (data.isEmpty())
        return currentChecksum

    var newChecksum = currentChecksum

    for (dataByte in data) {
        var t: Int

        newChecksum = newChecksum xor dataByte.toPosInt()
        // The "and 0xFF" are needed since the original C implementation
        // worked with implicit 8-bit logic, meaning that only the lower
        // 8 bits are kept - the rest is thrown away.
        var el: Int = (newChecksum xor (newChecksum shl 4)) and 0xFF
        t = ((el shl 3) or (el shr 5)) and 0xFF
        el = el xor (t and 0x07)
        t = (t and 0xF8) xor (((t shl 1) or (t shr 7)) and 0x0F) xor (newChecksum shr 8)
        newChecksum = (el shl 8) or t
    }

    return newChecksum
}
