package app.aaps.cgm.dexcomg7.protocol

/**
 * The GATT identifiers of a G7-family sensor.
 *
 * [ADVERTISEMENT_SERVICE] is what the sensor puts in its advertisement. It is not the service it
 * serves once connected, so a scan must filter on this one and not on [CGM_SERVICE]: a scan filtered
 * on the GATT service finds nothing, because that service only appears after connecting.
 */
object G7Gatt {

    const val ADVERTISEMENT_SERVICE = "0000FEBC-0000-1000-8000-00805F9B34FB"

    /** Mask that compares only the 16-bit part of [ADVERTISEMENT_SERVICE]. */
    const val ADVERTISEMENT_SERVICE_MASK = "0000FFFF-0000-0000-0000-000000000000"

    const val CGM_SERVICE = "F8083532-849E-531C-C594-30F1F86A4EA5"

    const val CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805F9B34FB"

    /** Company id in front of the manufacturer data of the advertisement (0x00D0). */
    const val MANUFACTURER_ID = 0x00D0
}

/** The characteristics of [G7Gatt.CGM_SERVICE] this driver uses. */
enum class G7Characteristic(val uuid: String) {

    /** Write/indicate. Commands and their answers: readings, versions, calibration, backfill end. */
    CONTROL("F8083534-849E-531C-C594-30F1F86A4EA5"),

    /** Write/indicate. The framing of the pairing handshake. */
    AUTHENTICATION("F8083535-849E-531C-C594-30F1F86A4EA5"),

    /** Notify. Backfill records, 9 bytes each. */
    BACKFILL("F8083536-849E-531C-C594-30F1F86A4EA5"),

    /**
     * Write/notify. The bulk data of the handshake (key exchange rounds, certificates, signatures),
     * sent in 20-byte pieces while [AUTHENTICATION] carries the framing.
     */
    CERTIFICATE("F8083538-849E-531C-C594-30F1F86A4EA5");

    companion object {

        fun fromUuid(uuid: String): G7Characteristic? = entries.firstOrNull { it.uuid.equals(uuid, ignoreCase = true) }
    }
}

/** First byte of a control message. */
object G7Opcode {

    const val AUTH_STATUS = 0x05
    const val CALIBRATION_BOUNDS = 0x32
    const val CALIBRATE = 0x34
    const val TRANSMITTER_VERSION = 0x4a
    const val GLUCOSE = 0x4e
    const val EXTENDED_VERSION = 0x52
    const val BACKFILL = 0x59
}

/**
 * What kind of display a sensor is talking to. A sensor keeps one slot per display type, so a phone
 * and a watch can both have a session with it, but two phones cannot. This driver always takes
 * [PHONE].
 */
enum class G7DisplayType(val code: Int) {

    UNKNOWN(0),
    MEDICAL(1),
    PHONE(2),
    WATCH(3),
    RECEIVER(4),
    PUMP(5),
    READER(6),
    TOOL(7),
    OTHER(8),
    TRANSMITTER(9);

    /** This type's bit in the advertisement's "types in use" byte. Seen on air for [PHONE] only. */
    val typesInUseMask: Int get() = if (code in 1..8) 1 shl (code - 1) else 0

    companion object {

        fun fromCode(code: Int): G7DisplayType = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}
