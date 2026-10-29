package app.aaps.plugins.libre3

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
}
