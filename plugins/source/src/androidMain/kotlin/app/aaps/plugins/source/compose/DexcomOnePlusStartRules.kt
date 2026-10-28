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

    /** Step 1 of the guided flow: let the app use Bluetooth. */
    const val STEP_PREPARE = 0

    /** Step 2: type or scan the four digit pairing code from the applicator. */
    const val STEP_CODE = 1

    /** Step 3: pick the sensor out of what the scan heard. */
    const val STEP_CONNECT = 2

    /** Step 4: the sensor is adopted and warm-up starts. */
    const val STEP_WARMUP = 3

    /**
     * How far along the guided flow the user is.
     *
     * The order is what makes it a flow: a missing permission is shown even when a code has already
     * been typed, because nothing can be scanned until it is granted.
     */
    fun currentStep(hasPermissions: Boolean, hasCode: Boolean, hasDevice: Boolean): Int = when {
        !hasPermissions -> STEP_PREPARE
        !hasCode        -> STEP_CODE
        !hasDevice      -> STEP_CONNECT
        else            -> STEP_WARMUP
    }

    /**
     * The scan list in the order the screen draws it: the sensor the code on screen points at
     * first, then by how well each hit matches, then by signal.
     */
    fun rank(devices: List<OnePlusScanResult>, hint: OnePlusStoredSession?): List<OnePlusScanResult> =
        devices.sortedByDescending { OnePlusAdvCandidate.rankScore(it.name, it.address, it.rssi, hint) }

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
