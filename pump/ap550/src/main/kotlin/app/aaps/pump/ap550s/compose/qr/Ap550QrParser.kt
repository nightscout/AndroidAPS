package app.aaps.pump.ap550s.compose.qr

/** What a scanned pump QR code turned into. */
sealed interface Ap550QrParseResult {

    /** The QR code was read and the pump model is known. */
    data class Success(val serialNumber: String, val deviceName: String) : Ap550QrParseResult

    /** The text is not an AP550 pump QR code at all. */
    data object Unrecognized : Ap550QrParseResult

    /** The QR code has the pump layout but the product id is one we do not ship. */
    data object UnknownDeviceType : Ap550QrParseResult
}

/**
 * Reads the QR code printed on the back of the pump.
 *
 * The payload holds the product id and the serial number as digits, each behind a two digit tag.
 * The serial number is what the Bluetooth scan is matched against, so only the layout matters here,
 * not the surrounding text.
 */
object Ap550QrParser {

    /** Product id: tag `240` followed by eight digits. */
    private val PRODUCT_ID_REGEX = Regex("""240([0-9]{8})""")

    /** Serial number: tag `21` followed by thirteen digits or upper case letters. */
    private val SERIAL_NUMBER_REGEX = Regex("""21([0-9A-Z]{13})""")

    /**
     * Product id to the name shown while pairing. `88001362` is shared by two pumps; the one in the
     * table is the model this driver supports. P700 is not listed, so its QR code is rejected.
     */
    private val DEVICE_NAMES = mapOf(
        "88001228" to "550S",
        "88001229" to "uAP 550SE",
        "88001360" to "uMH AP600",
        "88001362" to "uMH AP602"
    )

    fun parse(contents: String): Ap550QrParseResult {
        val productId = PRODUCT_ID_REGEX.find(contents)?.groupValues?.get(1)
            ?: return Ap550QrParseResult.Unrecognized

        val serialNumber = SERIAL_NUMBER_REGEX.find(contents)?.groupValues?.get(1)
            ?: return Ap550QrParseResult.Unrecognized

        val deviceName = DEVICE_NAMES[productId]
            ?: return Ap550QrParseResult.UnknownDeviceType

        return Ap550QrParseResult.Success(serialNumber = serialNumber, deviceName = deviceName)
    }
}