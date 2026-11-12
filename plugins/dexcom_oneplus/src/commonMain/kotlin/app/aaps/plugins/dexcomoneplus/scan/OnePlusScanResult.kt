package app.aaps.plugins.dexcomoneplus.scan

import kotlin.concurrent.Volatile

/**
 * LE advertisement hit for ONE+ / G7-family transmitters.
 *
 * @property phoneSlotHeld whether the advertisement says another phone holds the sensor's phone
 *   slot, or null when it did not say. See [app.aaps.plugins.dexcomoneplus.identity.OnePlusAdvSlot].
 *   Part of `equals` on purpose: a slot that became free must reach the list as a change.
 */
data class OnePlusScanResult(
    val address: String,
    val name: String?,
    val rssi: Int,
    val phoneSlotHeld: Boolean? = null,
) {
    /**
     * The platform's monotonic clock in milliseconds when this ADV was last seen, for UI→driver
     * handoff freshness. On Android that is `SystemClock.elapsedRealtime()`.
     * Intentionally **not** a constructor property: excluded from `equals`/`hashCode`/`copy` so it
     * never affects list keys or the scanner's field-diff dedup.
     */
    @Volatile
    var seenElapsedMs: Long = 0L
}
