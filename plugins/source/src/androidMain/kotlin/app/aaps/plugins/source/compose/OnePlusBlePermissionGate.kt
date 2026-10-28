package app.aaps.plugins.source.compose

/**
 * Whether the phone lets this app scan for Bluetooth sensors, and how to ask.
 *
 * Android has a runtime permission the user grants before a scan can hear anything, and it has to
 * be asked for from the Activity that is on top. The sensor start screen has no way to reach that
 * Activity, so the Activity hands it one of these.
 */
interface OnePlusBlePermissionGate {

    /** True when a scan may start now. */
    fun granted(): Boolean

    /**
     * Ask the user for what is still missing.
     *
     * The screen only calls this when [granted] said no, so an implementation never has to decide
     * whether asking is needed.
     */
    fun request()
}
