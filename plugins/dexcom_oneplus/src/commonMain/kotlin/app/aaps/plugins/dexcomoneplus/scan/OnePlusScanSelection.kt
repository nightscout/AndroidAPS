package app.aaps.plugins.dexcomoneplus.scan

import app.aaps.plugins.dexcomoneplus.identity.OnePlusStoredSession

/**
 * Which sensor a setup screen may select on its own, out of what the scan heard.
 *
 * This is a rule, not a platform job, so it lives in shared code and is the same on every
 * platform.
 */
object OnePlusScanSelection {

    /**
     * The sensor a screen may select on its own, out of what the scan heard.
     *
     * Exactly one hit, and only then. The 4-digit code is the KEKS password and is not in the
     * advertisement, so with two ONE+ in range no score can tell which one the user just applied:
     * the strongest signal may be the neighbour's, and the best ranked one may be the sensor being
     * replaced. Picking either would send the user into a five minute connect with the wrong
     * transmitter. Two or more is a question, and the user answers it by tapping a row.
     *
     * Nothing heard means nothing to connect to, so that answer is null as well.
     */
    fun autoSelectSingle(devices: List<OnePlusScanResult>): OnePlusScanResult? = devices.singleOrNull()

    /**
     * The transmitter the start screen may select on its own, given what the scan heard so far.
     *
     * Two cases, and the code on screen is what tells them apart:
     * - The code belongs to the stored sensor, so this is a reconnect or a repair of the session
     *   already running. That sensor is the answer, even among several: the user is not choosing
     *   anything new. A live sighting of it is preferred over the stored one, because it carries
     *   the fresh ADV the driver connects in-window with.
     * - The code belongs to another sensor. Nothing sticky may apply then, and only a single hit
     *   may be selected — see [autoSelectSingle].
     *
     * Feed it the session from `OnePlusAdvCandidate.scanHintFor`, which is what drops the stored
     * MAC as soon as the code on screen is another sensor.
     */
    fun autoSelect(devices: List<OnePlusScanResult>, hint: OnePlusStoredSession?): OnePlusScanResult? {
        val storedMac = hint?.lastMac?.takeIf { it.isNotBlank() } ?: return autoSelectSingle(devices)
        return devices.firstOrNull { it.address.equals(storedMac, ignoreCase = true) }
            ?: OnePlusScanResult(address = storedMac, name = hint.lastDeviceName, rssi = 0)
    }
}
