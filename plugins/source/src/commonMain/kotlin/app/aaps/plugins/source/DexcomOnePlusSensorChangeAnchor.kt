package app.aaps.plugins.source

/**
 * Pure decision behind the ONE+ SENSOR_CHANGE anchor: should the plugin's own auto timestamp
 * (pairing moment, or first accepted reading) be replaced by a SENSOR_CHANGE the user already
 * logged by hand — e.g. in Careportal, right after physically inserting the sensor and before
 * pairing it in the app?
 *
 * Without this, a sensor inserted hours before pairing always got dated at the pairing moment:
 * `OnePlusSensorStore.startSessionForSensor` and `saveSessionStartIfAbsent` only ever saw
 * `System.currentTimeMillis()` / the first reading's timestamp, so a manual, earlier, more
 * accurate entry was silently outrun by the later auto one becoming "the most recent SENSOR_CHANGE"
 * that `LinearCalibrationPlugin` reads.
 */
internal object DexcomOnePlusSensorChangeAnchor {

    /** How far back a manually logged SENSOR_CHANGE can still plausibly belong to this insertion. */
    private const val MANUAL_LOOKBACK_MS = 24L * 60L * 60L * 1000L

    /**
     * @param autoStartMs the timestamp this plugin would stamp on its own (pairing moment, or first
     *   accepted reading)
     * @param lastSensorChangeMs the most recent existing SENSOR_CHANGE therapy event, if any
     * @param now current wall-clock time, used to bound how far back a manual entry can be
     * @return [lastSensorChangeMs] when it is an earlier, recent-enough manual entry; [autoStartMs]
     *   otherwise
     */
    fun resolve(autoStartMs: Long, lastSensorChangeMs: Long?, now: Long): Long {
        if (lastSensorChangeMs == null) return autoStartMs
        if (lastSensorChangeMs >= autoStartMs) return autoStartMs
        if (now - lastSensorChangeMs > MANUAL_LOOKBACK_MS) return autoStartMs
        return lastSensorChangeMs
    }
}
