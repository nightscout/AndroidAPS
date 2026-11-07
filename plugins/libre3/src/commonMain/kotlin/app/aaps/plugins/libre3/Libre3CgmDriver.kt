package app.aaps.plugins.libre3

/**
 * Native Libre 3 / Libre 3 Plus driver facade.
 *
 * The implementation is `Libre3CgmDriverReal`, handed out by `Libre3CgmDrivers`.
 *
 * There is no scan step and no typed pairing code here. The sensor is activated over NFC, which
 * gives the BLE address and the PIN. The Start screen persists them and only then calls [connect].
 *
 * ⚠️ ASYNC IMPACT: the real driver calls [Libre3GlucoseWatcher] from its BLE executor thread.
 * Callers must not assume the main thread and must not run dose logic there.
 */
interface Libre3CgmDriver {

    /**
     * Gives the driver what it needs from the phone it is running on.
     *
     * Called once per instance, before anything else. The driver keeps the platform for as long
     * as it lives: a session is rebuilt on every reconnect and needs a fresh GATT client each
     * time. On Android a `setContext(context)` extension calls this.
     */
    fun attach(platform: Libre3Platform)

    fun addWatcher(watcher: Libre3GlucoseWatcher)

    fun removeWatcher(watcher: Libre3GlucoseWatcher)

    /** Connect to the sensor whose BLE address and PIN were stored during the NFC step. */
    fun connect(deviceAddress: String)

    /**
     * Drop the GATT link only.
     *
     * The driver must never send a patch control command here. Telling the sensor to shut down
     * would end the sensor for good, so an ordinary disconnect stays a link level action.
     */
    fun disconnect()

    /** Stop everything and forget the watchers. Same rule as [disconnect]: link level only. */
    fun shutdown()

    fun warmupState(): Libre3WarmupState

    fun isSessionUp(): Boolean
}
