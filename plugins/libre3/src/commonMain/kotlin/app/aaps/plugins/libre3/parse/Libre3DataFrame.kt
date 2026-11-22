package app.aaps.plugins.libre3.parse

/**
 * The outer wrapping of every message the sensor sends once a session is up.
 *
 * Ported from LibreCRKit `DataPlane/DataFrame.swift` at pin `a86b92f`.
 *
 * Shape: `encrypted bytes || seq || type`. The last two bytes are the packet number, and that
 * number goes into the nonce that protects the message.
 *
 * This matters more than it looks. The number is **read from the message itself**. Counting
 * messages on this side instead would give a different number after the very first missed or
 * repeated packet, every following message would fail its check, and the driver would look like a
 * sensor that had gone quiet.
 *
 * The sizes agree with the rest of the protocol: a glucose message arrives as 15 plus 20 bytes,
 * which is 35, and 35 is 29 plain bytes plus a 4 byte tag plus these 2 bytes.
 */
data class Libre3DataFrame(val encrypted: ByteArray, val seq: Int, val type: Int) {

    /** The packet number, as the nonce needs it. */
    val sequenceNumber: Int get() = seq or (type shl 8)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Libre3DataFrame) return false
        return encrypted.contentEquals(other.encrypted) && seq == other.seq && type == other.type
    }

    override fun hashCode(): Int {
        var result = encrypted.contentHashCode()
        result = 31 * result + seq
        result = 31 * result + type
        return result
    }

    companion object {

        /** Two bytes of packet number, and there has to be at least one byte in front of them. */
        const val MIN_SIZE = 3

        fun parse(raw: ByteArray): Libre3DataFrame {
            if (raw.size < MIN_SIZE) {
                throw Libre3ParseException("a message needs at least 3 bytes, this one has ${raw.size}")
            }
            val trailerStart = raw.size - 2
            return Libre3DataFrame(
                encrypted = raw.copyOfRange(0, trailerStart),
                seq = raw[trailerStart].toInt() and 0xFF,
                type = raw[trailerStart + 1].toInt() and 0xFF,
            )
        }
    }
}
