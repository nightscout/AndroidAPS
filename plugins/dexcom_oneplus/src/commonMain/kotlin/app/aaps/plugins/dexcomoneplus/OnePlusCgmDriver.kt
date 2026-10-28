package app.aaps.plugins.dexcomoneplus

import app.aaps.plugins.dexcomoneplus.scan.OnePlusScanListener

/**
 * Native Dexcom ONE+ BLE driver façade.
 *
 * The implementation is [OnePlusCgmDriverReal], which is shared code:
 * it builds and drives the session on both phones, and everything the phone itself owns is behind
 * [OnePlusPlatform]. The KEKS handshake (`:plugins:libkeks`) and `OnePlusSessionAuthKeks` are
 * shared too.
 *
 * ⚠️ ASYNC IMPACT: Real driver may invoke [OnePlusGlucoseWatcher] / scan listeners off the main
 * thread (bleExecutor / binder). Callers must not assume main; do not run dosing or UI work there.
 */
interface OnePlusCgmDriver {

    /**
     * Give the driver the phone it runs on, before anything else is asked of it.
     *
     * This replaced `setContext(Context)`. The driver needs four favours from its host and only a
     * file store means the same thing on both platforms, so the favours are named one by one in
     * [OnePlusPlatform] instead of being hidden behind one Android type. Calling it again is
     * allowed and rebuilds the scanner and the store; a link that is already up is not dropped.
     */
    fun attach(platform: OnePlusPlatform)
    fun addWatcher(watcher: OnePlusGlucoseWatcher)
    fun removeWatcher(watcher: OnePlusGlucoseWatcher)
    fun startScan(listener: OnePlusScanListener)
    fun stopScan()
    fun connect(deviceAddress: String, pairingCode: String)
    fun disconnect()
    fun shutdown()
    fun warmupState(): OnePlusWarmupState
    fun isSessionUp(): Boolean

    /**
     * Ask the driver to bring back the session it has stored, after the app was restarted.
     *
     * The driver keeps the sensor it last talked to - its address, its pairing code and its key -
     * so it can connect again without the user doing anything. This asks it to do that now.
     *
     * Default is false, which is the honest answer for a driver that stores no session: it did not
     * queue anything, and the caller must not show the sensor as coming back.
     *
     * @return true when a reconnect was queued. False when nothing was stored, when the stored
     *   session is incomplete, or when a session is already running.
     */
    fun resumeStoredSession(): Boolean = false
}
