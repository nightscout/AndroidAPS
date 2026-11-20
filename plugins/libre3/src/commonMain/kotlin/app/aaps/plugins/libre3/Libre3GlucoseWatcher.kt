package app.aaps.plugins.libre3

import app.aaps.plugins.libre3.parse.Libre3PatchStatus

/**
 * Callback surface of [Libre3CgmDriver]. The plugin in `:plugins:source` implements it.
 *
 * ⚠️ ASYNC IMPACT: the real driver calls these methods from its own BLE executor thread.
 * Do not block in [onGlucose]; hand the sample to a coroutine scope instead.
 */
interface Libre3GlucoseWatcher {

    fun onWarmup(state: Libre3WarmupState)
    fun onGlucose(sample: Libre3GlucoseSample)
    fun onSession(up: Boolean, reason: String?)
    fun onError(message: String, fatal: Boolean)

    /**
     * Every health message of the sensor, also a good one, so a watcher can tell when a fault is
     * over. Does nothing by default.
     */
    fun onPatchStatus(status: Libre3PatchStatus) {}
}
