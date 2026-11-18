package app.aaps.plugins.source.compose

import app.aaps.plugins.dexcomoneplus.identity.OnePlusAdvCandidate
import app.aaps.plugins.dexcomoneplus.identity.OnePlusStoredSession
import app.aaps.plugins.dexcomoneplus.scan.OnePlusScanResult

/**
 * The decisions the sensor start screen makes, written as plain functions.
 *
 * They used to be expressions inside the composable, where nothing could run them. They are the
 * same expressions, moved out, so a test can say what the screen shows for a given state.
 */
object DexcomOnePlusStartRules {

    /**
     * The scan list in the order the screen draws it: the sensor the code on screen points at
     * first, then by how well each hit matches, then free sensors before the ones another phone
     * holds, then by signal.
     */
    fun rank(devices: List<OnePlusScanResult>, hint: OnePlusStoredSession?): List<OnePlusScanResult> =
        devices.sortedByDescending {
            OnePlusAdvCandidate.rankScore(it.name, it.address, it.rssi, hint, it.phoneSlotHeld)
        }

    /**
     * Whether the row says "in use by another phone".
     *
     * Not for the stored sensor: the phone that holds its slot is normally this one.
     */
    fun showsSlotHeld(device: OnePlusScanResult, storedMac: String?): Boolean =
        device.phoneSlotHeld == true && storedMac?.equals(device.address, ignoreCase = true) != true

    /**
     * Signal strength as bars.
     *
     * "−67 dBm" is a number the user cannot act on; four bars say "close enough" or "walk nearer"
     * immediately. The dBm value stays in the driver log for anyone debugging a range problem.
     *
     * Zero bars is the stored sensor offered without a live sighting, which carries no rssi at all.
     */
    fun signalBars(rssi: Int): Int = when {
        rssi == 0   -> 0
        rssi >= -60 -> 4
        rssi >= -70 -> 3
        rssi >= -80 -> 2
        else        -> 1
    }

    /**
     * True when Connect would point the slot at a different transmitter than the one it holds.
     *
     * The pairing code is per sensor, so adopting another transmitter with the code of the previous
     * one cannot work. The screen asks once before it overwrites the slot.
     */
    fun adoptsAnotherSensor(storedMac: String?, address: String): Boolean =
        storedMac != null && !storedMac.equals(address, ignoreCase = true)
}
