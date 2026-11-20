package app.aaps.plugins.libre3

/**
 * Logcat markers for the native Libre 3 / Libre 3 Plus session.
 *
 * Every marker starts with `LIBRE3_`, so one logcat filter shows the whole driver.
 * Log the constant as a substring of the message, not only as the tag.
 */
object Libre3LogMarkers {

    const val TAG = "LIBRE3"

    const val NFC = "LIBRE3_NFC"
    const val SESSION = "LIBRE3_SESSION"
    const val PAIRING = "LIBRE3_PAIRING"
    const val WARMUP = "LIBRE3_WARMUP"
    const val BG = "LIBRE3_BG"
    const val ERROR = "LIBRE3_ERROR"
    const val SCAN = "LIBRE3_SCAN"
    const val RECONNECT = "LIBRE3_RECONNECT"
    const val PRESOAK = "LIBRE3_PRESOAK"

    /** Step by step trace of one pairing attempt. See `Libre3PairingTrace`. */
    const val TRACE = "LIBRE3_TRACE"
}
