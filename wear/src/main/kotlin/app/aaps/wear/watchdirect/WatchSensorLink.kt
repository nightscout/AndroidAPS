package app.aaps.wear.watchdirect

/**
 * The watch's own link to the CGM sensor, for direct-to-watch.
 *
 * Small on purpose: the watch app only needs to set the link up, pick it up again after a restart,
 * let it go, and hear readings. The protocol lives behind it, in `:plugins:dexcom_oneplus` and
 * `:plugins:libkeks` (see [OnePlusWatchSensorLink]); nothing else in the watch app knows about it.
 *
 * A reading that comes through here is for the watch faces only. The watch app has no way to store
 * glucose for the loop, and it never sends a reading to the phone.
 */
interface WatchSensorLink {

    /** What the link tells the watch app. Called on the link's own thread. */
    interface Listener {

        fun onReading(reading: WatchSensorReading)

        fun onConnected(connected: Boolean)

        /** A problem, in the driver's words. [fatal] means the link gave up and will not try again. */
        fun onProblem(message: String, fatal: Boolean)
    }

    fun setListener(listener: Listener)

    /** The sensor this link is set up for, or null when none is. */
    fun address(): String?

    /** The pairing code this link is set up with, or null. Never logged. */
    fun pairingCode(): String?

    /** True when the link has its own key for the sensor (the key exchange is done). */
    fun hasKey(): Boolean

    fun isConnected(): Boolean

    /** Set the link up for a new sensor. The first connection runs the key exchange. */
    fun connect(address: String, pairingCode: String, serial: String?)

    /** Pick the stored sensor up again, after the watch app restarted. False when none is stored. */
    fun resume(): Boolean

    /** Let the sensor go and forget it, key and all. */
    fun forget()
}

/** One reading the watch took from the sensor itself, in mg/dL. */
data class WatchSensorReading(
    val mgdl: Double,
    val timestampMs: Long,
    /** Rate of change in mg/dL per minute, or null when the sensor sent none. */
    val slopeMgdlPerMin: Double?,
)
