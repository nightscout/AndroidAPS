package app.aaps.plugins.dexcomoneplus.gatt

/**
 * Tagged Auth / ExtraData notify for the KEKS handshake.
 *
 * Ob1 routes by characteristic UUID: Auth → `receivedResponse`, ExtraData → `receivedData`.
 * Mixing both into one byte stream corrupts the Round1/2/3 160-byte accumulator.
 */
enum class OnePlusKeksNotifySource {
    AUTHENTICATION,
    EXTRA_DATA,
}

data class OnePlusKeksNotify(
    val source: OnePlusKeksNotifySource,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OnePlusKeksNotify) return false
        return source == other.source && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int = 31 * source.hashCode() + payload.contentHashCode()
}
