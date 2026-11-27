package app.aaps.plugins.dexcomoneplus.scan

import app.aaps.plugins.dexcomoneplus.identity.OnePlusStoredSession
import kotlin.concurrent.Volatile

/**
 * BLE scan façade for ONE+ / G7-family ADV.
 *
 * ⚠️ ASYNC IMPACT: Android `ScanCallback` runs on the binder thread;
 * [OnePlusScanListener.onDevice] may be invoked off the main thread — hop to main for UI.
 */
fun interface OnePlusScanListener {
    fun onDevice(result: OnePlusScanResult)
}

/**
 * Outcome of a bounded pre-connect ADV wait.
 *
 * @param target the awaited MAC, or null when it stayed silent for the whole window.
 * @param foreign other ONE+ / G7-family transmitters heard during the same window. A non-empty
 *   [foreign] with a null [target] is the signature of a **stale stored MAC** (sensor replaced, or
 *   started into the other slot): a ONE+ is right there, it is simply not the one we wait for.
 */
data class OnePlusAdvWaitResult(
    val target: OnePlusScanResult? = null,
    val foreign: List<OnePlusScanResult> = emptyList(),
) {

    /** Strongest foreign transmitter heard, for the "wrong MAC?" diagnostic. */
    fun strongestForeign(): OnePlusScanResult? = foreign.maxByOrNull { it.rssi }
}

interface OnePlusBleScanner {
    fun startScan(listener: OnePlusScanListener)
    fun stopScan()
    fun isScanning(): Boolean

    /**
     * Tell the scanner what the slot's store now knows about its sensor, so later advertisements
     * are ranked and sticky-matched against it.
     *
     * Called after a successful authentication and after the identity is saved. The default does
     * nothing, which is right for a scanner that does not rank anything.
     */
    fun updateSessionHint(session: OnePlusStoredSession?) = Unit

    /**
     * Block until [address] is seen in ADV (or timeout), also reporting any other ONE+ family
     * transmitter heard in the same window.
     *
     * A [stopScan] from the screen must not cut this wait short. Android gets that from a private
     * `ScanCallback`. Each platform owes the guarantee, not the mechanism.
     *
     * ⚠️ ASYNC IMPACT: blocks the calling thread; the platform delivers results on its own thread.
     */
    fun awaitTarget(address: String, timeoutMs: Long): OnePlusAdvWaitResult = OnePlusAdvWaitResult()
}

/**
 * No-op scanner for unit tests / Stub driver path.
 */
class OnePlusBleScannerStub : OnePlusBleScanner {
    @Volatile
    private var scanning = false

    override fun startScan(listener: OnePlusScanListener) {
        scanning = true
    }

    override fun stopScan() {
        scanning = false
    }

    override fun isScanning(): Boolean = scanning
}
