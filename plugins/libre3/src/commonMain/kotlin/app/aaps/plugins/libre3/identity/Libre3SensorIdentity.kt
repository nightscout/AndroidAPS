package app.aaps.plugins.libre3.identity

/**
 * Everything the driver knows about one sensor after the NFC scan.
 *
 * @param serialNumber nine characters printed by the sensor itself.
 * @param bleAddress Bluetooth address in Android form, for example `CC:22:DF:B8:F9:58`.
 * @param blePin four bytes from the NFC answer. Needed by every pairing message.
 * @param receiverId identifier this phone sent to the sensor during the NFC step.
 * @param generation 0 is Libre 3, 1 is Libre 3 Plus.
 * @param warmupMinutes warm-up length read from the sensor, 60 minutes on the sensors seen so far.
 * @param wearDurationMinutes whole sensor life read from the sensor.
 * @param activatedAtMs phone time of the NFC scan. Sample times are counted from here.
 */
data class Libre3SensorIdentity(
    val serialNumber: String,
    val bleAddress: String,
    val blePin: ByteArray,
    val receiverId: Int,
    val generation: Int,
    val warmupMinutes: Int,
    val wearDurationMinutes: Int,
    val activatedAtMs: Long,
) {

    /** True for Libre 3 Plus, false for Libre 3. Same driver either way. */
    val isPlus: Boolean get() = generation >= 1

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Libre3SensorIdentity) return false
        return serialNumber == other.serialNumber &&
            bleAddress == other.bleAddress &&
            blePin.contentEquals(other.blePin) &&
            receiverId == other.receiverId &&
            generation == other.generation &&
            warmupMinutes == other.warmupMinutes &&
            wearDurationMinutes == other.wearDurationMinutes &&
            activatedAtMs == other.activatedAtMs
    }

    override fun hashCode(): Int {
        var result = serialNumber.hashCode()
        result = 31 * result + bleAddress.hashCode()
        result = 31 * result + blePin.contentHashCode()
        result = 31 * result + receiverId
        result = 31 * result + generation
        result = 31 * result + warmupMinutes
        result = 31 * result + wearDurationMinutes
        result = 31 * result + activatedAtMs.hashCode()
        return result
    }
}

/**
 * Session keys of one sensor. They are written by the Bluetooth pairing, never by NFC.
 *
 * [phase5RawKey] is the long lived one: as long as it is stored, a reconnect must use the short
 * path and must never send the first pair command again. [kEnc] and [ivEnc] are replaced by every
 * new handshake.
 */
data class Libre3SessionKeys(
    val phase5RawKey: ByteArray?,
    val kEnc: ByteArray?,
    val ivEnc: ByteArray?,
) {

    /** True when a short reconnect is possible. */
    val hasPairingKey: Boolean get() = phase5RawKey != null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Libre3SessionKeys) return false
        return phase5RawKey.contentEquals(other.phase5RawKey) &&
            kEnc.contentEquals(other.kEnc) &&
            ivEnc.contentEquals(other.ivEnc)
    }

    override fun hashCode(): Int {
        var result = phase5RawKey?.contentHashCode() ?: 0
        result = 31 * result + (kEnc?.contentHashCode() ?: 0)
        result = 31 * result + (ivEnc?.contentHashCode() ?: 0)
        return result
    }
}
