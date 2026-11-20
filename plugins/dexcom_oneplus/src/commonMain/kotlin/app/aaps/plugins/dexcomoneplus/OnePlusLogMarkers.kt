package app.aaps.plugins.dexcomoneplus

/**
 * Logcat greppable markers for ONE+ native session.
 * Prefer logging the constant as a substring so filters like `DEXCOM_ONEPLUS_SESSION` work.
 */
object OnePlusLogMarkers {
    const val TAG = "DEXCOM_ONEPLUS"

    const val SESSION = "DEXCOM_ONEPLUS_SESSION"
    const val WARMUP = "DEXCOM_ONEPLUS_WARMUP"
    const val BG = "DEXCOM_ONEPLUS_BG"
    const val ERROR = "DEXCOM_ONEPLUS_ERROR"
    const val SCAN = "DEXCOM_ONEPLUS_SCAN"
    const val RECONNECT = "DEXCOM_ONEPLUS_RECONNECT"
    const val STUB = "ONEPLUS_DRIVER_STUB"

    /**
     * Slot label logged as `MARKER: [slot] …` (after the colon, so existing `DEXCOM_ONEPLUS_*`
     * filters keep matching). Without it a dual-sensor field trace cannot be attributed to the
     * production or the staging driver — both emit the same markers from their own threads.
     */
    const val SLOT_PRODUCTION = "prod"

    /** Slot label for a namespaced (non-production) driver — [namespace] is the store namespace. */
    fun slotOf(namespace: String?): String =
        if (namespace.isNullOrBlank()) SLOT_PRODUCTION else namespace
}
