package app.aaps.plugins.dexcomoneplus

/**
 * One app that connects to a Dexcom G7 / ONE+ itself.
 *
 * @property label the app's own product name. A name, not a sentence, so it is not translated.
 */
data class OnePlusCompetingApp(val packageName: String, val label: String)

/**
 * Answers "is this app installed?". Android asks its `PackageManager`.
 *
 * A host that cannot list the other apps on the phone has no instance of this at all, and the
 * warning is absent there. It must never be an instance that always says "not installed", because
 * that reads as "none found".
 */
fun interface OnePlusInstalledApps {

    fun isInstalled(packageName: String): Boolean
}

/**
 * The apps that fight with us for the sensor.
 *
 * A sensor talks to one phone app at a time. When the Dexcom app, xDrip+ or Juggluco is on the phone
 * as well, it may take the sensor's phone slot, and then the sensor refuses us. The user should know
 * before they start, so the start screen lists the ones that are installed, and the list is logged.
 *
 * The package names are the ones nightscout/AndroidAPS PR 5397 checks (`G7PairingViewModel`).
 */
object OnePlusCompetingApps {

    val KNOWN: List<OnePlusCompetingApp> = listOf(
        OnePlusCompetingApp("com.dexcom.g7", "Dexcom G7"),
        OnePlusCompetingApp("com.dexcom.dexcomone", "Dexcom ONE"),
        OnePlusCompetingApp("com.dexcom.one", "Dexcom ONE+"),
        OnePlusCompetingApp("com.dexcom.stelo", "Dexcom Stelo"),
        OnePlusCompetingApp("com.eveningoutpost.dexdrip", "xDrip+"),
        OnePlusCompetingApp("tk.glucodata", "Juggluco"),
    )

    /**
     * The known apps that are installed, in the order of [KNOWN], and one log line about them.
     *
     * A lookup that fails counts as "not installed" for that app only: one broken answer must not
     * hide the others.
     */
    fun installed(apps: OnePlusInstalledApps): List<OnePlusCompetingApp> {
        val found = KNOWN.filter { app ->
            try {
                apps.isInstalled(app.packageName)
            } catch (_: Throwable) {
                false
            }
        }
        if (found.isEmpty()) {
            OnePlusLog.i("${OnePlusLogMarkers.SCAN}: no other app that uses the sensor is installed")
        } else {
            OnePlusLog.w(
                "${OnePlusLogMarkers.SCAN}: other apps that use the sensor are installed: " +
                    found.joinToString { it.packageName } + " (a sensor talks to one phone app at a time)",
            )
        }
        return found
    }
}
