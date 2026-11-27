package app.aaps.plugins.dexcomoneplus.identity

/**
 * Dexcom ONE+ / G7 applicator identity (Juggluco GS1 Data Matrix fields).
 *
 * [pin] is the 4-digit KEKS password. [serial] comes from GS1 AI 21 when available
 * and is used to sticky-match BLE ADV names across reconnects.
 */
data class OnePlusSensorIdentity(
    val pin: String,
    val serial: String? = null,
    val gtin: String? = null,
    /** Raw GS1 / Data Matrix payload when parsed from applicator QR. */
    val rawGs1: String? = null,
)

data class OnePlusStoredSession(
    val identity: OnePlusSensorIdentity,
    val lastMac: String? = null,
    /** Last ADV local name that successfully authenticated (Juggluco DexDeviceName). */
    val lastDeviceName: String? = null,
    /** 16-byte KEKS shared key for reconnect short-auth (libkeks persistence channel 2). */
    val sharedKey: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OnePlusStoredSession) return false
        return identity == other.identity &&
            lastMac == other.lastMac &&
            lastDeviceName == other.lastDeviceName &&
            sharedKey.contentEquals(other.sharedKey)
    }

    override fun hashCode(): Int {
        var result = identity.hashCode()
        result = 31 * result + (lastMac?.hashCode() ?: 0)
        result = 31 * result + (lastDeviceName?.hashCode() ?: 0)
        result = 31 * result + (sharedKey?.contentHashCode() ?: 0)
        return result
    }
}
