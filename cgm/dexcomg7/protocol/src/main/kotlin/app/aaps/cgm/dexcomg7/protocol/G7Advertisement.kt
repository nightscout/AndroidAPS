package app.aaps.cgm.dexcomg7.protocol

/**
 * What a sensor says about itself before anyone connects.
 *
 * The manufacturer data (after the company id 0x00D0) is `CRC16-XMODEM(serial) LE | types in use | 04`.
 * The CRC is over the ASCII digits of the serial printed on the package, and "types in use" says which
 * kinds of display have held a slot in the last ~15 minutes.
 *
 * So without connecting we can tell whether a sensor can be the one whose package was scanned, and
 * whether another phone is using it. A sensor refuses a second phone, and four refusals in a row make it
 * stop accepting connections for a while, so a held slot is a reason to try other sensors first.
 *
 * Android gives the manufacturer data without the company id, so [manufacturerData] starts at the CRC.
 */
class G7Advertisement(val name: String, manufacturerData: ByteArray?) {

    /** CRC16-XMODEM of the serial's ASCII digits, when present. */
    val serialChecksum: Int?

    /** Which display types hold a slot. Null when the advertisement did not say. */
    val typesInUse: Int?

    init {
        if (manufacturerData != null && manufacturerData.size >= 3) {
            serialChecksum = manufacturerData.u16le(0)
            typesInUse = manufacturerData.u8(2)
        } else {
            serialChecksum = null
            typesInUse = null
        }
    }

    val model: G7SensorModel? get() = G7SensorModel.fromAdvertisedName(name)

    val isSupportedSensor: Boolean get() = model != null

    fun isSlotHeld(displayType: G7DisplayType = G7DisplayType.PHONE): Boolean? = typesInUse?.let { it and displayType.typesInUseMask != 0 }

    /**
     * Whether this sensor can have [serial] printed on its package. Unknown counts as possible: a wasted
     * handshake is better than never trying the user's own sensor.
     */
    fun couldHaveSerial(serial: String): Boolean {
        val have = serialChecksum ?: return true
        val expected = serialChecksum(serial) ?: return true
        return have == expected
    }

    companion object {

        /** The checksum a sensor with [serial] advertises, or null when [serial] is not only digits. */
        fun serialChecksum(serial: String): Int? {
            if (serial.isEmpty() || !serial.all { it in '0'..'9' }) return null
            return Crc16.xmodem(serial.toByteArray(Charsets.US_ASCII))
        }
    }
}

object Crc16 {

    /** CRC-16/XMODEM: polynomial 0x1021, initial value 0, no reflection. */
    fun xmodem(bytes: ByteArray): Int {
        var crc = 0
        for (b in bytes) {
            crc = crc xor ((b.toInt() and 0xff) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xffff
            }
        }
        return crc
    }
}
